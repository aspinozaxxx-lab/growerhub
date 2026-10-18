package ru.growerhub.backend.automation.engine;
import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import ru.growerhub.backend.automation.contract.AutomationData.ResourceBinding;
import ru.growerhub.backend.automation.contract.RuleData.Definition;
import ru.growerhub.backend.common.config.automation.RuleSettings;
import ru.growerhub.backend.common.contract.DomainException;
import ru.growerhub.backend.plant.contract.PlantInfo;
class RuleEvaluatorTest {
    final ObjectMapper json = new ObjectMapper();
    final RuleEvaluator engine = new RuleEvaluator(new RuleSettings(50, 16, 5, 64, 60, 86400, 300, 1800, 900, 60, 5000, 60, 50));
    final Instant at = Instant.parse("2026-10-18T09:00:00Z");
    JsonNode node(String text) throws Exception { return json.readTree(text); }
    Definition rule(JsonNode condition) throws Exception {
        return new Definition(1, "Рассада", "ROOM", 1, node("{\"type\":\"interval\",\"seconds\":60,\"anchor\":\"2026-10-18T09:00:00Z\"}"), condition,
            List.of(node("{\"type\":\"notify\",\"text\":\"Проверьте растения\"}")), 60);
    }
    RuleEvaluator.Context context(Instant now, Object value, Instant seen) {
        var sensor = new ResourceBinding(1, "ROOM", 1, "AIR_TEMPERATURE", "ZIGBEE", null, null, UUID.randomUUID(), "0x1", "temperature", null, null, null,
            value, LocalDateTime.ofInstant(seen, ZoneOffset.UTC), "ONLINE", null, "Температура", true, null);
        return new RuleEvaluator.Context(Map.of(1, sensor), Map.of(8, new PlantInfo(8, "Роза", null, null, "flower", "rose", "flowering", 1)), ZoneOffset.UTC, now);
    }
    @Test void staleOrMissingDataNeverBecomesTrueThroughNegation() throws Exception {
        var d = rule(node("{\"type\":\"not\",\"condition\":{\"type\":\"sensor\",\"resourceId\":1,\"op\":\"gt\",\"value\":25,\"maxAgeSeconds\":60}}"));
        engine.validate(d, context(at, 28, at));
        assertThat(engine.evaluate(d, context(at, 20, at.minusSeconds(61)), json.createObjectNode()).ready()).isFalse();
        assertThat(engine.evaluate(d, context(at, "NaN", at), json.createObjectNode()).ready()).isFalse();
        assertThat(engine.evaluate(d, context(at, 20, at), json.createObjectNode()).ready()).isTrue();
    }
    @Test void holdAndHysteresisSurviveSerializationAndResetOnMissingData() throws Exception {
        var d = rule(node("{\"type\":\"sensor\",\"resourceId\":1,\"op\":\"gt\",\"value\":25,\"hysteresis\":2,\"holdSeconds\":60,\"maxAgeSeconds\":90}"));
        var state = json.createObjectNode();
        assertThat(engine.evaluate(d, context(at, 26, at), state).ready()).isFalse();
        state = (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(state.toString());
        assertThat(engine.evaluate(d, context(at.plusSeconds(60), 24, at.plusSeconds(60)), state).ready()).isTrue();
        assertThat(engine.evaluate(d, context(at.plusSeconds(61), 24, at.minusSeconds(91)), state).ready()).isFalse();
        assertThat(engine.evaluate(d, context(at.plusSeconds(62), 26, at.plusSeconds(62)), state).ready()).isFalse();
        assertThat(engine.evaluate(d, context(at.plusSeconds(122), 26, at.plusSeconds(122)), state).ready()).isTrue();
    }
    @Test void plantStageAndOvernightWindowUseAccountTimezone() throws Exception {
        var d = rule(node("{\"type\":\"all\",\"conditions\":[{\"type\":\"stage\",\"plantId\":8,\"stage\":\"flowering\"},{\"type\":\"time\",\"from\":\"22:00\",\"until\":\"03:00\",\"days\":[7]}]}"));
        var c = context(at, 25, at);
        assertThat(engine.evaluate(d, new RuleEvaluator.Context(c.resources(), c.plants(), ZoneId.of("Europe/Istanbul"), Instant.parse("2026-10-18T22:00:00Z")), json.createObjectNode()).ready()).isTrue();
        assertThat(engine.evaluate(d, new RuleEvaluator.Context(c.resources(), c.plants(), ZoneId.of("Europe/Istanbul"), Instant.parse("2026-10-19T01:00:00Z")), json.createObjectNode()).ready()).isFalse();
    }
    @Test void duplicateDstHourHasOneStableScheduleKeyAndChangeDoesNotFireAtStartup() throws Exception {
        var d = new Definition(1, "Таймер", "ROOM", 1, node("{\"type\":\"schedule\",\"at\":\"02:30\"}"), null, rule(null).actions(), 60);
        var c = context(at, 25, at); var state = json.createObjectNode();
        var first = engine.evaluate(d, new RuleEvaluator.Context(c.resources(), c.plants(), ZoneId.of("Europe/Berlin"), Instant.parse("2026-10-25T00:30:00Z")), state);
        var second = engine.evaluate(d, new RuleEvaluator.Context(c.resources(), c.plants(), ZoneId.of("Europe/Berlin"), Instant.parse("2026-10-25T01:30:00Z")), state);
        assertThat(first.key()).isEqualTo(second.key()).isNotNull();
        d = new Definition(1, "Датчик", "ROOM", 1, node("{\"type\":\"change\",\"resourceId\":1,\"maxAgeSeconds\":60}"), null, rule(null).actions(), 60);
        state = json.createObjectNode();
        assertThat(engine.evaluate(d, context(at, 25, at), state).key()).isNull();
        assertThat(engine.evaluate(d, context(at.plusSeconds(1), 26, at.plusSeconds(1)), state).ready()).isTrue();
    }
    @Test void arbitraryCodeUnknownFieldsAndForeignResourcesAreRejected() throws Exception {
        var base = rule(null);
        for (String action : List.of("{\"type\":\"exec\",\"code\":\"system()\"}", "{\"type\":\"notify\",\"text\":\"x\",\"url\":\"https://example.com\"}", "{\"type\":\"set\",\"resourceId\":99,\"value\":\"ON\"}", "{\"type\":\"delay\",\"seconds\":86401}")) {
            var d = new Definition(1, base.name(), "ROOM", 1, base.trigger(), null, List.of(node(action)), 60);
            assertThatThrownBy(() -> engine.validate(d, context(at, 25, at))).isInstanceOf(DomainException.class);
        }
        var tooDeep = node("{\"type\":\"stage\",\"plantId\":8,\"stage\":\"flowering\"}");
        for (int i = 0; i < 7; i++) tooDeep = json.createObjectNode().put("type", "not").set("condition", tooDeep);
        var deep = rule(tooDeep);
        assertThatThrownBy(() -> engine.validate(deep, context(at, 25, at))).isInstanceOf(DomainException.class);
    }
}

package ru.growerhub.backend.api;

import static org.assertj.core.api.Assertions.*;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.annotation.DirtiesContext;
import ru.growerhub.backend.IntegrationTestBase;
import ru.growerhub.backend.user.jpa.UserEntity;
import ru.growerhub.backend.user.jpa.UserRepository;

@SpringBootTest(properties = {"MQTT_HOST=", "spring.datasource.url=jdbc:h2:mem:control_migration;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ControlMigrationIntegrationTest extends IntegrationTestBase {
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;

    @Test void additiveMigrationKeepsRulesAndCascadesDeletedOwners() {
        var at = LocalDateTime.of(2026, 10, 22, 9, 0);
        var owner = users.save(UserEntity.create("control-migration@test.example", "Migration", "user", true, at, at));
        UUID rule = UUID.randomUUID();
        jdbc.update("insert into automation_rules(id,user_id,revision,enabled,definition_json,runtime_json,created_at,updated_at) values(?,?,1,false,'{}','{}',?,?)", rule, owner.getId(), at, at);
        jdbc.execute("drop table automation_control_demands");
        jdbc.execute("drop table automation_control_channels");
        jdbc.execute("alter table zigbee_device_snapshots drop column live_properties_json");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V41__automation_control_demands.sql"))
                .execute(Objects.requireNonNull(jdbc.getDataSource()));
        assertThat(jdbc.queryForObject("select count(*) from automation_rules where id=?", Integer.class, rule)).isEqualTo(1);
        jdbc.update("insert into automation_control_demands(id,user_id,resource_id,target_key,binding_fingerprint,source_type,source_id,source_key,source_name,revision,priority,value_json,kind,status,requested_at,created_at) values(?,?,1,'target','binding','manual','source','manual:source','Manual',1,200,'\"ON\"','manual','pending',?,?)",
                UUID.randomUUID(), owner.getId(), at, at);
        jdbc.update("insert into automation_control_channels(id,user_id,resource_id,target_key,binding_fingerprint,coordinator_id,ieee_address,property,revision,status) values(?,?,1,'target','binding',?,'0xmigration','state',1,'pending')",
                UUID.randomUUID(), owner.getId(), UUID.randomUUID());
        users.delete(owner);
        assertThat(jdbc.queryForObject("select count(*) from automation_control_demands", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from automation_control_channels", Integer.class)).isZero();
    }
}

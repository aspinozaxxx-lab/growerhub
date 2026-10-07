package ru.growerhub.backend.common.config;

import java.io.IOException;
import java.util.HashSet;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import ru.growerhub.backend.shop.contract.ShopData;

@Configuration
public class ShopConfig {
    @Bean
    public ShopData.Definition shopCatalog(ShopSettings settings, ResourceLoader resources, ObjectMapper json) throws IOException {
        try (var stream = resources.getResource(settings.catalogPath()).getInputStream()) {
            var catalog = json.readValue(stream, ShopData.Definition.class);
            var identifiers = new HashSet<String>();
            if (catalog.version() == null || catalog.version().isBlank() || !"RUB".equals(catalog.currency())
                    || catalog.offers() == null || catalog.offers().isEmpty()) throw new IllegalStateException("Invalid shop catalog");
            for (var offer : catalog.offers()) {
                if (offer.id() == null || offer.id().isBlank() || !identifiers.add(offer.id()) || offer.priceMinor() <= 0
                        || offer.verification() == null) throw new IllegalStateException("Invalid shop offer");
                boolean starter = present(offer.hubModel()) && present(offer.hubEquipmentId())
                        && present(offer.socketEquipmentId()) && offer.socketCount() > 0;
                boolean accessory = offer.hubModel() == null && offer.hubEquipmentId() == null
                        && offer.socketEquipmentId() == null && offer.socketCount() == 0
                        && offer.components() != null && !offer.components().isEmpty();
                if (!starter && !accessory) throw new IllegalStateException("Invalid shop offer composition");
                if (offer.components() != null) {
                    var components = new HashSet<String>();
                    for (var component : offer.components()) {
                        if (component.id() == null || !component.id().matches("[a-z][a-z0-9-]*")
                                || component.quantity() < 1 || !components.add(component.id()))
                            throw new IllegalStateException("Invalid shop component");
                    }
                }
            }
            return new ShopData.Definition(catalog.version(), catalog.currency(), java.util.List.copyOf(catalog.offers()));
        }
    }
    private boolean present(String value) { return value != null && !value.isBlank(); }
    @Bean
    public ThreadPoolTaskScheduler shopTaskScheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1); scheduler.setThreadNamePrefix("shop-notification-");
        return scheduler;
    }
}

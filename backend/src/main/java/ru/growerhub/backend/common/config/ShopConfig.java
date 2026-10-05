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
                        || offer.socketCount() < 1 || offer.verification() == null || offer.hubEquipmentId() == null
                        || offer.socketEquipmentId() == null || offer.hubModel() == null) throw new IllegalStateException("Invalid shop offer");
            }
            return new ShopData.Definition(catalog.version(), catalog.currency(), java.util.List.copyOf(catalog.offers()));
        }
    }
    @Bean
    public ThreadPoolTaskScheduler shopTaskScheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1); scheduler.setThreadNamePrefix("shop-notification-");
        return scheduler;
    }
}

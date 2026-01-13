package ru.growerhub.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class FirmwareResourceConfig implements WebMvcConfigurer {
    private final FirmwareSettings firmwareSettings;

    public FirmwareResourceConfig(FirmwareSettings firmwareSettings) {
        this.firmwareSettings = firmwareSettings;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = firmwareSettings.getFirmwareDir().toUri().toString();
        if (!location.endsWith("/")) {
            location = location + "/";
        }
        registry.addResourceHandler("/firmware/**")
                .addResourceLocations(location)
                .setCacheControl(CacheControl.noStore());
    }
}

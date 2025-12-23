package ru.growerhub.backend.firmware;

import java.nio.file.Path;
import java.nio.file.Paths;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class FirmwareSettings {
    private final String serverPublicBaseUrl;
    private final Path firmwareDir;

    public FirmwareSettings(
            @Value("${SERVER_PUBLIC_BASE_URL:https://growerhub.ru}") String serverPublicBaseUrl,
            @Value("${FIRMWARE_BINARIES_DIR:server/firmware_binaries}") String firmwareDir
    ) {
        this.serverPublicBaseUrl = serverPublicBaseUrl;
        this.firmwareDir = Paths.get(firmwareDir).toAbsolutePath().normalize();
    }

    public String getServerPublicBaseUrl() {
        return serverPublicBaseUrl;
    }

    public Path getFirmwareDir() {
        return firmwareDir;
    }
}

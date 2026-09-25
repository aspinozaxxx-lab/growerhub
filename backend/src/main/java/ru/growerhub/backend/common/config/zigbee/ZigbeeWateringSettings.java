package ru.growerhub.backend.common.config.zigbee;

import java.util.List;
import java.util.UUID;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "zigbee.watering")
public record ZigbeeWateringSettings(boolean enabled, int stateFreshnessSeconds,
                                    int startConfirmationSeconds, int simulatedMaxDurationSeconds,
                                    List<VerifiedDevice> verifiedDevices) {
    public ZigbeeWateringSettings {
        verifiedDevices = verifiedDevices == null ? List.of() : List.copyOf(verifiedDevices);
    }

    public record VerifiedDevice(UUID coordinatorId, String ieeeAddress, String definitionSha256,
                                 String stateProperty, String durationProperty, String onValue, String offValue,
                                 int maxDurationSeconds, int durationUnitSeconds,
                                 String verifiedAt, String evidenceReference, String softwareBuildId,
                                 boolean duplicateCommandDoesNotExtendTimer) {}
}

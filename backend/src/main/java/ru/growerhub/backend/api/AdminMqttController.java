package ru.growerhub.backend.api;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.growerhub.backend.api.dto.MqttDtos;
import ru.growerhub.backend.common.contract.AuthenticatedUser;
import ru.growerhub.backend.mqtt.MqttMessageLog;
import ru.growerhub.backend.mqtt.MqttRecordedMessage;

@RestController
@Validated
public class AdminMqttController {
    private final MqttMessageLog messageLog;

    public AdminMqttController(MqttMessageLog messageLog) {
        this.messageLog = messageLog;
    }

    @GetMapping("/api/admin/mqtt/messages")
    public List<MqttDtos.MqttMessageResponse> listMessages(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(value = "topic", required = false) String topic,
            @RequestParam(value = "sender", required = false) String sender,
            @RequestParam(value = "limit", required = false) Integer limit
    ) {
        requireAdmin(user);
        return messageLog.list(topic, sender, limit).stream()
                .map(this::toResponse)
                .toList();
    }

    private MqttDtos.MqttMessageResponse toResponse(MqttRecordedMessage message) {
        return new MqttDtos.MqttMessageResponse(
                message.id(),
                message.receivedAt(),
                message.direction(),
                message.topic(),
                message.sender(),
                message.kind(),
                message.payload()
        );
    }

    private void requireAdmin(AuthenticatedUser user) {
        if (user == null || !user.isAdmin()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Nedostatochno prav");
        }
    }
}

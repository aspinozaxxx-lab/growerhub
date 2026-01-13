package ru.growerhub.backend.pump;

public record PumpAck(String correlationId, String result, String reason, String status) {
}


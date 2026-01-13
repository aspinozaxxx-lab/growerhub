package ru.growerhub.backend.pump.contract;

public record PumpAck(String correlationId, String result, String reason, String status) {
}


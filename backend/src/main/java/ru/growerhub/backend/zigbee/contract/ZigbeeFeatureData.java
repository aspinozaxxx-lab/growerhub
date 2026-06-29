package ru.growerhub.backend.zigbee.contract;

public record ZigbeeFeatureData(
        String type,
        String property,
        String name,
        String label,
        String description,
        Integer access,
        String unit,
        Object values,
        Object valueMin,
        Object valueMax,
        Object valueStep,
        Object valueOn,
        Object valueOff,
        Object valueToggle,
        String endpoint,
        Object value
) {
}

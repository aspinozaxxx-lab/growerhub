package ru.growerhub.backend.pump;

// Translitem: interfejs dlya uzkogo zaprosa statusa nasosa bez znaniya o shadow.
public interface PumpRunningStatusProvider {
    boolean isPumpRunning(String deviceId, int pumpChannel);
}

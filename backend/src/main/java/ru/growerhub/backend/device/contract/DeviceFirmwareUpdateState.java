package ru.growerhub.backend.device.contract;

public enum DeviceFirmwareUpdateState {
    IDLE,
    QUEUED,
    DOWNLOADING,
    RESTARTING,
    SUCCESS,
    ERROR
}

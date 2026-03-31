package ru.growerhub.backend.common.config.mqtt;

import org.springframework.boot.context.properties.ConfigurationProperties;

// Nastroyki mqtt topikov.
@ConfigurationProperties(prefix = "mqtt.topics")
public class MqttTopicSettings {
    private String state = "gh/dev/+/state";
    private String ack = "gh/dev/+/state/ack";
    private String events = "gh/dev/+/events";
    private String stateSuffix = "/state";
    private String ackSuffix = "/state/ack";
    private String eventsSuffix = "/events";

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getAck() {
        return ack;
    }

    public void setAck(String ack) {
        this.ack = ack;
    }

    public String getStateSuffix() {
        return stateSuffix;
    }

    public void setStateSuffix(String stateSuffix) {
        this.stateSuffix = stateSuffix;
    }

    public String getAckSuffix() {
        return ackSuffix;
    }

    public void setAckSuffix(String ackSuffix) {
        this.ackSuffix = ackSuffix;
    }

    public String getEvents() {
        return events;
    }

    public void setEvents(String events) {
        this.events = events;
    }

    public String getEventsSuffix() {
        return eventsSuffix;
    }

    public void setEventsSuffix(String eventsSuffix) {
        this.eventsSuffix = eventsSuffix;
    }
}

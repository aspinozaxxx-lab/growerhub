package ru.growerhub.backend.common.config.mqtt;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

// Nastroyki mqtt topikov.
@ConfigurationProperties(prefix = "mqtt.topics")
public class MqttTopicSettings {
    private String listen;
    private List<String> listenTopics = List.of("gh/dev/#", "zigbee2growerhub/#", "gh/z2m/#");
    private String state = "gh/dev/+/state";
    private String ack = "gh/dev/+/state/ack";
    private String events = "gh/dev/+/events";
    private String zigbeeBase = "zigbee2growerhub";
    private String zigbeeUserPrefix = "gh/z2m";
    private String stateSuffix = "/state";
    private String ackSuffix = "/state/ack";
    private String eventsSuffix = "/events";

    public String getListen() {
        return listen;
    }

    public void setListen(String listen) {
        this.listen = listen;
    }

    public List<String> getListenTopics() {
        return listenTopics;
    }

    public void setListenTopics(List<String> listenTopics) {
        this.listenTopics = listenTopics != null ? List.copyOf(listenTopics) : List.of();
    }

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

    public String getZigbeeBase() {
        return zigbeeBase;
    }

    public void setZigbeeBase(String zigbeeBase) {
        this.zigbeeBase = zigbeeBase;
    }

    public String getZigbeeUserPrefix() {
        return zigbeeUserPrefix;
    }

    public void setZigbeeUserPrefix(String zigbeeUserPrefix) {
        this.zigbeeUserPrefix = zigbeeUserPrefix;
    }

    public String getEventsSuffix() {
        return eventsSuffix;
    }

    public void setEventsSuffix(String eventsSuffix) {
        this.eventsSuffix = eventsSuffix;
    }
}

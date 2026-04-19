package com.dawn.mqtt;

import android.text.TextUtils;

public final class MqttSubscription {
    private final String topic;
    private final int qos;

    public MqttSubscription(String topic, int qos) {
        if (TextUtils.isEmpty(topic)) {
            throw new IllegalArgumentException("topic can not be empty");
        }
        this.topic = topic;
        this.qos = MqttUtils.normalizeQos(qos);
    }

    public String getTopic() {
        return topic;
    }

    public int getQos() {
        return qos;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MqttSubscription that = (MqttSubscription) o;
        return qos == that.qos && topic.equals(that.topic);
    }

    @Override
    public int hashCode() {
        return 31 * topic.hashCode() + qos;
    }

    @Override
    public String toString() {
        return "MqttSubscription{topic='" + topic + "', qos=" + qos + "}";
    }
}
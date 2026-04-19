package com.dawn.mqtt;

final class MqttUtils {

    private MqttUtils() {
    }

    static int normalizeQos(int qos) {
        return Math.max(0, Math.min(2, qos));
    }
}

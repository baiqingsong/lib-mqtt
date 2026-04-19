package com.dawn.mqtt;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttMessage;

public interface MqttListener {
    default void onStateChanged(MqttState state) {
    }

    default void onConnected(boolean reconnect, String serverUri) {
    }

    default void onConnectFailure(Throwable throwable) {
    }

    default void onConnectionLost(Throwable throwable) {
    }

    default void onDisconnected(boolean byUser) {
    }

    default void onMessageArrived(String topic, MqttMessage message) {
        if (message != null) {
            onMessagePayload(topic, message.toString());
        }
    }

    default void onMessagePayload(String topic, String payload) {
    }

    default void onDeliveryComplete(IMqttDeliveryToken token) {
    }

    default void onSubscribed(MqttSubscription subscription) {
    }

    default void onUnsubscribed(String topic) {
    }

    default void onError(MqttAction action, Throwable throwable) {
    }
}

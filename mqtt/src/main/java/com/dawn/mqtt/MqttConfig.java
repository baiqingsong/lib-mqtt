package com.dawn.mqtt;

import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.net.ssl.SSLSocketFactory;

public final class MqttConfig {
    private final String serverUri;
    private final String clientId;
    private final String username;
    private final String password;
    private final String defaultPublishTopic;
    private final List<MqttSubscription> subscriptions;
    private final String onlinePayload;
    private final String offlinePayload;
    private final String reconnectPayload;
    private final int connectionTimeoutSeconds;
    private final int keepAliveSeconds;
    private final int publishQos;
    private final int subscribeQos;
    private final int willQos;
    private final boolean cleanSession;
    private final boolean automaticReconnect;
    private final boolean statusRetained;
    private final boolean willRetained;
    private final SSLSocketFactory sslSocketFactory;
    private final int disconnectTimeoutMs;
    private final int maxReconnectDelayMs;
    private final int maxRetryCount;
    private final int initialRetryDelayMs;

    private MqttConfig(Builder builder) {
        serverUri = builder.serverUri;
        clientId = builder.clientId;
        username = builder.username;
        password = builder.password;
        defaultPublishTopic = builder.defaultPublishTopic;
        subscriptions = Collections.unmodifiableList(new ArrayList<>(builder.subscriptions.values()));
        onlinePayload = builder.onlinePayload;
        offlinePayload = builder.offlinePayload;
        reconnectPayload = builder.reconnectPayload;
        connectionTimeoutSeconds = builder.connectionTimeoutSeconds;
        keepAliveSeconds = builder.keepAliveSeconds;
        publishQos = builder.publishQos;
        subscribeQos = builder.subscribeQos;
        willQos = builder.willQos;
        cleanSession = builder.cleanSession;
        automaticReconnect = builder.automaticReconnect;
        statusRetained = builder.statusRetained;
        willRetained = builder.willRetained;
        sslSocketFactory = builder.sslSocketFactory;
        disconnectTimeoutMs = builder.disconnectTimeoutMs;
        maxReconnectDelayMs = builder.maxReconnectDelayMs;
        maxRetryCount = builder.maxRetryCount;
        initialRetryDelayMs = builder.initialRetryDelayMs;
    }

    public static Builder builder(String serverUri, String clientId) {
        return new Builder(serverUri, clientId);
    }

    public String getServerUri() {
        return serverUri;
    }

    public String getClientId() {
        return clientId;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getDefaultPublishTopic() {
        return defaultPublishTopic;
    }

    public List<MqttSubscription> getSubscriptions() {
        return subscriptions;
    }

    public String getOnlinePayload() {
        return onlinePayload;
    }

    public String getOfflinePayload() {
        return offlinePayload;
    }

    public String getReconnectPayload() {
        return reconnectPayload;
    }

    public int getConnectionTimeoutSeconds() {
        return connectionTimeoutSeconds;
    }

    public int getKeepAliveSeconds() {
        return keepAliveSeconds;
    }

    public int getPublishQos() {
        return publishQos;
    }

    public int getSubscribeQos() {
        return subscribeQos;
    }

    public int getWillQos() {
        return willQos;
    }

    public boolean isCleanSession() {
        return cleanSession;
    }

    public boolean isAutomaticReconnect() {
        return automaticReconnect;
    }

    public boolean isStatusRetained() {
        return statusRetained;
    }

    public boolean isWillRetained() {
        return willRetained;
    }

    public SSLSocketFactory getSslSocketFactory() {
        return sslSocketFactory;
    }

    public int getDisconnectTimeoutMs() {
        return disconnectTimeoutMs;
    }

    public int getMaxReconnectDelayMs() {
        return maxReconnectDelayMs;
    }

    public int getMaxRetryCount() {
        return maxRetryCount;
    }

    public int getInitialRetryDelayMs() {
        return initialRetryDelayMs;
    }

    @Override
    public String toString() {
        return "MqttConfig{" +
                "serverUri='" + serverUri + '\'' +
                ", clientId='" + clientId + '\'' +
                ", username='" + username + '\'' +
                ", password='" + (TextUtils.isEmpty(password) ? "" : "****") + '\'' +
                ", defaultPublishTopic='" + defaultPublishTopic + '\'' +
                ", subscriptions=" + subscriptions +
                ", ssl=" + (sslSocketFactory != null) +
                ", cleanSession=" + cleanSession +
                ", automaticReconnect=" + automaticReconnect +
                ", keepAliveSeconds=" + keepAliveSeconds +
                ", connectionTimeoutSeconds=" + connectionTimeoutSeconds +
                ", disconnectTimeoutMs=" + disconnectTimeoutMs +
                '}';
    }

    public static final class Builder {
        private final String serverUri;
        private final String clientId;
        private String username;
        private String password;
        private String defaultPublishTopic;
        private final Map<String, MqttSubscription> subscriptions = new LinkedHashMap<>();
        private String onlinePayload;
        private String offlinePayload;
        private String reconnectPayload;
        private int connectionTimeoutSeconds = 10;
        private int keepAliveSeconds = 20;
        private int publishQos = 1;
        private int subscribeQos = 1;
        private int willQos = 1;
        private boolean cleanSession = false;
        private boolean automaticReconnect = true;
        private boolean statusRetained = false;
        private boolean willRetained = true;
        private SSLSocketFactory sslSocketFactory;
        private int disconnectTimeoutMs = 5000;
        private int maxReconnectDelayMs = 128000;
        private int maxRetryCount = 0;
        private int initialRetryDelayMs = 1000;

        private Builder(String serverUri, String clientId) {
            this.serverUri = serverUri;
            this.clientId = clientId;
        }

        public Builder username(String username) {
            this.username = username;
            return this;
        }

        public Builder password(String password) {
            this.password = password;
            return this;
        }

        public Builder defaultPublishTopic(String defaultPublishTopic) {
            this.defaultPublishTopic = defaultPublishTopic;
            return this;
        }

        public Builder subscribeTopic(String topic) {
            return subscribeTopic(topic, subscribeQos);
        }

        public Builder subscribeTopic(String topic, int qos) {
            if (!TextUtils.isEmpty(topic)) {
                subscriptions.put(topic, new MqttSubscription(topic, qos));
            }
            return this;
        }

        public Builder subscribe(MqttSubscription subscription) {
            if (subscription != null) {
                subscriptions.put(subscription.getTopic(), subscription);
            }
            return this;
        }

        public Builder subscribeTopics(Collection<String> topics) {
            if (topics == null) {
                return this;
            }
            for (String topic : topics) {
                subscribeTopic(topic);
            }
            return this;
        }

        public Builder subscribeAll(Collection<MqttSubscription> topics) {
            if (topics == null) {
                return this;
            }
            for (MqttSubscription subscription : topics) {
                subscribe(subscription);
            }
            return this;
        }

        public Builder onlinePayload(String onlinePayload) {
            this.onlinePayload = onlinePayload;
            return this;
        }

        public Builder offlinePayload(String offlinePayload) {
            this.offlinePayload = offlinePayload;
            return this;
        }

        public Builder reconnectPayload(String reconnectPayload) {
            this.reconnectPayload = reconnectPayload;
            return this;
        }

        public Builder connectionTimeoutSeconds(int connectionTimeoutSeconds) {
            if (connectionTimeoutSeconds > 0) {
                this.connectionTimeoutSeconds = connectionTimeoutSeconds;
            }
            return this;
        }

        public Builder keepAliveSeconds(int keepAliveSeconds) {
            if (keepAliveSeconds > 0) {
                this.keepAliveSeconds = keepAliveSeconds;
            }
            return this;
        }

        public Builder publishQos(int publishQos) {
            this.publishQos = MqttUtils.normalizeQos(publishQos);
            return this;
        }

        public Builder subscribeQos(int subscribeQos) {
            this.subscribeQos = MqttUtils.normalizeQos(subscribeQos);
            return this;
        }

        public Builder willQos(int willQos) {
            this.willQos = MqttUtils.normalizeQos(willQos);
            return this;
        }

        public Builder cleanSession(boolean cleanSession) {
            this.cleanSession = cleanSession;
            return this;
        }

        public Builder automaticReconnect(boolean automaticReconnect) {
            this.automaticReconnect = automaticReconnect;
            return this;
        }

        public Builder statusRetained(boolean statusRetained) {
            this.statusRetained = statusRetained;
            return this;
        }

        public Builder willRetained(boolean willRetained) {
            this.willRetained = willRetained;
            return this;
        }

        public Builder sslSocketFactory(SSLSocketFactory sslSocketFactory) {
            this.sslSocketFactory = sslSocketFactory;
            return this;
        }

        public Builder disconnectTimeoutMs(int disconnectTimeoutMs) {
            if (disconnectTimeoutMs > 0) {
                this.disconnectTimeoutMs = disconnectTimeoutMs;
            }
            return this;
        }

        public Builder maxReconnectDelayMs(int maxReconnectDelayMs) {
            if (maxReconnectDelayMs > 0) {
                this.maxReconnectDelayMs = maxReconnectDelayMs;
            }
            return this;
        }

        public Builder maxRetryCount(int maxRetryCount) {
            this.maxRetryCount = Math.max(0, maxRetryCount);
            return this;
        }

        public Builder initialRetryDelayMs(int initialRetryDelayMs) {
            if (initialRetryDelayMs > 0) {
                this.initialRetryDelayMs = initialRetryDelayMs;
            }
            return this;
        }

        public MqttConfig build() {
            if (TextUtils.isEmpty(serverUri)) {
                throw new IllegalArgumentException("serverUri can not be empty");
            }
            if (TextUtils.isEmpty(clientId)) {
                throw new IllegalArgumentException("clientId can not be empty");
            }
            if (!serverUri.startsWith("tcp://") && !serverUri.startsWith("ssl://")
                    && !serverUri.startsWith("ws://") && !serverUri.startsWith("wss://")) {
                throw new IllegalArgumentException(
                        "serverUri must start with tcp://, ssl://, ws://, or wss://");
            }
            return new MqttConfig(this);
        }

    }
}
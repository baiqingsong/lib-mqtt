package com.dawn.mqtt;

import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public final class MqttFactory {
    private static final String TAG = "MqttFactory";
    private static final MqttFactory INSTANCE = new MqttFactory();

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Object clientLock = new Object();
    private final Map<String, MqttSubscription> subscriptions = new LinkedHashMap<>();

    private ExecutorService executorService = createExecutor();
    private volatile MqttClient mqttClient;
    private volatile MqttConnectOptions mqttConnectOptions;
    private volatile MqttConfig mqttConfig;
    private volatile MqttListener mqttListener;
    private volatile MqttState mqttState = MqttState.IDLE;
    private volatile boolean manualDisconnect;
    private volatile boolean connecting;
    private volatile int retryCount;

    private final Runnable retryRunnable = new Runnable() {
        @Override
        public void run() {
            if (!manualDisconnect && mqttState != MqttState.CONNECTED && mqttState != MqttState.RELEASED) {
                runOnWorker(new Runnable() {
                    @Override
                    public void run() {
                        connectInternal(true);
                    }
                });
            }
        }
    };

    public static MqttFactory getInstance() {
        return INSTANCE;
    }

    private MqttFactory() {
    }

    public void init(MqttConfig config, MqttListener listener) {
        configure(config);
        setListener(listener);
        connect();
    }

    public void configure(MqttConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("MqttConfig can not be null");
        }
        if (isConnected() || isConnecting()) {
            disconnect();
        }
        mqttConfig = config;
        synchronized (clientLock) {
            subscriptions.clear();
            for (MqttSubscription subscription : config.getSubscriptions()) {
                subscriptions.put(subscription.getTopic(), subscription);
            }
        }
    }

    public MqttConfig getConfig() {
        return mqttConfig;
    }

    public void setListener(MqttListener listener) {
        mqttListener = listener;
    }

    public void clearListener() {
        mqttListener = null;
    }

    public MqttState getState() {
        return mqttState;
    }

    public boolean isConnecting() {
        return connecting || mqttState == MqttState.CONNECTING;
    }

    public boolean isConnected() {
        MqttClient client = mqttClient;
        return client != null && client.isConnected() && mqttState == MqttState.CONNECTED;
    }

    public List<MqttSubscription> getSubscribedTopics() {
        synchronized (clientLock) {
            return Collections.unmodifiableList(new ArrayList<>(subscriptions.values()));
        }
    }

    public void connect() {
        manualDisconnect = false;
        cancelRetry();
        runOnWorker(new Runnable() {
            @Override
            public void run() {
                connectInternal(false);
            }
        });
    }

    public void reconnect() {
        manualDisconnect = false;
        cancelRetry();
        runOnWorker(new Runnable() {
            @Override
            public void run() {
                connectInternal(true);
            }
        });
    }

    public void disconnect() {
        manualDisconnect = true;
        cancelRetry();
        runOnWorker(new Runnable() {
            @Override
            public void run() {
                disconnectInternal(false);
            }
        });
    }

    public void release() {
        manualDisconnect = true;
        cancelRetry();
        runOnWorker(new Runnable() {
            @Override
            public void run() {
                disconnectInternal(true);
            }
        });
    }

    public void subscribe(String topic) {
        MqttConfig config = mqttConfig;
        int qos = config == null ? 1 : config.getSubscribeQos();
        subscribe(new MqttSubscription(topic, qos));
    }

    public void subscribe(String topic, int qos) {
        subscribe(new MqttSubscription(topic, qos));
    }

    public void subscribe(MqttSubscription subscription) {
        if (subscription == null) {
            return;
        }
        synchronized (clientLock) {
            subscriptions.put(subscription.getTopic(), subscription);
        }
        if (isConnected()) {
            runOnWorker(new Runnable() {
                @Override
                public void run() {
                    subscribeInternal(subscription);
                }
            });
        }
    }

    public void subscribeAll(Collection<MqttSubscription> topics) {
        if (topics == null || topics.isEmpty()) {
            return;
        }
        for (MqttSubscription subscription : topics) {
            subscribe(subscription);
        }
    }

    public void unsubscribe(String topic) {
        if (TextUtils.isEmpty(topic)) {
            return;
        }
        synchronized (clientLock) {
            subscriptions.remove(topic);
        }
        runOnWorker(new Runnable() {
            @Override
            public void run() {
                unsubscribeInternal(topic);
            }
        });
    }

    public void unsubscribeAll(Collection<String> topics) {
        if (topics == null || topics.isEmpty()) {
            return;
        }
        for (String topic : topics) {
            unsubscribe(topic);
        }
    }

    public void clearSubscriptions(boolean unsubscribeRemote) {
        List<String> topics;
        synchronized (clientLock) {
            topics = new ArrayList<>(subscriptions.keySet());
            subscriptions.clear();
        }
        if (unsubscribeRemote) {
            unsubscribeAll(topics);
        }
    }

    public void publish(String payload) {
        MqttConfig config = mqttConfig;
        if (config == null || TextUtils.isEmpty(config.getDefaultPublishTopic())) {
            notifyError(MqttAction.PUBLISH, new IllegalStateException("Default publish topic is not configured"));
            return;
        }
        publish(config.getDefaultPublishTopic(), payload, config.getPublishQos(), false);
    }

    public void publish(String topic, String payload) {
        MqttConfig config = mqttConfig;
        int qos = config == null ? 1 : config.getPublishQos();
        publish(topic, payload, qos, false);
    }

    public void publish(String topic, String payload, boolean retained) {
        MqttConfig config = mqttConfig;
        int qos = config == null ? 1 : config.getPublishQos();
        publish(topic, payload, qos, retained);
    }

    public void publish(String topic, String payload, int qos, boolean retained) {
        if (payload == null) {
            notifyError(MqttAction.PUBLISH, new IllegalArgumentException("payload can not be null"));
            return;
        }
        publish(topic, payload.getBytes(StandardCharsets.UTF_8), qos, retained);
    }

    public void publish(String topic, byte[] payload, int qos, boolean retained) {
        if (TextUtils.isEmpty(topic)) {
            notifyError(MqttAction.PUBLISH, new IllegalArgumentException("topic can not be empty"));
            return;
        }
        MqttMessage message = new MqttMessage(payload == null ? new byte[0] : payload);
        message.setQos(normalizeQos(qos));
        message.setRetained(retained);
        publish(topic, message);
    }

    public void publish(String topic, MqttMessage message) {
        runOnWorker(new Runnable() {
            @Override
            public void run() {
                publishInternal(topic, message);
            }
        });
    }

    private void connectInternal(boolean recreateClient) {
        if (connecting) {
            return;
        }
        MqttConfig config = mqttConfig;
        if (config == null) {
            notifyError(MqttAction.CONNECT, new IllegalStateException("Please call init() or configure() before connect()"));
            return;
        }

        connecting = true;
        updateState(MqttState.CONNECTING);
        try {
            MqttClient client;
            synchronized (clientLock) {
                if (recreateClient) {
                    closeClientLocked();
                }
                ensureClientLocked(config);
                client = mqttClient;
            }
            if (client != null && client.isConnected()) {
                updateState(MqttState.CONNECTED);
                retryCount = 0;
                return;
            }
            if (client != null) {
                client.connect(mqttConnectOptions);
            }
        } catch (Exception e) {
            Log.e(TAG, "mqtt connect failed", e);
            updateState(MqttState.DISCONNECTED);
            notifyConnectFailure(e);
            scheduleRetry();
        } finally {
            connecting = false;
        }
    }

    private void disconnectInternal(boolean releaseClient) {
        MqttConfig config = mqttConfig;
        long timeout = config != null ? config.getDisconnectTimeoutMs() : 5000;
        synchronized (clientLock) {
            try {
                if (mqttClient != null) {
                    if (mqttClient.isConnected()) {
                        mqttClient.disconnect(timeout);
                    }
                    mqttClient.close();
                    mqttClient = null;
                }
            } catch (Exception e) {
                Log.e(TAG, "mqtt disconnect failed", e);
                notifyError(releaseClient ? MqttAction.RELEASE : MqttAction.DISCONNECT, e);
            } finally {
                mqttConnectOptions = null;
                updateState(releaseClient ? MqttState.RELEASED : MqttState.DISCONNECTED);
                if (releaseClient) {
                    mqttConfig = null;
                    mqttListener = null;
                    subscriptions.clear();
                    resetExecutor();
                } else {
                    notifyDisconnected(true);
                }
            }
        }
    }

    private void ensureClientLocked(MqttConfig config) throws MqttException {
        if (mqttClient == null) {
            mqttClient = new MqttClient(config.getServerUri(), config.getClientId(), new MemoryPersistence());
            mqttClient.setCallback(new InternalMqttCallback());
        }
        mqttConnectOptions = buildConnectOptions(config);
    }

    private MqttConnectOptions buildConnectOptions(MqttConfig config) {
        MqttConnectOptions options = new MqttConnectOptions();
        if (!TextUtils.isEmpty(config.getUsername())) {
            options.setUserName(config.getUsername());
        }
        if (!TextUtils.isEmpty(config.getPassword())) {
            options.setPassword(config.getPassword().toCharArray());
        }
        if (!TextUtils.isEmpty(config.getDefaultPublishTopic()) && !TextUtils.isEmpty(config.getOfflinePayload())) {
            options.setWill(config.getDefaultPublishTopic(), config.getOfflinePayload().getBytes(StandardCharsets.UTF_8),
                    config.getWillQos(), config.isWillRetained());
        }
        options.setCleanSession(config.isCleanSession());
        options.setConnectionTimeout(config.getConnectionTimeoutSeconds());
        options.setKeepAliveInterval(config.getKeepAliveSeconds());
        options.setAutomaticReconnect(config.isAutomaticReconnect());
        options.setMaxReconnectDelay(config.getMaxReconnectDelayMs());
        if (config.getSslSocketFactory() != null) {
            options.setSocketFactory(config.getSslSocketFactory());
        }
        return options;
    }

    private void subscribeStoredTopics() {
        List<MqttSubscription> snapshot;
        synchronized (clientLock) {
            snapshot = new ArrayList<>(subscriptions.values());
        }
        if (snapshot.isEmpty()) {
            return;
        }
        MqttClient client = mqttClient;
        if (client == null || !client.isConnected()) {
            return;
        }
        String[] topics = new String[snapshot.size()];
        int[] qosArray = new int[snapshot.size()];
        for (int i = 0; i < snapshot.size(); i++) {
            topics[i] = snapshot.get(i).getTopic();
            qosArray[i] = snapshot.get(i).getQos();
        }
        try {
            client.subscribe(topics, qosArray);
            for (MqttSubscription sub : snapshot) {
                notifySubscribed(sub);
            }
        } catch (Exception e) {
            Log.e(TAG, "mqtt batch subscribe failed", e);
            notifyError(MqttAction.SUBSCRIBE, e);
        }
    }

    private void subscribeInternal(MqttSubscription subscription) {
        if (subscription == null || TextUtils.isEmpty(subscription.getTopic())) {
            return;
        }
        MqttClient client = mqttClient;
        if (client == null || !client.isConnected()) {
            return;
        }
        try {
            client.subscribe(subscription.getTopic(), subscription.getQos());
            notifySubscribed(subscription);
        } catch (Exception e) {
            Log.e(TAG, "mqtt subscribe failed: " + subscription.getTopic(), e);
            notifyError(MqttAction.SUBSCRIBE, e);
        }
    }

    private void unsubscribeInternal(String topic) {
        MqttClient client = mqttClient;
        if (client == null || TextUtils.isEmpty(topic) || !client.isConnected()) {
            return;
        }
        try {
            client.unsubscribe(topic);
            notifyUnsubscribed(topic);
        } catch (Exception e) {
            Log.e(TAG, "mqtt unsubscribe failed: " + topic, e);
            notifyError(MqttAction.UNSUBSCRIBE, e);
        }
    }

    private void publishInternal(String topic, MqttMessage message) {
        MqttClient client = mqttClient;
        if (client == null || !client.isConnected()) {
            notifyError(MqttAction.PUBLISH, new IllegalStateException("MQTT client is not connected"));
            return;
        }
        if (TextUtils.isEmpty(topic) || message == null) {
            notifyError(MqttAction.PUBLISH, new IllegalArgumentException("Topic or message is empty"));
            return;
        }
        try {
            client.publish(topic, message);
        } catch (Exception e) {
            Log.e(TAG, "mqtt publish failed: " + topic, e);
            notifyError(MqttAction.PUBLISH, e);
        }
    }

    private void publishStatusMessage(boolean reconnect) {
        MqttConfig config = mqttConfig;
        if (config == null || TextUtils.isEmpty(config.getDefaultPublishTopic())) {
            return;
        }
        String payload = reconnect ? config.getReconnectPayload() : config.getOnlinePayload();
        if (TextUtils.isEmpty(payload)) {
            return;
        }
        publish(config.getDefaultPublishTopic(), payload, config.getPublishQos(), config.isStatusRetained());
    }

    private void closeClientLocked() {
        if (mqttClient == null) {
            return;
        }
        try {
            if (mqttClient.isConnected()) {
                mqttClient.disconnect();
            }
            mqttClient.close();
        } catch (Exception e) {
            Log.w(TAG, "close mqtt client failed", e);
        } finally {
            mqttClient = null;
        }
    }

    private void updateState(final MqttState state) {
        mqttState = state;
        postToMainThread(new Runnable() {
            @Override
            public void run() {
                MqttListener listener = mqttListener;
                if (listener != null) {
                    listener.onStateChanged(state);
                }
            }
        });
    }

    private void notifyConnected(final boolean reconnect, final String serverUri) {
        postToMainThread(new Runnable() {
            @Override
            public void run() {
                MqttListener listener = mqttListener;
                if (listener != null) {
                    listener.onConnected(reconnect, serverUri);
                }
            }
        });
    }

    private void notifyConnectFailure(final Throwable throwable) {
        postToMainThread(new Runnable() {
            @Override
            public void run() {
                MqttListener listener = mqttListener;
                if (listener != null) {
                    listener.onConnectFailure(throwable);
                }
            }
        });
    }

    private void notifyMessageArrived(final String topic, final MqttMessage message) {
        postToMainThread(new Runnable() {
            @Override
            public void run() {
                MqttListener listener = mqttListener;
                if (listener != null) {
                    listener.onMessageArrived(topic, message);
                }
            }
        });
    }

    private void notifyConnectionLost(final Throwable throwable) {
        postToMainThread(new Runnable() {
            @Override
            public void run() {
                MqttListener listener = mqttListener;
                if (listener != null) {
                    listener.onConnectionLost(throwable);
                }
            }
        });
    }

    private void notifyDisconnected(final boolean byUser) {
        postToMainThread(new Runnable() {
            @Override
            public void run() {
                MqttListener listener = mqttListener;
                if (listener != null) {
                    listener.onDisconnected(byUser);
                }
            }
        });
    }

    private void notifySubscribed(final MqttSubscription subscription) {
        postToMainThread(new Runnable() {
            @Override
            public void run() {
                MqttListener listener = mqttListener;
                if (listener != null) {
                    listener.onSubscribed(subscription);
                }
            }
        });
    }

    private void notifyUnsubscribed(final String topic) {
        postToMainThread(new Runnable() {
            @Override
            public void run() {
                MqttListener listener = mqttListener;
                if (listener != null) {
                    listener.onUnsubscribed(topic);
                }
            }
        });
    }

    private void notifyDeliveryComplete(final IMqttDeliveryToken token) {
        postToMainThread(new Runnable() {
            @Override
            public void run() {
                MqttListener listener = mqttListener;
                if (listener != null) {
                    listener.onDeliveryComplete(token);
                }
            }
        });
    }

    private void notifyError(final MqttAction action, final Throwable throwable) {
        postToMainThread(new Runnable() {
            @Override
            public void run() {
                MqttListener listener = mqttListener;
                if (listener != null) {
                    listener.onError(action, throwable);
                }
            }
        });
    }

    private void postToMainThread(Runnable runnable) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runnable.run();
        } else {
            mainHandler.post(runnable);
        }
    }

    private void runOnWorker(Runnable runnable) {
        try {
            ensureExecutor();
            executorService.execute(runnable);
        } catch (RejectedExecutionException e) {
            Log.e(TAG, "Worker task rejected, executor may be shut down", e);
        }
    }

    private void ensureExecutor() {
        if (executorService != null && !executorService.isShutdown()) {
            return;
        }
        synchronized (clientLock) {
            if (executorService == null || executorService.isShutdown()) {
                executorService = createExecutor();
            }
        }
    }

    private void resetExecutor() {
        ExecutorService currentExecutor = executorService;
        executorService = null;
        if (currentExecutor != null) {
            currentExecutor.shutdown();
        }
    }

    private ExecutorService createExecutor() {
        final AtomicInteger index = new AtomicInteger(1);
        return Executors.newSingleThreadExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, "mqtt-worker-" + index.getAndIncrement());
                thread.setDaemon(true);
                return thread;
            }
        });
    }

    private int normalizeQos(int qos) {
        return MqttUtils.normalizeQos(qos);
    }

    private void scheduleRetry() {
        if (manualDisconnect) {
            return;
        }
        MqttConfig config = mqttConfig;
        if (config == null || !config.isAutomaticReconnect()) {
            return;
        }
        int maxRetry = config.getMaxRetryCount();
        if (maxRetry > 0 && retryCount >= maxRetry) {
            Log.w(TAG, "Max retry count reached: " + maxRetry);
            return;
        }
        long delay = Math.min(
                (long) config.getInitialRetryDelayMs() * (1L << Math.min(retryCount, 20)),
                config.getMaxReconnectDelayMs());
        retryCount++;
        Log.i(TAG, "Scheduling reconnect attempt " + retryCount + " in " + delay + "ms");
        mainHandler.postDelayed(retryRunnable, delay);
    }

    private void cancelRetry() {
        retryCount = 0;
        mainHandler.removeCallbacks(retryRunnable);
    }

    private class InternalMqttCallback implements MqttCallbackExtended {
        @Override
        public void connectComplete(boolean reconnect, String serverUri) {
            retryCount = 0;
            updateState(MqttState.CONNECTED);
            subscribeStoredTopics();
            publishStatusMessage(reconnect);
            notifyConnected(reconnect, serverUri);
        }

        @Override
        public void connectionLost(Throwable cause) {
            updateState(MqttState.DISCONNECTED);
            if (!manualDisconnect) {
                notifyConnectionLost(cause);
                notifyDisconnected(false);
                scheduleRetry();
            }
        }

        @Override
        public void messageArrived(String topic, MqttMessage message) {
            notifyMessageArrived(topic, message);
        }

        @Override
        public void deliveryComplete(IMqttDeliveryToken token) {
            notifyDeliveryComplete(token);
        }
    }
}
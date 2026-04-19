# lib-mqtt

Android MQTT 客户端封装库，基于 [Eclipse Paho MQTT v3](https://github.com/eclipse/paho.mqtt.java) 封装，提供简洁的 API 用于 MQTT 连接管理、消息订阅与发布。

## 特性

- 单例模式，全局统一管理 MQTT 连接
- Builder 模式构建配置，参数清晰可控
- 所有网络操作在后台线程执行，回调自动切换到主线程
- 支持 SSL/TLS 加密连接
- 双层重连保障：Paho 自动重连 + 自定义指数退避重试
- 支持遗嘱消息（Will Message）
- 支持上线 / 离线 / 重连状态消息自动发布
- 批量订阅优化，减少网络往返
- ProGuard 混淆规则已内置

## 环境要求

- Android minSdk 28+
- Java 8+
- Gradle 7.6 + AGP 7.4.2

## 引用

### JitPack 引用

根 `build.gradle` 添加仓库：

```groovy
allprojects {
    repositories {
        google()
        mavenCentral()
        maven { url "https://jitpack.io" }
    }
}
```

模块 `build.gradle` 添加依赖：

```groovy
dependencies {
    implementation 'com.github.baiqingsong:lib-mqtt:版本号'
}
```

### 核心依赖

```groovy
// mqtt 模块已包含以下依赖，会自动传递
implementation 'org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5'
```

## 权限

模块已在 `AndroidManifest.xml` 中声明，会自动合并到主工程：

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

---

## 类说明

### MqttFactory

MQTT 客户端核心管理类，采用单例模式。负责连接的建立、断开、重连，以及消息的订阅和发布。所有对外方法均可在主线程调用，内部自动切换到工作线程执行。

### MqttConfig

MQTT 连接配置模型，不可变对象。通过 `Builder` 模式构建，包含服务器地址、认证信息、订阅主题、QoS、重连策略等全部配置参数。

### MqttListener

MQTT 事件监听接口。所有方法均为 `default` 方法，可按需选择性重写。回调均在主线程执行。

### MqttSubscription

订阅主题模型，封装主题名称和 QoS 等级。不可变对象，已正确实现 `equals()`、`hashCode()`、`toString()`。

### MqttState

连接状态枚举：

| 值 | 说明 |
|---|---|
| `IDLE` | 初始状态，尚未连接过 |
| `CONNECTING` | 正在连接中 |
| `CONNECTED` | 已连接 |
| `DISCONNECTED` | 已断开连接 |
| `RELEASED` | 已释放（清除全部配置和资源） |

### MqttAction

操作类型枚举，用于 `onError` 回调中标识错误来源：

| 值 | 说明 |
|---|---|
| `CONNECT` | 连接操作 |
| `DISCONNECT` | 断开操作 |
| `RELEASE` | 释放操作 |
| `SUBSCRIBE` | 订阅操作 |
| `UNSUBSCRIBE` | 取消订阅操作 |
| `PUBLISH` | 发布操作 |

---

## MqttConfig.Builder 参数说明

通过 `MqttConfig.builder(serverUri, clientId)` 创建 Builder：

| 方法 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `username(String)` | String | null | 认证用户名 |
| `password(String)` | String | null | 认证密码 |
| `defaultPublishTopic(String)` | String | null | 默认发布主题，用于 `publish(payload)` 和遗嘱消息 |
| `subscribeTopic(String)` | - | - | 添加订阅主题，使用默认 QoS |
| `subscribeTopic(String, int)` | - | - | 添加订阅主题，指定 QoS |
| `subscribe(MqttSubscription)` | - | - | 添加订阅对象 |
| `subscribeTopics(Collection<String>)` | - | - | 批量添加订阅主题 |
| `subscribeAll(Collection<MqttSubscription>)` | - | - | 批量添加订阅对象 |
| `onlinePayload(String)` | String | null | 连接成功后自动发布的上线消息内容 |
| `offlinePayload(String)` | String | null | 遗嘱消息内容（Broker 检测到断开时发布） |
| `reconnectPayload(String)` | String | null | 重连成功后自动发布的消息内容 |
| `connectionTimeoutSeconds(int)` | int | 10 | 连接超时（秒） |
| `keepAliveSeconds(int)` | int | 20 | 心跳间隔（秒） |
| `publishQos(int)` | int | 1 | 默认发布 QoS（0-2） |
| `subscribeQos(int)` | int | 1 | 默认订阅 QoS（0-2） |
| `willQos(int)` | int | 1 | 遗嘱消息 QoS（0-2） |
| `cleanSession(boolean)` | boolean | false | 是否清除会话 |
| `automaticReconnect(boolean)` | boolean | true | 是否启用自动重连 |
| `statusRetained(boolean)` | boolean | false | 上线/重连状态消息是否保留 |
| `willRetained(boolean)` | boolean | true | 遗嘱消息是否保留 |
| `sslSocketFactory(SSLSocketFactory)` | SSLSocketFactory | null | SSL 加密连接的 SocketFactory |
| `disconnectTimeoutMs(int)` | int | 5000 | 断开连接超时（毫秒） |
| `maxReconnectDelayMs(int)` | int | 128000 | 最大重连间隔（毫秒） |
| `maxRetryCount(int)` | int | 0 | 最大重试次数，0 表示无限重试 |
| `initialRetryDelayMs(int)` | int | 1000 | 初始重试间隔（毫秒），每次翻倍 |

> **serverUri 格式要求**：必须以 `tcp://`、`ssl://`、`ws://` 或 `wss://` 开头，否则 `build()` 会抛出异常。

---

## MqttFactory 方法说明

### 获取实例

```java
MqttFactory mqtt = MqttFactory.getInstance();
```

### 连接生命周期

| 方法 | 说明 |
|------|------|
| `init(MqttConfig, MqttListener)` | 设置配置 + 设置监听 + 立即连接（一步完成） |
| `configure(MqttConfig)` | 仅设置配置，不连接。若当前已连接会先自动断开 |
| `connect()` | 发起连接，连接失败会自动按指数退避重试 |
| `reconnect()` | 强制重新连接（销毁旧 Client 后重建） |
| `disconnect()` | 主动断开连接，不释放配置，可再次 `connect()` |
| `release()` | 完全释放：断开连接 + 清除配置 + 清除监听 + 关闭线程池 |

**生命周期流程**：

```
configure() → connect() → disconnect() → connect() → ... → release()
         或
init()                  → disconnect() → connect() → ... → release()
```

### 监听管理

| 方法 | 说明 |
|------|------|
| `setListener(MqttListener)` | 设置事件监听器 |
| `clearListener()` | 清除监听器（防止内存泄漏） |

### 状态查询

| 方法 | 返回类型 | 说明 |
|------|---------|------|
| `getState()` | `MqttState` | 获取当前连接状态 |
| `isConnected()` | `boolean` | 是否已连接 |
| `isConnecting()` | `boolean` | 是否正在连接中 |
| `getConfig()` | `MqttConfig` | 获取当前配置 |
| `getSubscribedTopics()` | `List<MqttSubscription>` | 获取当前已注册的订阅列表 |

### 订阅管理

| 方法 | 说明 |
|------|------|
| `subscribe(String topic)` | 订阅主题，使用配置中的默认 QoS |
| `subscribe(String topic, int qos)` | 订阅主题，指定 QoS |
| `subscribe(MqttSubscription)` | 订阅主题对象 |
| `subscribeAll(Collection<MqttSubscription>)` | 批量订阅 |
| `unsubscribe(String topic)` | 取消订阅 |
| `unsubscribeAll(Collection<String>)` | 批量取消订阅 |
| `clearSubscriptions(boolean unsubscribeRemote)` | 清除所有订阅，`unsubscribeRemote=true` 会同时向 Broker 发送取消订阅 |

> 连接成功后会自动订阅所有已注册的主题（包括配置中预设的和运行时动态添加的）。

### 消息发布

| 方法 | 说明 |
|------|------|
| `publish(String payload)` | 发布到默认主题（需在配置中设置 `defaultPublishTopic`） |
| `publish(String topic, String payload)` | 发布到指定主题 |
| `publish(String topic, String payload, boolean retained)` | 发布到指定主题，指定是否保留 |
| `publish(String topic, String payload, int qos, boolean retained)` | 完整参数发布 |
| `publish(String topic, byte[] payload, int qos, boolean retained)` | 字节数组发布 |
| `publish(String topic, MqttMessage message)` | 直接发布 MqttMessage 对象 |

---

## MqttListener 回调说明

所有回调在**主线程**执行，所有方法均为 `default`，按需重写即可。

| 回调方法 | 参数说明 | 触发时机 |
|---------|---------|---------|
| `onStateChanged(MqttState state)` | 新状态 | 每次状态变化时 |
| `onConnected(boolean reconnect, String serverUri)` | 是否重连、服务器地址 | 连接成功（首次或自动重连后） |
| `onConnectFailure(Throwable throwable)` | 异常信息 | 连接失败时 |
| `onConnectionLost(Throwable throwable)` | 异常信息 | 连接意外断开（非主动断开） |
| `onDisconnected(boolean byUser)` | 是否用户主动断开 | 连接关闭时 |
| `onMessageArrived(String topic, MqttMessage message)` | 主题、原始消息 | 收到消息（默认会调用 `onMessagePayload`） |
| `onMessagePayload(String topic, String payload)` | 主题、字符串内容 | 收到消息的便捷回调 |
| `onDeliveryComplete(IMqttDeliveryToken token)` | 投递令牌 | QoS 1/2 消息投递确认 |
| `onSubscribed(MqttSubscription subscription)` | 订阅对象 | 订阅成功 |
| `onUnsubscribed(String topic)` | 主题名 | 取消订阅成功 |
| `onError(MqttAction action, Throwable throwable)` | 操作类型、异常 | 任何操作异常时 |

> **消息接收**：`onMessageArrived` 和 `onMessagePayload` 二选一重写即可。`onMessageArrived` 的默认实现会自动调用 `onMessagePayload`，如果你重写了 `onMessageArrived`，则 `onMessagePayload` 不会自动触发。

---

## 使用示例

### 1. 基础用法（快速连接）

```java
MqttConfig config = MqttConfig.builder("tcp://broker.emqx.io:1883", "my-client-id")
        .username("user")
        .password("pass")
        .defaultPublishTopic("device/status")
        .subscribeTopic("device/command")
        .onlinePayload("{\"cmd\":\"online\"}")
        .offlinePayload("{\"cmd\":\"offline\"}")
        .build();

MqttFactory.getInstance().init(config, new MqttListener() {
    @Override
    public void onConnected(boolean reconnect, String serverUri) {
        Log.i("MQTT", "已连接: " + serverUri + ", 重连=" + reconnect);
    }

    @Override
    public void onMessagePayload(String topic, String payload) {
        Log.i("MQTT", "收到消息: topic=" + topic + ", payload=" + payload);
    }

    @Override
    public void onError(MqttAction action, Throwable throwable) {
        Log.e("MQTT", action + " 出错: " + throwable.getMessage());
    }
});
```

### 2. 分步操作（先配置后连接）

```java
MqttFactory mqtt = MqttFactory.getInstance();

// 第一步：配置
MqttConfig config = MqttConfig.builder("tcp://192.168.1.100:1883", "device-001")
        .username("admin")
        .password("123456")
        .subscribeTopic("room/light", 2)
        .subscribeTopic("room/temperature", 1)
        .keepAliveSeconds(30)
        .build();
mqtt.configure(config);

// 第二步：设置监听
mqtt.setListener(new MqttListener() {
    @Override
    public void onStateChanged(MqttState state) {
        Log.i("MQTT", "状态: " + state);
    }

    @Override
    public void onMessagePayload(String topic, String payload) {
        if ("room/light".equals(topic)) {
            handleLightCommand(payload);
        } else if ("room/temperature".equals(topic)) {
            handleTemperature(payload);
        }
    }
});

// 第三步：连接
mqtt.connect();
```

### 3. 发布消息

```java
MqttFactory mqtt = MqttFactory.getInstance();

// 发布到默认主题（需配置 defaultPublishTopic）
mqtt.publish("{\"cmd\":\"heartbeat\"}");

// 发布到指定主题
mqtt.publish("device/data", "{\"temp\":25.6}");

// 发布保留消息
mqtt.publish("device/status", "{\"online\":true}", true);

// 完整参数发布
mqtt.publish("device/data", "{\"temp\":25.6}", 2, false);

// 发布字节数据
byte[] imageData = getImageBytes();
mqtt.publish("device/image", imageData, 0, false);
```

### 4. 动态订阅与取消

```java
MqttFactory mqtt = MqttFactory.getInstance();

// 运行时动态订阅新主题
mqtt.subscribe("room/door");
mqtt.subscribe("room/camera", 2);

// 批量订阅
List<MqttSubscription> topics = new ArrayList<>();
topics.add(new MqttSubscription("floor1/sensor", 1));
topics.add(new MqttSubscription("floor2/sensor", 1));
mqtt.subscribeAll(topics);

// 取消订阅
mqtt.unsubscribe("room/door");

// 清除所有订阅（同时通知 Broker）
mqtt.clearSubscriptions(true);
```

### 5. SSL/TLS 加密连接

```java
SSLSocketFactory sslFactory = createSslSocketFactory();

MqttConfig config = MqttConfig.builder("ssl://broker.emqx.io:8883", "secure-client")
        .username("user")
        .password("pass")
        .sslSocketFactory(sslFactory)
        .subscribeTopic("secure/topic")
        .build();

MqttFactory.getInstance().init(config, listener);
```

### 6. 自定义重连策略

```java
MqttConfig config = MqttConfig.builder("tcp://broker.emqx.io:1883", "retry-client")
        .automaticReconnect(true)       // 启用自动重连
        .maxRetryCount(10)              // 最多重试 10 次（0=无限）
        .initialRetryDelayMs(2000)      // 首次重试延迟 2 秒
        .maxReconnectDelayMs(60000)     // 最大重试间隔 60 秒
        .build();

// 重试间隔按指数退避递增：2s → 4s → 8s → 16s → 32s → 60s → 60s → ...
```

### 7. 完整生命周期管理（Activity 中）

```java
public class MqttActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        MqttConfig config = MqttConfig.builder("tcp://broker.emqx.io:1883", "app-client")
                .defaultPublishTopic("app/status")
                .subscribeTopic("app/command")
                .onlinePayload("{\"status\":\"online\"}")
                .offlinePayload("{\"status\":\"offline\"}")
                .reconnectPayload("{\"status\":\"reconnected\"}")
                .build();

        MqttFactory.getInstance().init(config, new MqttListener() {
            @Override
            public void onConnected(boolean reconnect, String serverUri) {
                // 连接成功，更新 UI
            }

            @Override
            public void onMessagePayload(String topic, String payload) {
                // 处理收到的消息
            }

            @Override
            public void onStateChanged(MqttState state) {
                // 状态变化，可更新连接状态指示器
            }

            @Override
            public void onError(MqttAction action, Throwable throwable) {
                // 处理异常
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 页面销毁时释放资源，防止内存泄漏
        MqttFactory.getInstance().release();
    }
}
```

### 8. 切换配置（重新连接不同 Broker）

```java
MqttFactory mqtt = MqttFactory.getInstance();

// configure() 会自动断开当前连接
MqttConfig newConfig = MqttConfig.builder("tcp://new-broker:1883", "new-client-id")
        .subscribeTopic("new/topic")
        .build();
mqtt.configure(newConfig);
mqtt.connect();
```

---

## 重连机制说明

本库提供两层重连保障：

1. **Paho 自动重连**：连接成功后若意外断开，Paho 库自身会尝试自动重连（由 `automaticReconnect(true)` 控制）。适用于网络短暂波动场景，恢复快、开销小。
2. **自定义指数退避重试**：首次连接失败或 Paho 重连未能恢复时，由本库的 `scheduleRetry()` 处理。每次间隔翻倍，直到 `maxReconnectDelayMs` 上限。可通过 `maxRetryCount` 控制最大重试次数（0 = 无限重试）。

重试间隔计算公式：

```
delay = min(initialRetryDelayMs × 2^retryCount, maxReconnectDelayMs)
```

调用 `disconnect()` 或 `release()` 会**立即取消**所有待执行的重试。

---

## 注意事项

1. **生命周期管理**：在 `Activity.onDestroy()` 或 `Fragment.onDestroyView()` 中务必调用 `release()` 或 `clearListener()`，防止内存泄漏。
2. **线程安全**：所有公开方法均可在任意线程调用，内部通过单线程池序列化执行。
3. **消息格式**：`publish()` 支持 String 和 byte[] 两种 payload 格式，String 使用 UTF-8 编码。
4. **QoS 等级**：自动将 QoS 值归一化到 0-2 范围内，超出范围不会报错。
5. **遗嘱消息**：需同时配置 `defaultPublishTopic` 和 `offlinePayload`，否则遗嘱不会生效。
6. **批量订阅**：连接成功后自动批量订阅所有已注册主题（一次网络往返），运行时动态添加的主题会立即订阅。
7. **Clean Session**：默认 `false`，Broker 会保留离线消息。设置为 `true` 则每次连接重新开始。
8. **快速重启**：`disconnect()` 后可再次 `connect()`，无需重新 `configure()`。`release()` 后需重新 `init()` 或 `configure()`。

---

## 项目结构

```
mqtt/src/main/java/com/dawn/mqtt/
├── MqttFactory.java        // 核心管理类（单例）
├── MqttConfig.java         // 配置模型（Builder 模式）
├── MqttListener.java       // 事件监听接口
├── MqttSubscription.java   // 订阅主题模型
├── MqttState.java          // 连接状态枚举
├── MqttAction.java         // 操作类型枚举
└── MqttUtils.java          // 内部工具类
```

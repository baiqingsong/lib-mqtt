package com.dawn.libmqtt;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.util.Log;

import com.dawn.mqtt.MqttAction;
import com.dawn.mqtt.MqttConfig;
import com.dawn.mqtt.MqttFactory;
import com.dawn.mqtt.MqttListener;
import com.dawn.mqtt.MqttState;
import com.dawn.mqtt.MqttSubscription;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 示例：构建 MQTT 配置并连接
//        MqttConfig mqttConfig = MqttConfig.builder("tcp://broker.emqx.io:1883", "android-client-id")
//                .username("username")
//                .password("password")
//                .defaultPublishTopic("device/status")
//                .subscribe(new MqttSubscription("device/command", 1))
//                .onlinePayload("{\"cmd\":\"info\",\"data\":\"设备上线\"}")
//                .offlinePayload("{\"cmd\":\"bye\",\"data\":\"设备离线\"}")
//                .reconnectPayload("{\"cmd\":\"info\",\"data\":\"设备重连\"}")
//                .keepAliveSeconds(20)
//                .maxRetryCount(0)          // 0 = 无限重试
//                .initialRetryDelayMs(2000)
//                .build();
//
//        MqttFactory.getInstance().init(mqttConfig, new MqttListener() {
//            @Override
//            public void onStateChanged(MqttState state) {
//                Log.i(TAG, "mqtt state=" + state);
//            }
//
//            @Override
//            public void onConnected(boolean reconnect, String serverUri) {
//                Log.i(TAG, "mqtt connected, reconnect=" + reconnect + ", server=" + serverUri);
//            }
//
//            @Override
//            public void onConnectFailure(Throwable throwable) {
//                Log.e(TAG, "mqtt connect fail", throwable);
//            }
//
//            @Override
//            public void onMessagePayload(String topic, String payload) {
//                Log.i(TAG, "topic=" + topic + ", payload=" + payload);
//            }
//
//            @Override
//            public void onError(MqttAction action, Throwable throwable) {
//                Log.e(TAG, action + " error", throwable);
//            }
//        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        MqttFactory.getInstance().release();
    }
}
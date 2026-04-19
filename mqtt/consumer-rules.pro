# Keep MQTT library public API
-keep public class com.dawn.mqtt.** { public *; }
-keep public interface com.dawn.mqtt.** { *; }
-keep public enum com.dawn.mqtt.** { *; }

# Eclipse Paho MQTT
-keep class org.eclipse.paho.client.mqttv3.** { *; }
-dontwarn org.eclipse.paho.client.mqttv3.**
-keep class org.eclipse.paho.android.service.** { *; }
-dontwarn org.eclipse.paho.android.service.**

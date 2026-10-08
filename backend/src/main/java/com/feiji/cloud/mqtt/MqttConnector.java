package com.feiji.cloud.mqtt;

import com.feiji.cloud.config.AppProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
@ConditionalOnProperty(prefix = "app.mqtt", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MqttConnector implements MqttCallbackExtended {

    private static final Logger log = LoggerFactory.getLogger(MqttConnector.class);
    private static final long RETRY_INTERVAL_MS = 5000;

    private final MqttClient mqttClient;
    private final AppProperties appProperties;
    private final MqttMessageDispatcher dispatcher;

    private volatile boolean stopped = false;
    private Thread connectThread;

    public MqttConnector(MqttClient mqttClient, AppProperties appProperties, MqttMessageDispatcher dispatcher) {
        this.mqttClient = mqttClient;
        this.appProperties = appProperties;
        this.dispatcher = dispatcher;
    }

    @PostConstruct
    public void start() {
        mqttClient.setCallback(this);
        connectThread = new Thread(this::connectWithRetry, "mqtt-connector");
        connectThread.setDaemon(true);
        connectThread.start();
    }

    private void connectWithRetry() {
        AppProperties.Mqtt mqtt = appProperties.mqtt();
        while (!stopped && !mqttClient.isConnected()) {
            try {
                MqttConnectOptions options = new MqttConnectOptions();
                options.setCleanSession(true);
                options.setAutomaticReconnect(true);
                options.setKeepAliveInterval(mqtt.keepAlive());
                options.setConnectionTimeout(10);
                if (!mqtt.username().isBlank()) {
                    options.setUserName(mqtt.username());
                    options.setPassword(mqtt.password().toCharArray());
                }
                log.info("连接 MQTT Broker: {}", mqtt.host());
                mqttClient.connect(options);
            } catch (MqttException e) {
                log.warn("MQTT 连接失败, {} 秒后重试: {}", RETRY_INTERVAL_MS / 1000, e.getMessage());
                sleepQuietly(RETRY_INTERVAL_MS);
            }
        }
    }

    @Override
    public void connectComplete(boolean reconnect, String serverURI) {
        String[] topics = appProperties.mqtt().subscribeTopics().toArray(String[]::new);
        int[] qos = Arrays.stream(topics).mapToInt(t -> 1).toArray();
        try {
            mqttClient.subscribe(topics, qos);
            log.info("MQTT 已连接({}), 订阅 Topics: {}", reconnect ? "重连" : "首次", Arrays.toString(topics));
        } catch (MqttException e) {
            log.error("MQTT 订阅失败", e);
        }
    }

    @Override
    public void connectionLost(Throwable cause) {
        log.warn("MQTT 连接断开: {}", cause == null ? "" : cause.getMessage());
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        dispatcher.dispatch(topic, message.getPayload());
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
    }

    @PreDestroy
    public void stop() {
        stopped = true;
        if (connectThread != null) {
            connectThread.interrupt();
        }
        try {
            if (mqttClient.isConnected()) {
                mqttClient.disconnect();
            }
        } catch (MqttException ignored) {
        }
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

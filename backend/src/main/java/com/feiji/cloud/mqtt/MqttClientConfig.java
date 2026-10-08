package com.feiji.cloud.mqtt;

import com.feiji.cloud.config.AppProperties;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "app.mqtt", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MqttClientConfig {

    @Bean(destroyMethod = "close")
    public MqttClient mqttClient(AppProperties properties) throws Exception {
        AppProperties.Mqtt mqtt = properties.mqtt();
        return new MqttClient(mqtt.host(), mqtt.clientId(), new MemoryPersistence());
    }
}

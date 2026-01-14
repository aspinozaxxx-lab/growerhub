package ru.growerhub.backend.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Conditional;
import ru.growerhub.backend.common.config.mqtt.MqttTopicSettings;

@Configuration
public class MqttConfig {

    @Bean
    Clock systemClock() {
        return Clock.systemUTC();
    }

    @Bean
    @Conditional(MqttEnabledCondition.class)
    @ConditionalOnMissingBean(MqttPublisher.class)
    MqttPublisher mqttPublisher(MqttSettings settings, DebugSettings debugSettings, ObjectMapper objectMapper) {
        return new PahoMqttPublisher(settings, debugSettings, objectMapper);
    }

    @Bean
    @Conditional(MqttEnabledCondition.class)
    @ConditionalOnMissingBean(MqttSubscriber.class)
    MqttSubscriber mqttSubscriber(
            MqttSettings settings,
            DebugSettings debugSettings,
            MqttTopicSettings topicSettings,
            MqttMessageHandler handler
    ) {
        return new PahoMqttSubscriber(settings, debugSettings, topicSettings, handler);
    }
}

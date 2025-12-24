package ru.growerhub.backend.mqtt;

import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

public class MqttEnabledCondition implements Condition, EnvironmentAware {
    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        Environment env = environment != null ? environment : context.getEnvironment();
        String rawHost = env.getProperty("MQTT_HOST");
        if (rawHost == null) {
            rawHost = env.getProperty("mqtt.host");
        }
        if (rawHost == null) {
            return true;
        }
        return StringUtils.hasText(rawHost);
    }
}

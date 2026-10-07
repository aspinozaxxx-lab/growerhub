package ru.growerhub.backend.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class TelegramSchedulerConfig {
    @Bean
    public ThreadPoolTaskScheduler telegramTaskScheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("telegram-");
        return scheduler;
    }
    @Bean
    public ThreadPoolTaskScheduler telegramUpdatesTaskScheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("telegram-updates-");
        return scheduler;
    }
}

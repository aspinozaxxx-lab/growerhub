package ru.growerhub.backend.common.config.advisor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
@Configuration
public class AssistantScheduler {
    @Bean public ThreadPoolTaskScheduler assistantTaskScheduler(AssistantSettings settings) {
        var scheduler = new ThreadPoolTaskScheduler(); scheduler.setPoolSize(settings.schedulerThreads());
        scheduler.setThreadNamePrefix("assistant-scheduler-"); return scheduler;
    }
}

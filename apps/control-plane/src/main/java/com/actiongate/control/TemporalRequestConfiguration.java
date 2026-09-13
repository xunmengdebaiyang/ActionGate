package com.actiongate.control;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class TemporalRequestConfiguration {
    @Bean(destroyMethod = "shutdown")
    ScheduledExecutorService temporalRequestDeadlines() {
        return Executors.newSingleThreadScheduledExecutor(
                Thread.ofPlatform().daemon().name("temporal-api-deadline").factory());
    }

    @Bean(destroyMethod = "shutdown")
    ExecutorService temporalRequestOperations() {
        return Executors.newCachedThreadPool(
                Thread.ofPlatform().daemon().name("temporal-api-operation", 0).factory());
    }
}

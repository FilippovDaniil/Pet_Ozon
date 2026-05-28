package com.example.marketplace.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Включает поддержку @Scheduled в Spring контексте.
 * Без этой аннотации @Scheduled-методы не запускаются.
 */
@Configuration
@EnableScheduling
public class SchedulerConfig {
}

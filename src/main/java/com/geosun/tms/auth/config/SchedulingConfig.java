package com.geosun.tms.auth.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Увімкнення планувальника для фонових задач (очищення токенів).
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(CleanupProperties.class)
public class SchedulingConfig {
}

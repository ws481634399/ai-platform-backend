package com.ai.mall.identity.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 调度开关（CHG-0016）：启用 outbox relay 的 @Scheduled 定时兜底扫描。
 */
@Configuration
@EnableScheduling
public class SchedulingConfiguration {
}

package com.ai.mall.search;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * mall-search 服务启动类。
 *
 * <p>CHG-0020：商品搜索（Elasticsearch 官方 Java Client 接入）。
 * <p>CHG-0021：索引生命周期与同步失败退避调度。
 * 注意：{@code @EnableScheduling} 为单实例前提；多实例需引入 ShedLock 等分片机制（M7 评估）。
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class MallSearchApplication {

    public static void main(String[] args) {
        SpringApplication.run(MallSearchApplication.class, args);
    }
}

package com.ai.mall.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * mall-order 服务启动类（CHG-0019 M4 订单交易闭环）。
 *
 * <p>{@link EnableScheduling} 承载补偿任务有界退避重试调度（单实例，M7 再升级分布式调度）。
 */
@EnableScheduling
@SpringBootApplication
public class MallOrderApplication {

    public static void main(String[] args) {
        SpringApplication.run(MallOrderApplication.class, args);
    }
}

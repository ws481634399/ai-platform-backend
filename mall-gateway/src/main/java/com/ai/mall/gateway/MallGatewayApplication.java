package com.ai.mall.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * mall-gateway 统一接入层启动类（M0 骨架，路由规则后续里程碑实现）。
 */
@SpringBootApplication
public class MallGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(MallGatewayApplication.class, args);
    }
}

package com.ai.mall.common.mq.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * RocketMQ 集成配置属性（Nacos 下发 rocketmq.* 前缀）。
 * <p>enabled=false 时全部 MQ Bean 不装配（服务正常启动，同步路径可用）。</p>
 */
@ConfigurationProperties(prefix = "rocketmq")
public class RocketMQProperties {

    /** 总开关：true 才装配 Producer/消费者容器 */
    private boolean enabled = false;

    /** NameServer 地址（relaxed binding 支持 rocketmq.name-server） */
    private String nameServer;

    /** 生产者组 */
    private String producerGroup = "mall-producer-group";

    /** 同步发送超时（ms） */
    private int sendMsgTimeout = 3000;

    /** 同步发送失败内部重试次数 */
    private int retryTimesWhenSendFailed = 2;

    /** 异步发送失败内部重试次数 */
    private int retryTimesWhenSendAsyncFailed = 2;

    /** 消费端最大重试次数，超过进 DLQ */
    private int maxReconsumeTimes = 16;

    /** 消费者线程数（本地资源友好，默认小值） */
    private int consumeThreadMin = 2;
    private int consumeThreadMax = 4;

    /** 幂等表名（consumed_event 归属消费方数据库） */
    private String idempotentTable = "consumed_event";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getNameServer() {
        return nameServer;
    }

    public void setNameServer(String nameServer) {
        this.nameServer = nameServer;
    }

    public String getProducerGroup() {
        return producerGroup;
    }

    public void setProducerGroup(String producerGroup) {
        this.producerGroup = producerGroup;
    }

    public int getSendMsgTimeout() {
        return sendMsgTimeout;
    }

    public void setSendMsgTimeout(int sendMsgTimeout) {
        this.sendMsgTimeout = sendMsgTimeout;
    }

    public int getRetryTimesWhenSendFailed() {
        return retryTimesWhenSendFailed;
    }

    public void setRetryTimesWhenSendFailed(int retryTimesWhenSendFailed) {
        this.retryTimesWhenSendFailed = retryTimesWhenSendFailed;
    }

    public int getRetryTimesWhenSendAsyncFailed() {
        return retryTimesWhenSendAsyncFailed;
    }

    public void setRetryTimesWhenSendAsyncFailed(int retryTimesWhenSendAsyncFailed) {
        this.retryTimesWhenSendAsyncFailed = retryTimesWhenSendAsyncFailed;
    }

    public int getMaxReconsumeTimes() {
        return maxReconsumeTimes;
    }

    public void setMaxReconsumeTimes(int maxReconsumeTimes) {
        this.maxReconsumeTimes = maxReconsumeTimes;
    }

    public int getConsumeThreadMin() {
        return consumeThreadMin;
    }

    public void setConsumeThreadMin(int consumeThreadMin) {
        this.consumeThreadMin = consumeThreadMin;
    }

    public int getConsumeThreadMax() {
        return consumeThreadMax;
    }

    public void setConsumeThreadMax(int consumeThreadMax) {
        this.consumeThreadMax = consumeThreadMax;
    }

    public String getIdempotentTable() {
        return idempotentTable;
    }

    public void setIdempotentTable(String idempotentTable) {
        this.idempotentTable = idempotentTable;
    }
}

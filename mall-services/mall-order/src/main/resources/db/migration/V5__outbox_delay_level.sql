-- CHG-0025 M7 STORY-009-04-01：Outbox 延迟消息级别列
-- 0=即时投递（既有订单四事件）；1~18=RocketMQ 延迟级别，业务事务 flush 时落定。
ALTER TABLE outbox_event ADD COLUMN delay_level INT NOT NULL DEFAULT 0 AFTER event_type;

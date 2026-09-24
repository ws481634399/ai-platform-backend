package com.ai.mall.order.application.order.event;

import com.ai.mall.event.Envelope;
import com.ai.mall.order.application.order.timeout.DelayLevelMapper;
import com.ai.mall.order.application.order.timeout.PaymentTimeoutPolicy;
import com.ai.mall.order.application.outbox.OutboxRecordWriter;
import com.ai.mall.order.domain.order.Order;
import com.ai.mall.order.domain.order.event.OrderIntegrationEvent;
import com.ai.mall.order.domain.order.event.OrderIntegrationEventType;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 订单事件 Outbox flush 实现。
 *
 * <p>异步模式：进入分支后只读取一次支付超时分钟数，逐事件组装 Envelope 并在当前事务
 * （MANDATORY）内 append 到 outbox_event；延迟检查事件携带映射后的延迟级别。
 * append 异常向上传播，业务事务整体回滚。同步模式：事件取出即丢弃，不产生 outbox 行。
 */
@Component
public class OutboxOrderEventFlusher implements OrderEventOutbox {

    private static final Logger log = LoggerFactory.getLogger(OutboxOrderEventFlusher.class);

    private final OrderEnvelopeAssembler assembler;
    private final OutboxRecordWriter recordWriter;
    private final IntegrationMode mode;
    private final PaymentTimeoutPolicy timeoutPolicy;
    private final DelayLevelMapper delayLevelMapper;

    public OutboxOrderEventFlusher(OrderEnvelopeAssembler assembler, OutboxRecordWriter recordWriter,
                                   IntegrationMode mode, PaymentTimeoutPolicy timeoutPolicy,
                                   DelayLevelMapper delayLevelMapper) {
        this.assembler = assembler;
        this.recordWriter = recordWriter;
        this.mode = mode;
        this.timeoutPolicy = timeoutPolicy;
        this.delayLevelMapper = delayLevelMapper;
    }

    @Override
    public void flush(Order order) {
        List<OrderIntegrationEvent> events = order.pullIntegrationEvents();
        if (events.isEmpty()) {
            return;
        }
        if (!mode.async()) {
            // 同步降级：事件丢弃（支付/取消改走同步 HTTP 路径，由各 Service 分支处理）
            log.debug("同步模式丢弃订单集成事件: orderId={}, count={}", order.getId(), events.size());
            return;
        }
        // 超时分钟数一次 flush 只读取一次：ORDER_CREATED.paymentDeadline 与
        // PAYMENT_TIMEOUT_CHECK.expireAt/延迟级别保证同源
        long timeoutMinutes = timeoutPolicy.timeoutMinutes();
        String aggregateId = String.valueOf(order.getId());
        for (OrderIntegrationEvent event : events) {
            Envelope envelope = assembler.assemble(order, event, timeoutMinutes);
            // MANDATORY：必须运行在仓储事务内，与业务数据原子提交
            if (event.type() == OrderIntegrationEventType.PAYMENT_TIMEOUT_CHECK) {
                int delayLevel = delayLevelMapper.toLevel(timeoutMinutes);
                recordWriter.append(aggregateId, envelope.getEventType(), envelope, delayLevel);
            } else {
                recordWriter.append(aggregateId, envelope.getEventType(), envelope);
            }
        }
    }
}

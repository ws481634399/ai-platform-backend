package com.ai.mall.order.application.order.event;

import com.ai.mall.event.Envelope;
import com.ai.mall.order.application.outbox.OutboxRecordWriter;
import com.ai.mall.order.domain.order.Order;
import com.ai.mall.order.domain.order.event.OrderIntegrationEvent;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 订单事件 Outbox flush 实现。
 *
 * <p>异步模式：逐事件组装 Envelope 并在当前事务（MANDATORY）内 append 到 outbox_event；
 * append 异常向上传播，业务事务整体回滚。同步模式：事件取出即丢弃，不产生 outbox 行。
 */
@Component
public class OutboxOrderEventFlusher implements OrderEventOutbox {

    private static final Logger log = LoggerFactory.getLogger(OutboxOrderEventFlusher.class);

    private final OrderEnvelopeAssembler assembler;
    private final OutboxRecordWriter recordWriter;
    private final IntegrationMode mode;

    public OutboxOrderEventFlusher(OrderEnvelopeAssembler assembler, OutboxRecordWriter recordWriter,
                                   IntegrationMode mode) {
        this.assembler = assembler;
        this.recordWriter = recordWriter;
        this.mode = mode;
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
        String aggregateId = String.valueOf(order.getId());
        for (OrderIntegrationEvent event : events) {
            Envelope envelope = assembler.assemble(order, event);
            // MANDATORY：必须运行在仓储事务内，与业务数据原子提交
            recordWriter.append(aggregateId, envelope.getEventType(), envelope);
        }
    }
}

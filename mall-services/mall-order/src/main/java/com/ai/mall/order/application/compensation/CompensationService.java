package com.ai.mall.order.application.compensation;

import com.ai.mall.common.core.trace.TraceConstants;
import com.ai.mall.common.web.exception.BusinessException;
import com.ai.mall.order.application.order.port.OrderCompensationPort;
import com.ai.mall.order.domain.compensation.CompensationRepository;
import com.ai.mall.order.domain.compensation.CompensationStatus;
import com.ai.mall.order.domain.compensation.CompensationTask;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import com.ai.mall.order.domain.order.OrderErrorCode;

/**
 * 补偿任务应用服务（CHG-0019 REQ-M4-004；CHG-0025 STORY-009-05-01 扩展）。
 *
 * <p>挂点登记（{@link OrderCompensationPort}）幂等落库；调度器每 30s 捞取到期任务，
 * 成功置 SUCCESS，失败按 30s/1m/2m/5m/10m 有界退避，5 次失败转 FAILED_DEAD 人工；
 * 管理端可手动复活重试/手动标记完成。release/confirm/cancel 本身幂等，并发/重复调度不产生重复数量变化。
 *
 * <p>CHG-0025：执行器列表化（库存两类 + 订单自动取消）；执行期间沿用任务登记的原 traceId（MDC）。
 */
@Service
public class CompensationService implements OrderCompensationPort {

    private static final Logger log = LoggerFactory.getLogger(CompensationService.class);
    private static final int DUE_LIMIT = 50;

    private final CompensationRepository repository;
    private final List<CompensationActionHandler> handlers;
    private final ObjectMapper objectMapper;

    public CompensationService(CompensationRepository repository, List<CompensationActionHandler> handlers,
                               ObjectMapper objectMapper) {
        this.repository = repository;
        this.handlers = handlers;
        this.objectMapper = objectMapper;
    }

    // ---------- 交易挂点登记 ----------

    @Override
    public void enqueueInventoryRelease(String orderNo, List<InventoryLine> lines, String reason) {
        enqueue(orderNo, CompensationTask.OP_RELEASE_INVENTORY, lines, reason);
    }

    @Override
    public void enqueueInventoryConfirm(String orderNo, List<InventoryLine> lines, String reason) {
        enqueue(orderNo, CompensationTask.OP_CONFIRM_INVENTORY, lines, reason);
    }

    private void enqueue(String orderNo, String operation, List<InventoryLine> lines, String reason) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        try {
            List<InventoryCompensationPayload.Line> payloadLines = lines.stream()
                    .map(line -> new InventoryCompensationPayload.Line(line.skuId(), line.quantity(),
                            line.reservationId()))
                    .toList();
            String payload = objectMapper.writeValueAsString(new InventoryCompensationPayload(payloadLines));
            // CHG-0023：登记时刻从 MDC 取请求 traceId（HTTP 线程由 TraceIdFilter 写入）；
            // 非 HTTP 防御路径 MDC 缺失归 null，不阻断补偿登记
            String traceId = currentMdcTraceId();
            CompensationTask task = CompensationTask.register(
                    CompensationTask.TYPE_ORDER, orderNo, operation, payload, traceId, Instant.now());
            boolean inserted = repository.insertIgnore(task);
            log.warn("登记库存补偿任务 orderNo={}, op={}, reason={}, inserted={}, traceId={}",
                    orderNo, operation, reason, inserted, traceId);
        } catch (Exception ex) {
            // 登记失败不能影响主交易响应；ERROR 暴露给监控，人工按日志核对库存预留
            log.error("登记库存补偿任务失败 orderNo={}, op={}, reason={}", orderNo, operation, reason, ex);
        }
    }

    /**
     * 登记订单自动取消补偿（CHG-0025 STORY-009-05-01，AC-035）。
     *
     * <p>由延迟回查消费者在 systemCancel 非 CONFLICT 失败时本地调用（不经 HTTP）；
     * traceId 沿用原事件（调用方负责 MDC 已有值，亦可显式传入）。
     *
     * @param orderId 订单主键
     * @param orderNo 订单号
     * @param eventId 原 PAYMENT_TIMEOUT_CHECK 事件 ID（payload 冗余）
     */
    public void enqueueOrderAutoCancel(long orderId, String orderNo, String eventId, String traceId) {
        try {
            String payload = objectMapper.writeValueAsString(
                    new OrderAutoCancelCompensationPayload(orderId, orderNo, eventId));
            String effectiveTraceId = (traceId == null || traceId.isBlank()) ? currentMdcTraceId() : traceId;
            CompensationTask task = CompensationTask.register(
                    CompensationTask.TYPE_ORDER, orderNo, CompensationTask.OP_AUTO_CANCEL_ORDER,
                    payload, effectiveTraceId, Instant.now());
            boolean inserted = repository.insertIgnore(task);
            log.warn("登记订单自动取消补偿 orderId={}, orderNo={}, eventId={}, inserted={}, traceId={}",
                    orderId, orderNo, eventId, inserted, effectiveTraceId);
        } catch (Exception ex) {
            // 登记失败不掩盖消费者原始异常；ERROR 暴露监控
            log.error("登记订单自动取消补偿失败 orderId={}, orderNo={}, eventId={}", orderId, orderNo, eventId, ex);
        }
    }

    // ---------- 调度执行 ----------

    /** 每 30s 扫描到期任务（fixedDelay：上一轮结束后再计时，避免单实例堆叠）。 */
    @Scheduled(fixedDelayString = "${mall.order.compensation-fixed-delay-ms:30000}")
    public void processDue() {
        List<CompensationTask> due;
        try {
            due = repository.findDue(DUE_LIMIT, Instant.now());
        } catch (Exception ex) {
            log.error("捞取到期补偿任务失败", ex);
            return;
        }
        for (CompensationTask task : due) {
            execute(task);
        }
    }

    /** 立即执行一次任务并落结果（成功 SUCCESS / 失败退避或 FAILED_DEAD）。 */
    public void execute(CompensationTask task) {
        Instant now = Instant.now();
        String traceId = task.traceId();
        // AC-039：补偿执行沿用原事件 traceId，下游日志/事件与原链路同源
        if (traceId != null) {
            MDC.put(TraceConstants.MDC_KEY, traceId);
        }
        try {
            CompensationActionHandler handler = handlers.stream()
                    .filter(candidate -> candidate.supports(task.operation()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("无匹配补偿执行器: " + task.operation()));
            handler.handle(task);
            task.markSuccess(now);
            log.info("补偿任务成功 id={}, orderNo={}, op={}", task.getId(), task.businessId(), task.operation());
        } catch (Exception ex) {
            boolean retryable = task.recordFailure(rootMessage(ex), now);
            log.warn("补偿任务失败 id={}, orderNo={}, op={}, retry={}, retryCount={}",
                    task.getId(), task.businessId(), task.operation(), retryable, task.retryCount(), ex);
        } finally {
            try {
                repository.update(task);
            } catch (Exception updateEx) {
                log.error("补偿任务结果落库失败 id={}", task.getId(), updateEx);
            }
            if (traceId != null) {
                MDC.remove(TraceConstants.MDC_KEY);
            }
        }
    }

    // ---------- 管理台 ----------

    public CompensationRepository.CompensationPage page(String status, int page, int size) {
        return repository.page(null, null, status, page, size);
    }

    /** 手动重试：复活 FAILED_DEAD/PENDING 任务并立即执行一次；SUCCESS 直接返回。 */
    public CompensationTask manualRetry(long id) {
        CompensationTask task = repository.findById(id)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.COMPENSATION_NOT_FOUND, HttpStatus.NOT_FOUND));
        if (task.status() == CompensationStatus.SUCCESS) {
            return task;
        }
        task.rearmForManualRetry(Instant.now());
        repository.update(task);
        execute(task);
        return repository.findById(id).orElse(task);
    }

    /** 手动标记完成（CHG-0025 STORY-009-05-01，AC-038）：人工确认闭环后直接置 SUCCESS。 */
    public CompensationTask manualComplete(long id) {
        CompensationTask task = repository.findById(id)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.COMPENSATION_NOT_FOUND, HttpStatus.NOT_FOUND));
        task.manualComplete(Instant.now());
        repository.update(task);
        return task;
    }

    // ---------- 辅助 ----------

    /** 取 MDC 当前 traceId，缺失/空白归 null。 */
    private static String currentMdcTraceId() {
        String rawTraceId = MDC.get(TraceConstants.MDC_KEY);
        return (rawTraceId == null || rawTraceId.isBlank()) ? null : rawTraceId;
    }

    private static String rootMessage(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        String message = cause.getMessage();
        return message == null ? cause.getClass().getSimpleName() : message;
    }
}

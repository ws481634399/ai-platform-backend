package com.ai.mall.order.domain.compensation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/** CompensationTask 聚合行为单测（manualComplete，CHG-0025 STORY-009-05-01）。 */
class CompensationTaskTest {

    @Test
    void manualComplete_待处理任务转成功并保留人工标记() {
        CompensationTask task = CompensationTask.register(
                CompensationTask.TYPE_ORDER, "ORD-1", CompensationTask.OP_AUTO_CANCEL_ORDER,
                "{}", "trace-1", Instant.parse("2026-05-25T00:00:00Z"));
        Instant now = Instant.parse("2026-05-25T01:00:00Z");

        task.manualComplete(now);

        assertThat(task.status()).isEqualTo(CompensationStatus.SUCCESS);
        assertThat(task.nextRetryAt()).isNull();
        assertThat(task.updatedAt()).isEqualTo(now);
        assertThat(task.lastError()).contains("MANUAL_COMPLETE");
    }

    @Test
    void manualComplete_失败任务转成功且不抹原始错误() {
        CompensationTask task = CompensationTask.reconstitute(
                9L, CompensationTask.TYPE_ORDER, "ORD-2", CompensationTask.OP_RELEASE_INVENTORY,
                "{}", CompensationStatus.FAILED_DEAD, 5, CompensationTask.MAX_RETRIES,
                "boom", null, "trace-2",
                Instant.parse("2026-05-25T00:00:00Z"), Instant.parse("2026-05-25T00:30:00Z"));

        task.manualComplete(Instant.parse("2026-05-25T02:00:00Z"));

        assertThat(task.status()).isEqualTo(CompensationStatus.SUCCESS);
        assertThat(task.lastError()).contains("boom").contains("MANUAL_COMPLETE");
    }
}

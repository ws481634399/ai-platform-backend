package com.ai.mall.search.domain.index;

import java.time.Instant;

/**
 * 全量重建任务聚合（CHG-0021）。
 *
 * <p>任务创建即 RUNNING（同步执行，M5 单实例）；成功 SUCCESS、异常 FAILED，
 * FAILED 保留临时物理索引与错误信息，允许再次触发。
 */
public class IndexRebuildTask {

    private Long id;
    private String taskNo;
    private RebuildStatus status;
    private int totalCount;
    private int indexedCount;
    private int failedCount;
    private String physicalIndex;
    private String errorMessage;
    private Instant startedAt;
    private Instant finishedAt;
    private Instant createdAt;
    private Instant updatedAt;

    public static IndexRebuildTask start(String taskNo, String physicalIndex, Instant now) {
        IndexRebuildTask task = new IndexRebuildTask();
        task.taskNo = taskNo;
        task.physicalIndex = physicalIndex;
        task.status = RebuildStatus.RUNNING;
        task.startedAt = now;
        task.createdAt = now;
        task.updatedAt = now;
        return task;
    }

    public void reportProgress(int totalCount, int indexedCount) {
        this.totalCount = totalCount;
        this.indexedCount = indexedCount;
    }

    public void markSuccess(int totalCount, int indexedCount, Instant now) {
        this.status = RebuildStatus.SUCCESS;
        this.totalCount = totalCount;
        this.indexedCount = indexedCount;
        this.finishedAt = now;
        this.updatedAt = now;
    }

    public void markFailed(String errorMessage, Instant now) {
        this.status = RebuildStatus.FAILED;
        this.errorMessage = errorMessage == null ? null
                : errorMessage.length() > 1000 ? errorMessage.substring(0, 1000) : errorMessage;
        this.finishedAt = now;
        this.updatedAt = now;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTaskNo() { return taskNo; }
    public void setTaskNo(String taskNo) { this.taskNo = taskNo; }
    public RebuildStatus getStatus() { return status; }
    public void setStatus(RebuildStatus status) { this.status = status; }
    public int getTotalCount() { return totalCount; }
    public void setTotalCount(int totalCount) { this.totalCount = totalCount; }
    public int getIndexedCount() { return indexedCount; }
    public void setIndexedCount(int indexedCount) { this.indexedCount = indexedCount; }
    public int getFailedCount() { return failedCount; }
    public void setFailedCount(int failedCount) { this.failedCount = failedCount; }
    public String getPhysicalIndex() { return physicalIndex; }
    public void setPhysicalIndex(String physicalIndex) { this.physicalIndex = physicalIndex; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

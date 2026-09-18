package com.ai.mall.search.application.index;

import com.ai.mall.common.web.exception.BusinessException;
import com.ai.mall.search.domain.index.IndexErrorCode;
import com.ai.mall.search.domain.index.IndexRebuildTask;
import com.ai.mall.search.domain.index.RebuildTaskRepository;
import com.ai.mall.search.domain.index.SearchIndexPort;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * 重建编排（CHG-0021 DU-BE-504）：并发闸门 409 → 临时索引全量构建 → 原子别名切换 → 清旧索引。
 * M5 同步执行（单实例），FAILED 保留临时索引与错误信息，允许再次触发。
 */
@Service
public class RebuildService {

    private static final Logger log = LoggerFactory.getLogger(RebuildService.class);
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final RebuildTaskRepository taskRepository;
    private final SearchIndexPort searchIndexPort;
    private final FullIndexBuildService fullIndexBuildService;
    private final String alias;

    public RebuildService(RebuildTaskRepository taskRepository,
                          SearchIndexPort searchIndexPort,
                          FullIndexBuildService fullIndexBuildService,
                          @Value("${mall.search.index-alias:mall_products}") String alias) {
        this.taskRepository = taskRepository;
        this.searchIndexPort = searchIndexPort;
        this.fullIndexBuildService = fullIndexBuildService;
        this.alias = alias;
    }

    public IndexRebuildTask startRebuild() {
        taskRepository.findRunning().ifPresent(running -> {
            throw new BusinessException(IndexErrorCode.SEARCH_REBUILD_RUNNING, HttpStatus.CONFLICT);
        });
        String timestamp = LocalDateTime.now(ZoneOffset.UTC).format(TS);
        String physicalIndex = "mall_products_rebuild_" + timestamp;
        String taskNo = "RBL" + timestamp;
        IndexRebuildTask task = IndexRebuildTask.start(taskNo, physicalIndex, Instant.now());
        taskRepository.insert(task);

        Set<String> oldIndices = Set.of();
        try {
            searchIndexPort.createProductIndex(physicalIndex);
            int indexed = fullIndexBuildService.build(physicalIndex, (total, done) -> {
                task.reportProgress(total, done);
                taskRepository.update(task);
            });
            // 切换前快照旧物理索引；原子 actions 摘除旧索引、挂接新索引，查询不中断
            oldIndices = searchIndexPort.physicalIndicesOf(alias);
            searchIndexPort.switchAlias(alias, oldIndices, physicalIndex);
            if (!oldIndices.isEmpty()) {
                searchIndexPort.deleteIndices(oldIndices);
            }
            task.markSuccess(task.getTotalCount(), indexed, Instant.now());
            log.info("索引重建成功 taskNo={} total={} indexed={} removed={}",
                    taskNo, task.getTotalCount(), indexed, oldIndices);
        } catch (Exception ex) {
            task.markFailed(rootMessage(ex), Instant.now());
            log.error("索引重建失败 taskNo={} 临时索引保留 {}，旧索引 {} 仍在服务",
                    taskNo, physicalIndex, oldIndices, ex);
        }
        taskRepository.update(task);
        return task;
    }

    public IndexRebuildTask getTask(long id) {
        return taskRepository.findById(id)
                .orElseThrow(() -> new BusinessException(IndexErrorCode.SYNC_FAILURE_NOT_FOUND, HttpStatus.NOT_FOUND));
    }

    public List<IndexRebuildTask> recent(int limit) {
        return taskRepository.recent(limit);
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

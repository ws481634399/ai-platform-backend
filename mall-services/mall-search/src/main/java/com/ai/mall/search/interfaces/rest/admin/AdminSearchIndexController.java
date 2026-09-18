package com.ai.mall.search.interfaces.rest.admin;

import com.ai.mall.common.core.result.UnifyResult;
import com.ai.mall.search.application.index.ConsistencyCheckService;
import com.ai.mall.search.application.index.RebuildService;
import com.ai.mall.search.application.index.SearchSyncFailureService;
import com.ai.mall.search.domain.index.IndexRebuildTask;
import com.ai.mall.search.domain.index.SearchSyncFailure;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 搜索索引管理端（CHG-0021）：重建触发/查询、一致性检查、失败记录与人工重试。
 * 权限码：search:index:list / search:index:rebuild（identity V9 种子）。
 */
@RestController
@RequestMapping("/api/admin/search/index")
public class AdminSearchIndexController {

    private final RebuildService rebuildService;
    private final ConsistencyCheckService consistencyCheckService;
    private final SearchSyncFailureService failureService;

    public AdminSearchIndexController(RebuildService rebuildService,
                                      ConsistencyCheckService consistencyCheckService,
                                      SearchSyncFailureService failureService) {
        this.rebuildService = rebuildService;
        this.consistencyCheckService = consistencyCheckService;
        this.failureService = failureService;
    }

    /** 触发全量重建；已有 RUNNING 返回 409（B0503）。 */
    @PostMapping("/rebuild")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasAuthority('search:index:rebuild')")
    public UnifyResult<RebuildTaskView> rebuild() {
        IndexRebuildTask task = rebuildService.startRebuild();
        return UnifyResult.ok(RebuildTaskView.from(task));
    }

    @GetMapping("/rebuild/{taskId}")
    @PreAuthorize("hasAuthority('search:index:list')")
    public UnifyResult<RebuildTaskView> getRebuild(@PathVariable long taskId) {
        return UnifyResult.ok(RebuildTaskView.from(rebuildService.getTask(taskId)));
    }

    @GetMapping("/rebuild")
    @PreAuthorize("hasAuthority('search:index:list')")
    public UnifyResult<List<RebuildTaskView>> recentRebuilds(
            @RequestParam(defaultValue = "20") int limit) {
        return UnifyResult.ok(rebuildService.recent(limit).stream()
                .map(RebuildTaskView::from).toList());
    }

    @GetMapping("/consistency-check")
    @PreAuthorize("hasAuthority('search:index:list')")
    public UnifyResult<Map<String, Object>> consistencyCheck() {
        return UnifyResult.ok(consistencyCheckService.check());
    }

    @GetMapping("/sync-failures")
    @PreAuthorize("hasAuthority('search:index:list')")
    public UnifyResult<Map<String, Object>> syncFailures(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        int safeSize = Math.max(1, Math.min(size, 100));
        List<SyncFailureView> items = failureService.page(status, Math.max(1, page), safeSize)
                .stream().map(SyncFailureView::from).toList();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", failureService.countByStatus(status));
        data.put("page", Math.max(1, page));
        data.put("size", safeSize);
        data.put("items", items);
        return UnifyResult.ok(data);
    }

    @PostMapping("/sync-failures/{id}/retry")
    @PreAuthorize("hasAuthority('search:index:rebuild')")
    public UnifyResult<SyncFailureView> retryFailure(@PathVariable long id) {
        SearchSyncFailure failure = failureService.manualRetry(id);
        return UnifyResult.ok(SyncFailureView.from(failure));
    }
}

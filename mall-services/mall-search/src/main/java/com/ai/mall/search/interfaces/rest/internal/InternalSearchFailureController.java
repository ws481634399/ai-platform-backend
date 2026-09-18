package com.ai.mall.search.interfaces.rest.internal;

import com.ai.mall.common.core.result.UnifyResult;
import com.ai.mall.search.application.index.SearchSyncFailureService;
import com.ai.mall.search.interfaces.rest.admin.SyncFailureView;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 搜索索引失败记录内部只读端点（CHG-0021，requirement-design §2.4）。
 *
 * <p>GET /api/internal/search/sync-failures?status=：供 SERVICE 角色服务端排查，
 * 仅经 X-Internal-Token 安全链（ROLE_SERVICE，见 SearchSecurityConfiguration），无任何写操作。
 * 返回结构与管理端 GET /api/admin/search/index/sync-failures 同构（{total,page,size,items}）。
 */
@RestController
@RequestMapping("/api/internal/search")
public class InternalSearchFailureController {

    private final SearchSyncFailureService failureService;

    public InternalSearchFailureController(SearchSyncFailureService failureService) {
        this.failureService = failureService;
    }

    @GetMapping("/sync-failures")
    public UnifyResult<Map<String, Object>> syncFailures(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, Math.min(size, 100));
        List<SyncFailureView> items = failureService.page(status, safePage, safeSize)
                .stream().map(SyncFailureView::from).toList();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", failureService.countByStatus(status));
        data.put("page", safePage);
        data.put("size", safeSize);
        data.put("items", items);
        return UnifyResult.ok(data);
    }
}

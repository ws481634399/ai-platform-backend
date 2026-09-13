package com.ai.mall.inventory.interfaces.rest.admin;

import com.ai.mall.common.core.result.UnifyResult;
import com.ai.mall.inventory.application.inventory.InventoryApplicationService;
import com.ai.mall.inventory.application.inventory.InventoryCommands.AdjustCommand;
import com.ai.mall.inventory.application.inventory.InventoryCommands.BatchQuery;
import com.ai.mall.inventory.application.inventory.InventoryCommands.InitCommand;
import com.ai.mall.inventory.application.inventory.InventoryCommands.PageQuery;
import com.ai.mall.inventory.domain.inventory.Inventory;
import com.ai.mall.inventory.domain.inventory.InventoryRepository.InventoryPageResult;
import com.ai.mall.inventory.interfaces.rest.admin.dto.InventoryDtos;
import com.ai.mall.inventory.interfaces.rest.admin.dto.InventoryDtos.AdjustRequest;
import com.ai.mall.inventory.interfaces.rest.admin.dto.InventoryDtos.InitRequest;
import com.ai.mall.inventory.interfaces.rest.admin.dto.InventoryDtos.InventoryView;
import com.ai.mall.inventory.interfaces.rest.admin.dto.InventoryDtos.LogView;
import com.ai.mall.inventory.interfaces.rest.admin.dto.InventoryDtos.PageView;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 库存管理端接口：/api/admin/inventory。
 */
@RestController
@RequestMapping("/api/admin/inventory")
public class InventoryAdminController {

    private final InventoryApplicationService service;

    public InventoryAdminController(InventoryApplicationService service) {
        this.service = service;
    }

    @GetMapping("/stocks")
    @PreAuthorize("hasAuthority('inventory:stock:list')")
    public UnifyResult<PageView<InventoryView>> page(
            @RequestParam(required = false) Long skuId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        InventoryPageResult result = service.page(new PageQuery(page, size, skuId));
        List<InventoryView> records = result.records().stream().map(InventoryView::from).toList();
        return UnifyResult.ok(new PageView<>(records, result.total(), result.page(), result.size()));
    }

    @GetMapping("/stocks/{skuId}")
    @PreAuthorize("hasAuthority('inventory:stock:detail')")
    public UnifyResult<InventoryView> get(@PathVariable long skuId) {
        return UnifyResult.ok(InventoryView.from(service.getBySkuId(skuId)));
    }

    @PostMapping("/stocks/batch")
    @PreAuthorize("hasAuthority('inventory:stock:list')")
    public UnifyResult<List<InventoryView>> batch(@RequestBody List<Long> skuIds) {
        List<InventoryView> records = service.batchGet(new BatchQuery(skuIds)).stream()
                .map(InventoryView::from).toList();
        return UnifyResult.ok(records);
    }

    @PostMapping("/stocks/init")
    @PreAuthorize("hasAuthority('inventory:stock:init')")
    public UnifyResult<InventoryView> init(@RequestBody InitRequest request) {
        Inventory inventory = service.init(new InitCommand(request.skuId(), request.totalQuantity()));
        return UnifyResult.ok(InventoryView.from(inventory));
    }

    @PostMapping("/stocks/{skuId}/adjust")
    @PreAuthorize("hasAuthority('inventory:stock:adjust')")
    public UnifyResult<InventoryView> adjust(@PathVariable long skuId, @RequestBody AdjustRequest request) {
        Inventory inventory = service.adjust(skuId,
                new AdjustCommand(request.delta(), request.reason(), request.businessId()));
        return UnifyResult.ok(InventoryView.from(inventory));
    }

    @GetMapping("/logs")
    @PreAuthorize("hasAuthority('inventory:log:list')")
    public UnifyResult<PageView<LogView>> logs(
            @RequestParam(required = false) Long skuId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        var logs = service.logs(new PageQuery(page, size, skuId));
        List<LogView> records = logs.stream().map(LogView::from).toList();
        return UnifyResult.ok(new PageView<>(records, records.size(), page == null ? 1 : page, size == null ? 20 : size));
    }
}

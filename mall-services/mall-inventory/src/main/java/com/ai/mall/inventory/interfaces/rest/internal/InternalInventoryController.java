package com.ai.mall.inventory.interfaces.rest.internal;

import com.ai.mall.common.core.result.UnifyResult;
import com.ai.mall.common.web.exception.BusinessException;
import com.ai.mall.inventory.application.inventory.InventoryApplicationService;
import com.ai.mall.inventory.application.inventory.InventoryCommands.ConfirmCommand;
import com.ai.mall.inventory.application.inventory.InventoryCommands.LockCommand;
import com.ai.mall.inventory.application.inventory.InventoryCommands.ReleaseCommand;
import com.ai.mall.inventory.domain.inventory.InventoryErrorCode;
import com.ai.mall.inventory.interfaces.rest.internal.dto.InternalInventoryDtos.AvailabilityRequest;
import com.ai.mall.inventory.interfaces.rest.internal.dto.InternalInventoryDtos.ConfirmRequest;
import com.ai.mall.inventory.interfaces.rest.internal.dto.InternalInventoryDtos.LockRequest;
import com.ai.mall.inventory.interfaces.rest.internal.dto.InternalInventoryDtos.ReleaseRequest;
import com.ai.mall.inventory.interfaces.rest.internal.dto.InternalInventoryDtos.ReservationView;
import com.ai.mall.inventory.interfaces.rest.internal.dto.InternalInventoryDtos.SkuAvailabilityView;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 库存内部接口：供 mall-order 调用锁定/释放/确认扣减。
 */
@RestController
@RequestMapping("/api/internal/inventory")
public class InternalInventoryController {

    private final InventoryApplicationService service;

    public InternalInventoryController(InventoryApplicationService service) {
        this.service = service;
    }

    @PostMapping("/lock")
    public UnifyResult<ReservationView> lock(@RequestBody LockRequest request) {
        return UnifyResult.ok(ReservationView.from(
                service.lock(new LockCommand(request.reservationId(), request.skuId(), request.quantity()))));
    }

    @PostMapping("/release")
    public UnifyResult<ReservationView> release(@RequestBody ReleaseRequest request) {
        return UnifyResult.ok(ReservationView.from(
                service.release(new ReleaseCommand(request.reservationId()))));
    }

    @PostMapping("/confirm")
    public UnifyResult<ReservationView> confirm(@RequestBody ConfirmRequest request) {
        return UnifyResult.ok(ReservationView.from(
                service.confirmDeduction(new ConfirmCommand(request.reservationId()))));
    }

    /**
     * 批量可售数量查询（内部）：一次 IN 批量 SQL；无记录=0；skuIds 非空且 ≤100、非负。
     * 响应精确数量，仅集群内可达（X-Internal-Token + /api/internal/** 网关 denyAll）。
     */
    @PostMapping("/availability")
    public UnifyResult<List<SkuAvailabilityView>> availability(@RequestBody AvailabilityRequest request) {
        List<Long> skuIds = request.skuIds();
        if (skuIds == null || skuIds.isEmpty()) {
            throw new BusinessException(InventoryErrorCode.AVAILABILITY_BATCH_INVALID, "skuIds 不能为空");
        }
        if (skuIds.size() > 100) {
            throw new BusinessException(InventoryErrorCode.AVAILABILITY_BATCH_INVALID, "skuIds 数量不能超过 100");
        }
        for (Long id : skuIds) {
            if (id == null || id <= 0) {
                throw new BusinessException(InventoryErrorCode.AVAILABILITY_BATCH_INVALID, "skuId 必须为正整数");
            }
        }
        Map<Long, Long> qtyMap = service.availabilityMap(skuIds);
        List<SkuAvailabilityView> result = new ArrayList<>(skuIds.size());
        for (Long id : skuIds) {
            result.add(new SkuAvailabilityView(id, qtyMap.getOrDefault(id, 0L)));
        }
        return UnifyResult.ok(result);
    }
}

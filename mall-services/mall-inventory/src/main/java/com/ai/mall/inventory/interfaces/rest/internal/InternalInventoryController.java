package com.ai.mall.inventory.interfaces.rest.internal;

import com.ai.mall.common.core.result.UnifyResult;
import com.ai.mall.inventory.application.inventory.InventoryApplicationService;
import com.ai.mall.inventory.application.inventory.InventoryCommands.ConfirmCommand;
import com.ai.mall.inventory.application.inventory.InventoryCommands.LockCommand;
import com.ai.mall.inventory.application.inventory.InventoryCommands.ReleaseCommand;
import com.ai.mall.inventory.interfaces.rest.internal.dto.InternalInventoryDtos.ConfirmRequest;
import com.ai.mall.inventory.interfaces.rest.internal.dto.InternalInventoryDtos.LockRequest;
import com.ai.mall.inventory.interfaces.rest.internal.dto.InternalInventoryDtos.ReleaseRequest;
import com.ai.mall.inventory.interfaces.rest.internal.dto.InternalInventoryDtos.ReservationView;
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
}

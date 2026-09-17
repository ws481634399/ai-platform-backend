package com.ai.mall.inventory.application.inventory;

import com.ai.mall.common.core.trace.TraceContext;
import com.ai.mall.inventory.application.inventory.InventoryCommands.AdjustCommand;
import com.ai.mall.inventory.application.inventory.InventoryCommands.BatchQuery;
import com.ai.mall.inventory.application.inventory.InventoryCommands.ConfirmCommand;
import com.ai.mall.inventory.application.inventory.InventoryCommands.InitCommand;
import com.ai.mall.inventory.application.inventory.InventoryCommands.LockCommand;
import com.ai.mall.inventory.application.inventory.InventoryCommands.PageQuery;
import com.ai.mall.inventory.application.inventory.InventoryCommands.ReleaseCommand;
import com.ai.mall.inventory.domain.inventory.Inventory;
import com.ai.mall.inventory.domain.inventory.InventoryErrorCode;
import com.ai.mall.inventory.domain.inventory.InventoryException;
import com.ai.mall.inventory.domain.inventory.InventoryLog;
import com.ai.mall.inventory.domain.inventory.InventoryOperationType;
import com.ai.mall.inventory.domain.inventory.InventoryRepository;
import com.ai.mall.inventory.domain.inventory.InventoryRepository.InventoryLogQuery;
import com.ai.mall.inventory.domain.inventory.InventoryRepository.InventoryPageQuery;
import com.ai.mall.inventory.domain.inventory.InventoryRepository.InventoryPageResult;
import com.ai.mall.inventory.domain.inventory.InventoryReservation;
import com.ai.mall.inventory.domain.inventory.ReservationStatus;
import com.ai.mall.inventory.infrastructure.client.SkuClient;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 库存应用服务：初始化/查询/调整/锁定/释放/确认扣减编排。
 */
@Service
public class InventoryApplicationService {

    private final InventoryRepository inventoryRepository;
    private final SkuClient skuClient;

    public InventoryApplicationService(InventoryRepository inventoryRepository, SkuClient skuClient) {
        this.inventoryRepository = inventoryRepository;
        this.skuClient = skuClient;
    }

    @Transactional
    public Inventory init(InitCommand command) {
        if (!skuClient.exists(command.skuId())) {
            throw InventoryException.skuNotFound(command.skuId());
        }
        if (inventoryRepository.findBySkuId(command.skuId()).isPresent()) {
            throw InventoryException.alreadyExists(command.skuId());
        }
        Inventory inventory = Inventory.initialize(command.skuId(), command.totalQuantity());
        inventoryRepository.insert(inventory);
        inventoryRepository.insertLog(new InventoryLog(
                command.skuId(), InventoryOperationType.INIT, command.totalQuantity(),
                0L, command.totalQuantity(), null, currentOperator(), traceId(), Instant.now()));
        return inventory;
    }

    @Transactional(readOnly = true)
    public Inventory getBySkuId(long skuId) {
        return inventoryRepository.findBySkuId(skuId)
                .orElseThrow(() -> InventoryException.notFound(skuId));
    }

    @Transactional(readOnly = true)
    public List<Inventory> batchGet(BatchQuery query) {
        return inventoryRepository.findBySkuIds(query.skuIds());
    }

    /**
     * 批量可售数量查询：一次 IN 批量 SQL；无库存记录的 skuId 语义为 available=0。
     * 返回 skuId → availableQty（total - locked）映射，调用方按需补零与排序。
     */
    @Transactional(readOnly = true)
    public java.util.Map<Long, Long> availabilityMap(java.util.List<Long> skuIds) {
        if (skuIds == null || skuIds.isEmpty()) {
            return java.util.Map.of();
        }
        java.util.Map<Long, Long> map = new java.util.HashMap<>();
        for (Inventory inv : inventoryRepository.findBySkuIds(skuIds)) {
            map.put(inv.getSkuId(), inv.getAvailableQuantity());
        }
        return map;
    }

    @Transactional(readOnly = true)
    public InventoryPageResult page(PageQuery query) {
        return inventoryRepository.page(new InventoryPageQuery(query.page(), query.size(), query.skuId()));
    }

    @Transactional
    public Inventory adjust(long skuId, AdjustCommand command) {
        Inventory inventory = inventoryRepository.findBySkuId(skuId)
                .orElseThrow(() -> InventoryException.notFound(skuId));
        long before = inventory.getTotalQuantity();
        inventory.adjust(command.delta());
        inventoryRepository.update(inventory);
        inventoryRepository.insertLog(new InventoryLog(
                skuId, InventoryOperationType.ADJUST, command.delta(),
                before, inventory.getTotalQuantity(), command.businessId(),
                currentOperator(), traceId(), Instant.now()));
        return inventory;
    }

    @Transactional
    public InventoryReservation lock(LockCommand command) {
        var existing = inventoryRepository.findReservationByReservationId(command.reservationId());
        if (existing.isPresent()) {
            return existing.get();
        }
        Inventory inventory = inventoryRepository.findBySkuId(command.skuId())
                .orElseThrow(() -> InventoryException.notFound(command.skuId()));
        int rows = inventoryRepository.lockStock(command.skuId(), command.quantity());
        if (rows == 0) {
            throw InventoryException.insufficient(command.skuId());
        }
        inventory.lock(command.quantity());
        InventoryReservation reservation = new InventoryReservation(
                command.reservationId(), command.skuId(), command.quantity());
        inventoryRepository.saveReservation(reservation);
        inventoryRepository.insertLog(new InventoryLog(
                command.skuId(), InventoryOperationType.LOCK, command.quantity(),
                inventory.getLockedQuantity() - command.quantity(), inventory.getLockedQuantity(),
                command.reservationId(), null, traceId(), Instant.now()));
        return reservation;
    }

    /**
     * CHG-0019 释放预留（CAS 加固，契约不变）。
     *
     * <p>同事务两步条件更新：① 预留记录 LOCKED→RELEASED 状态 CAS 先行，串行化同一预留的
     * 并发 release/confirm（落败方不会触碰库存数量）；② inventory_stock 的 locked 条件扣减
     * （{@code locked >= qty}），账实不一致时抛错回滚整事务。终态 RELEASED 重复调用幂等返回。
     */
    @Transactional
    public InventoryReservation release(ReleaseCommand command) {
        InventoryReservation reservation = inventoryRepository
                .findReservationByReservationId(command.reservationId())
                .orElseThrow(() -> InventoryException.reservationNotFound(command.reservationId()));
        if (reservation.getStatus() == ReservationStatus.RELEASED) {
            return reservation;
        }
        if (reservation.getStatus() != ReservationStatus.LOCKED) {
            throw InventoryException.reservationInvalidState(command.reservationId(), reservation.getStatus());
        }
        int stateRows = inventoryRepository.casReservationStatus(
                reservation.getId(), ReservationStatus.LOCKED, ReservationStatus.RELEASED);
        if (stateRows == 0) {
            // 并发竞争：重读仲裁，重复释放幂等，其余冲突报错
            InventoryReservation reloaded = inventoryRepository
                    .findReservationByReservationId(command.reservationId())
                    .orElseThrow(() -> InventoryException.reservationNotFound(command.reservationId()));
            if (reloaded.getStatus() == ReservationStatus.RELEASED) {
                return reloaded;
            }
            throw InventoryException.reservationInvalidState(command.reservationId(), reloaded.getStatus());
        }
        int stockRows = inventoryRepository.releaseStock(reservation.getSkuId(), reservation.getQuantity());
        if (stockRows == 0) {
            // locked 账面不足预留量（账实异常）：回滚预留状态迁移，交补偿/人工处理
            throw new InventoryException(InventoryErrorCode.RESERVATION_INVALID_STATE,
                    org.springframework.http.HttpStatus.CONFLICT,
                    "库存锁定量与预留不一致: reservationId=" + command.reservationId());
        }
        Inventory inventory = inventoryRepository.findBySkuId(reservation.getSkuId())
                .orElseThrow(() -> InventoryException.notFound(reservation.getSkuId()));
        inventoryRepository.insertLog(new InventoryLog(
                reservation.getSkuId(), InventoryOperationType.RELEASE, reservation.getQuantity(),
                inventory.getLockedQuantity() + reservation.getQuantity(), inventory.getLockedQuantity(),
                command.reservationId(), null, traceId(), Instant.now()));
        // 库内状态已由 CAS 更新；内存对象同步为终态用于响应
        reservation.release();
        return reservation;
    }

    /**
     * CHG-0019 确认扣减（CAS 加固，契约不变）：预留 LOCKED→DEDUCTED 状态 CAS 先行，
     * 随后 total/locked 条件同减；终态 DEDUCTED 重复调用幂等返回。
     */
    @Transactional
    public InventoryReservation confirmDeduction(ConfirmCommand command) {
        InventoryReservation reservation = inventoryRepository
                .findReservationByReservationId(command.reservationId())
                .orElseThrow(() -> InventoryException.reservationNotFound(command.reservationId()));
        if (reservation.getStatus() == ReservationStatus.DEDUCTED) {
            return reservation;
        }
        if (reservation.getStatus() != ReservationStatus.LOCKED) {
            throw InventoryException.reservationInvalidState(command.reservationId(), reservation.getStatus());
        }
        int stateRows = inventoryRepository.casReservationStatus(
                reservation.getId(), ReservationStatus.LOCKED, ReservationStatus.DEDUCTED);
        if (stateRows == 0) {
            InventoryReservation reloaded = inventoryRepository
                    .findReservationByReservationId(command.reservationId())
                    .orElseThrow(() -> InventoryException.reservationNotFound(command.reservationId()));
            if (reloaded.getStatus() == ReservationStatus.DEDUCTED) {
                return reloaded;
            }
            throw InventoryException.reservationInvalidState(command.reservationId(), reloaded.getStatus());
        }
        int stockRows = inventoryRepository.deductStock(reservation.getSkuId(), reservation.getQuantity());
        if (stockRows == 0) {
            throw new InventoryException(InventoryErrorCode.RESERVATION_INVALID_STATE,
                    org.springframework.http.HttpStatus.CONFLICT,
                    "库存锁定量与预留不一致: reservationId=" + command.reservationId());
        }
        Inventory inventory = inventoryRepository.findBySkuId(reservation.getSkuId())
                .orElseThrow(() -> InventoryException.notFound(reservation.getSkuId()));
        inventoryRepository.insertLog(new InventoryLog(
                reservation.getSkuId(), InventoryOperationType.DEDUCT, reservation.getQuantity(),
                inventory.getTotalQuantity() + reservation.getQuantity(), inventory.getTotalQuantity(),
                command.reservationId(), null, traceId(), Instant.now()));
        reservation.confirmDeduction();
        return reservation;
    }

    @Transactional(readOnly = true)
    public List<InventoryLog> logs(PageQuery query) {
        return inventoryRepository.findLogs(new InventoryLogQuery(query.page(), query.size(), query.skuId()));
    }

    private Long currentOperator() {
        return null;
    }

    private String traceId() {
        return TraceContext.get();
    }
}

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

    @Transactional
    public InventoryReservation release(ReleaseCommand command) {
        InventoryReservation reservation = inventoryRepository
                .findReservationByReservationId(command.reservationId())
                .orElseThrow(() -> InventoryException.reservationNotFound(command.reservationId()));
        if (reservation.getStatus() == ReservationStatus.RELEASED) {
            return reservation;
        }
        Inventory inventory = inventoryRepository.findBySkuId(reservation.getSkuId())
                .orElseThrow(() -> InventoryException.notFound(reservation.getSkuId()));
        long before = inventory.getLockedQuantity();
        inventory.release(reservation.getQuantity());
        inventoryRepository.update(inventory);
        reservation.release();
        inventoryRepository.saveReservation(reservation);
        inventoryRepository.insertLog(new InventoryLog(
                reservation.getSkuId(), InventoryOperationType.RELEASE, reservation.getQuantity(),
                before, inventory.getLockedQuantity(),
                command.reservationId(), null, traceId(), Instant.now()));
        return reservation;
    }

    @Transactional
    public InventoryReservation confirmDeduction(ConfirmCommand command) {
        InventoryReservation reservation = inventoryRepository
                .findReservationByReservationId(command.reservationId())
                .orElseThrow(() -> InventoryException.reservationNotFound(command.reservationId()));
        if (reservation.getStatus() == ReservationStatus.DEDUCTED) {
            return reservation;
        }
        Inventory inventory = inventoryRepository.findBySkuId(reservation.getSkuId())
                .orElseThrow(() -> InventoryException.notFound(reservation.getSkuId()));
        long beforeTotal = inventory.getTotalQuantity();
        long beforeLocked = inventory.getLockedQuantity();
        inventory.confirmDeduction(reservation.getQuantity());
        inventoryRepository.update(inventory);
        reservation.confirmDeduction();
        inventoryRepository.saveReservation(reservation);
        inventoryRepository.insertLog(new InventoryLog(
                reservation.getSkuId(), InventoryOperationType.DEDUCT, reservation.getQuantity(),
                beforeTotal, inventory.getTotalQuantity(),
                command.reservationId(), null, traceId(), Instant.now()));
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

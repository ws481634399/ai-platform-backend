package com.ai.mall.inventory.infrastructure.persistence.inventory;

import com.ai.mall.inventory.domain.inventory.Inventory;
import com.ai.mall.inventory.domain.inventory.InventoryLog;
import com.ai.mall.inventory.domain.inventory.InventoryOperationType;
import com.ai.mall.inventory.domain.inventory.InventoryRepository;
import com.ai.mall.inventory.domain.inventory.InventoryReservation;
import com.ai.mall.inventory.domain.inventory.ReservationStatus;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class InventoryRepositoryImpl implements InventoryRepository {

    private final InventoryMapper inventoryMapper;
    private final InventoryLogMapper logMapper;
    private final InventoryReservationMapper reservationMapper;

    public InventoryRepositoryImpl(InventoryMapper inventoryMapper, InventoryLogMapper logMapper,
                                   InventoryReservationMapper reservationMapper) {
        this.inventoryMapper = inventoryMapper;
        this.logMapper = logMapper;
        this.reservationMapper = reservationMapper;
    }

    @Override
    public Optional<Inventory> findBySkuId(long skuId) {
        InventoryPo po = inventoryMapper.selectOne(new LambdaQueryWrapper<InventoryPo>()
                .eq(InventoryPo::getSkuId, skuId));
        return po == null ? Optional.empty() : Optional.of(toDomain(po));
    }

    @Override
    public List<Inventory> findBySkuIds(List<Long> skuIds) {
        if (skuIds == null || skuIds.isEmpty()) {
            return List.of();
        }
        return inventoryMapper.selectList(new LambdaQueryWrapper<InventoryPo>()
                        .in(InventoryPo::getSkuId, skuIds))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public InventoryPageResult page(InventoryPageQuery query) {
        int page = query.page() == null || query.page() < 1 ? 1 : query.page();
        int size = query.size() == null || query.size() < 1 ? 20 : Math.min(query.size(), 100);
        LambdaQueryWrapper<InventoryPo> wrapper = new LambdaQueryWrapper<InventoryPo>()
                .eq(query.skuId() != null, InventoryPo::getSkuId, query.skuId())
                .orderByDesc(InventoryPo::getCreatedAt);
        Page<InventoryPo> result = inventoryMapper.selectPage(new Page<>(page, size), wrapper);
        List<Inventory> records = result.getRecords().stream().map(this::toDomain).toList();
        return new InventoryPageResult(records, result.getTotal(), page, size);
    }

    @Override
    public void insert(Inventory inventory) {
        InventoryPo po = toPo(inventory);
        inventoryMapper.insert(po);
        inventory.assignCreated(po.getId(), po.getCreatedAt() != null ? po.getCreatedAt() : Instant.now());
    }

    @Override
    public boolean update(Inventory inventory) {
        inventory.touch(Instant.now());
        InventoryPo po = toPo(inventory);
        return inventoryMapper.updateById(po) == 1;
    }

    @Override
    public void insertLog(InventoryLog log) {
        InventoryLogPo po = new InventoryLogPo();
        po.setSkuId(log.getSkuId());
        po.setOperationType(log.getOperationType().name());
        po.setQuantity(log.getQuantity());
        po.setBeforeQuantity(log.getBeforeQuantity());
        po.setAfterQuantity(log.getAfterQuantity());
        po.setBusinessId(log.getBusinessId());
        po.setOperator(log.getOperator());
        po.setTraceId(log.getTraceId());
        po.setOccurredAt(log.getOccurredAt() != null ? log.getOccurredAt() : Instant.now());
        logMapper.insert(po);
        log.assignId(po.getId());
    }

    @Override
    public List<InventoryLog> findLogs(InventoryLogQuery query) {
        int page = query.page() == null || query.page() < 1 ? 1 : query.page();
        int size = query.size() == null || query.size() < 1 ? 20 : Math.min(query.size(), 100);
        LambdaQueryWrapper<InventoryLogPo> wrapper = new LambdaQueryWrapper<InventoryLogPo>()
                .eq(query.skuId() != null, InventoryLogPo::getSkuId, query.skuId())
                .orderByDesc(InventoryLogPo::getOccurredAt);
        Page<InventoryLogPo> result = logMapper.selectPage(new Page<>(page, size), wrapper);
        return result.getRecords().stream().map(this::logToDomain).toList();
    }

    @Override
    public Optional<InventoryReservation> findReservationByReservationId(String reservationId) {
        InventoryReservationPo po = reservationMapper.selectOne(new LambdaQueryWrapper<InventoryReservationPo>()
                .eq(InventoryReservationPo::getReservationId, reservationId));
        return po == null ? Optional.empty() : Optional.of(reservationToDomain(po));
    }

    @Override
    public void saveReservation(InventoryReservation reservation) {
        InventoryReservationPo po = reservationToPo(reservation);
        if (reservation.getId() == 0L) {
            reservationMapper.insert(po);
            reservation.assignId(po.getId());
            reservation.assignCreated(po.getCreatedAt() != null ? po.getCreatedAt() : Instant.now());
        } else {
            reservation.touch(Instant.now());
            po.setUpdatedAt(Instant.now());
            reservationMapper.updateById(po);
        }
    }

    @Override
    public int lockStock(long skuId, long quantity) {
        return inventoryMapper.lockStock(skuId, quantity);
    }

    private Inventory toDomain(InventoryPo po) {
        Instant created = po.getCreatedAt() == null ? Instant.EPOCH : po.getCreatedAt();
        Instant updated = po.getUpdatedAt() == null ? created : po.getUpdatedAt();
        return Inventory.reconstitute(po.getId(), po.getSkuId(),
                po.getTotalQuantity() == null ? 0L : po.getTotalQuantity(),
                po.getLockedQuantity() == null ? 0L : po.getLockedQuantity(),
                po.getVersion() == null ? 0L : po.getVersion(),
                created, updated);
    }

    private InventoryPo toPo(Inventory inventory) {
        Instant now = Instant.now();
        InventoryPo po = new InventoryPo();
        po.setId(inventory.getId() == 0L ? null : inventory.getId());
        po.setSkuId(inventory.getSkuId());
        po.setTotalQuantity(inventory.getTotalQuantity());
        po.setLockedQuantity(inventory.getLockedQuantity());
        po.setCreatedAt(inventory.getCreatedAt() != null ? inventory.getCreatedAt() : now);
        po.setUpdatedAt(inventory.getUpdatedAt() != null ? inventory.getUpdatedAt() : now);
        po.setVersion(inventory.getVersion());
        return po;
    }

    private InventoryLog logToDomain(InventoryLogPo po) {
        // CHG-0015：读取回填日志雪花 ID（此前漏传导致流水列表 id 恒为 0，字符串化后前端直接可见）
        InventoryLog log = new InventoryLog(po.getSkuId(),
                InventoryOperationType.valueOf(po.getOperationType()),
                po.getQuantity(), po.getBeforeQuantity(), po.getAfterQuantity(),
                po.getBusinessId(), po.getOperator(), po.getTraceId(),
                po.getOccurredAt());
        log.assignId(po.getId() == null ? 0L : po.getId());
        return log;
    }

    private InventoryReservation reservationToDomain(InventoryReservationPo po) {
        Instant created = po.getCreatedAt() == null ? Instant.EPOCH : po.getCreatedAt();
        Instant updated = po.getUpdatedAt() == null ? created : po.getUpdatedAt();
        return InventoryReservation.reconstitute(po.getId(), po.getReservationId(), po.getSkuId(),
                po.getQuantity(), ReservationStatus.valueOf(po.getStatus()), created, updated);
    }

    private InventoryReservationPo reservationToPo(InventoryReservation reservation) {
        InventoryReservationPo po = new InventoryReservationPo();
        po.setId(reservation.getId() == 0L ? null : reservation.getId());
        po.setReservationId(reservation.getReservationId());
        po.setSkuId(reservation.getSkuId());
        po.setQuantity(reservation.getQuantity());
        po.setStatus(reservation.getStatus().name());
        po.setCreatedAt(reservation.getCreatedAt());
        po.setUpdatedAt(reservation.getUpdatedAt());
        return po;
    }
}

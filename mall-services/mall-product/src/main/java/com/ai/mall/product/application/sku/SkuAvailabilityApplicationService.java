package com.ai.mall.product.application.sku;

import com.ai.mall.common.web.exception.BusinessException;
import com.ai.mall.product.domain.shared.ProductErrorCode;
import com.ai.mall.product.infrastructure.client.InventoryAvailabilityClient;
import com.ai.mall.product.interfaces.rest.mall.dto.MallSkuAvailabilityDtos.SkuAvailabilityView;
import com.ai.mall.product.interfaces.rest.mall.dto.StockStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * SKU 可售状态聚合应用服务：调用 inventory 一次批量查询，映射三态，故障降级 UNKNOWN。
 */
@Service
public class SkuAvailabilityApplicationService {

    private static final Logger log = LoggerFactory.getLogger(SkuAvailabilityApplicationService.class);

    /** 单批最大 SKU 数。 */
    public static final int MAX_BATCH_SIZE = 100;

    private final InventoryAvailabilityClient inventoryClient;

    public SkuAvailabilityApplicationService(InventoryAvailabilityClient inventoryClient) {
        this.inventoryClient = inventoryClient;
    }

    /**
     * 批量查询 SKU 可售状态（三态）。
     * inventory 故障/超时/异常 → 全部 UNKNOWN，HTTP 仍 200（不向上游抛 5xx）。
     */
    public List<SkuAvailabilityView> availability(List<Long> skuIds) {
        validate(skuIds);
        Map<Long, Long> qtyMap;
        try {
            qtyMap = inventoryClient.availability(skuIds);
        } catch (Exception e) {
            log.warn("inventory availability 调用失败，降级为 UNKNOWN: {}", e.getMessage());
            return allUnknown(skuIds);
        }
        List<SkuAvailabilityView> result = new ArrayList<>(skuIds.size());
        for (Long id : skuIds) {
            long qty = qtyMap.getOrDefault(id, 0L);
            result.add(new SkuAvailabilityView(id, StockStatus.fromAvailableQty(qty)));
        }
        return result;
    }

    private void validate(List<Long> skuIds) {
        if (skuIds == null || skuIds.isEmpty()) {
            throw new BusinessException(ProductErrorCode.AVAILABILITY_BATCH_INVALID, "skuIds 不能为空");
        }
        if (skuIds.size() > MAX_BATCH_SIZE) {
            throw new BusinessException(ProductErrorCode.AVAILABILITY_BATCH_INVALID, "skuIds 数量不能超过 100");
        }
        for (Long id : skuIds) {
            if (id == null || id <= 0) {
                throw new BusinessException(ProductErrorCode.AVAILABILITY_BATCH_INVALID, "skuId 必须为正整数");
            }
        }
    }

    private List<SkuAvailabilityView> allUnknown(List<Long> skuIds) {
        List<SkuAvailabilityView> result = new ArrayList<>(skuIds.size());
        for (Long id : skuIds) {
            result.add(new SkuAvailabilityView(id, StockStatus.UNKNOWN));
        }
        return result;
    }
}

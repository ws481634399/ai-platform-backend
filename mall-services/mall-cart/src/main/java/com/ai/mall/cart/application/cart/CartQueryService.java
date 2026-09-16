package com.ai.mall.cart.application.cart;

import com.ai.mall.cart.application.cart.CartReadModel.CartView;
import com.ai.mall.cart.application.cart.InventoryAvailabilityClient.SkuAvailability;
import com.ai.mall.cart.application.cart.ProductSkuClient.SkuSnapshot;
import com.ai.mall.cart.domain.cart.CartErrorCode;
import com.ai.mall.cart.domain.cart.CartItem;
import com.ai.mall.cart.domain.cart.CartRepository;
import com.ai.mall.common.web.exception.BusinessException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * 购物车读模型应用服务（CHG-0018 DU-BE-802）。
 *
 * <p>读车一次 HGETALL + 一次 product sku/batch + 一次 inventory availability（禁 N+1，
 * 车条目上限 100 即单批上限，无需分片）。product/inventory 任一故障都条目级降级、
 * 整车仍返回 200（AC-012）；仅 Redis 自身故障归一为 503（车数据不可得无法装配）。
 * 本服务无任何写 Redis 路径，校验不改数量/选择（只读）。
 */
@Service
public class CartQueryService {

    private static final Logger log = LoggerFactory.getLogger(CartQueryService.class);

    private final CartRepository repository;
    private final ProductSkuClient productSkuClient;
    private final InventoryAvailabilityClient inventoryAvailabilityClient;

    public CartQueryService(CartRepository repository,
                            ProductSkuClient productSkuClient,
                            InventoryAvailabilityClient inventoryAvailabilityClient) {
        this.repository = repository;
        this.productSkuClient = productSkuClient;
        this.inventoryAvailabilityClient = inventoryAvailabilityClient;
    }

    /** 装配实时读模型；空车直接空响应且不发起任何依赖调用。 */
    public CartView getView(long memberId) {
        List<CartItem> items;
        try {
            items = repository.findItems(memberId);
        } catch (DataAccessException | IllegalStateException ex) {
            throw new BusinessException(CartErrorCode.CART_STORAGE_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE);
        }
        if (items.isEmpty()) {
            return CartView.empty();
        }
        List<Long> skuIds = items.stream().map(CartItem::skuId).toList();

        boolean productFailed = false;
        Map<Long, SkuSnapshot> snapshots = Map.of();
        try {
            snapshots = productSkuClient.findSnapshots(skuIds).stream()
                    .filter(s -> s.skuId() != null)
                    .collect(Collectors.toMap(SkuSnapshot::skuId, Function.identity(), (a, b) -> a));
        } catch (RuntimeException ex) {
            // product 故障不抛出：整车商品态降级 UNKNOWN，仍展示车中数量/选择
            productFailed = true;
            log.warn("product sku/batch 不可用，读车商品态降级 UNKNOWN: {}", ex.getMessage());
        }

        boolean inventoryFailed = false;
        Map<Long, Long> availability = Map.of();
        try {
            availability = inventoryAvailabilityClient.findAvailability(skuIds).stream()
                    .collect(Collectors.toMap(SkuAvailability::skuId, SkuAvailability::availableQty, (a, b) -> a));
        } catch (RuntimeException ex) {
            // inventory 故障仅库存态 UNKNOWN；金额按未知库存排除口径处理
            inventoryFailed = true;
            log.warn("inventory availability 不可用，读车库存态降级 UNKNOWN: {}", ex.getMessage());
        }

        return CartViewAssembler.assemble(items, snapshots, availability, productFailed, inventoryFailed);
    }
}

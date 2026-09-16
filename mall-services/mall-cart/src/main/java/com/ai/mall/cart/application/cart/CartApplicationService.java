package com.ai.mall.cart.application.cart;

import com.ai.mall.cart.domain.cart.CartErrorCode;
import com.ai.mall.cart.domain.cart.CartItem;
import com.ai.mall.cart.domain.cart.CartRepository;
import com.ai.mall.cart.domain.cart.CartScriptCode;
import com.ai.mall.cart.application.cart.ProductSkuClient.SkuSnapshot;
import com.ai.mall.common.web.exception.BusinessException;
import com.ai.mall.common.core.result.CommonErrorCode;
import java.util.List;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * 购物车应用服务（CHG-0018 DU-BE-801）。
 *
 * <p>会员购物车写模型用例全集：加购（先可售校验后原子写）、改量、单/批删除、
 * 单选/全选、原始车读取。memberId 一律由接口层从 SecurityContext 传入，
 * 本服务不接受任何外部指定的 memberId（AC-007）。
 *
 * <p>错误语义：商品不可售 → 400 {@link CartErrorCode#SKU_NOT_SALABLE}（车不变）；
 * product 依赖故障由 {@link ProductSkuClient} 抛 503；Redis 故障在此统一归一为
 * 503 {@link CartErrorCode#CART_STORAGE_UNAVAILABLE}。
 */
@Service
public class CartApplicationService {

    private final CartRepository repository;
    private final ProductSkuClient productSkuClient;

    public CartApplicationService(CartRepository repository, ProductSkuClient productSkuClient) {
        this.repository = repository;
        this.productSkuClient = productSkuClient;
    }

    /**
     * 加购：先经 product 内部契约校验双状态并取价，再原子累加写入。
     * 不可售/任一上限拒绝时购物车保持原样。
     */
    public List<CartItem> add(long memberId, long skuId, int quantity) {
        if (quantity < 1) {
            throw new BusinessException(CommonErrorCode.PARAM_INVALID, HttpStatus.BAD_REQUEST, "加购数量必须大于0");
        }
        List<SkuSnapshot> snapshots = productSkuClient.findSnapshots(List.of(skuId));
        SkuSnapshot snapshot = snapshots.stream().findFirst().orElse(null);
        if (snapshot == null || !snapshot.salable() || snapshot.salePriceInCents() == null) {
            // 下架/禁用/不存在统一文案，不暴露存在性；此时尚未触写 Redis，车不变
            throw new BusinessException(CartErrorCode.SKU_NOT_SALABLE, HttpStatus.BAD_REQUEST);
        }
        CartScriptCode code = invokeStorage(() ->
                repository.add(memberId, skuId, quantity, snapshot.salePriceInCents()));
        switch (code) {
            case QTY_LIMIT -> throw new BusinessException(CartErrorCode.CART_QUANTITY_LIMIT, HttpStatus.BAD_REQUEST);
            case ITEMS_LIMIT -> throw new BusinessException(CartErrorCode.CART_ITEMS_LIMIT, HttpStatus.BAD_REQUEST);
            default -> { }
        }
        return list(memberId);
    }

    /** 改量 1..999；条目不存在 404。 */
    public List<CartItem> updateQuantity(long memberId, long skuId, int quantity) {
        if (quantity < 1) {
            throw new BusinessException(CommonErrorCode.PARAM_INVALID, HttpStatus.BAD_REQUEST, "数量必须大于0");
        }
        CartScriptCode code = invokeStorage(() -> repository.updateQuantity(memberId, skuId, quantity));
        if (code == CartScriptCode.NOT_FOUND) {
            throw new BusinessException(CartErrorCode.CART_ITEM_NOT_FOUND, HttpStatus.NOT_FOUND);
        }
        if (code == CartScriptCode.QTY_LIMIT) {
            throw new BusinessException(CartErrorCode.CART_QUANTITY_LIMIT, HttpStatus.BAD_REQUEST);
        }
        return list(memberId);
    }

    /** 删除单条：幂等。 */
    public List<CartItem> remove(long memberId, long skuId) {
        invokeStorage(() -> {
            repository.remove(memberId, skuId);
            return CartScriptCode.OK;
        });
        return list(memberId);
    }

    /** 批量删除：不存在项忽略，整体幂等。 */
    public List<CartItem> removeBatch(long memberId, List<Long> skuIds) {
        invokeStorage(() -> {
            repository.removeBatch(memberId, skuIds);
            return CartScriptCode.OK;
        });
        return list(memberId);
    }

    /** 单条目勾选/取消：条目不存在 404。 */
    public List<CartItem> select(long memberId, long skuId, boolean selected) {
        CartScriptCode code = invokeStorage(() -> repository.selectOne(memberId, skuId, selected));
        if (code == CartScriptCode.NOT_FOUND) {
            throw new BusinessException(CartErrorCode.CART_ITEM_NOT_FOUND, HttpStatus.NOT_FOUND);
        }
        return list(memberId);
    }

    /** 全车勾选/取消（空车 no-op 成功）。 */
    public List<CartItem> selectAll(long memberId, boolean selected) {
        invokeStorage(() -> {
            repository.selectAll(memberId, selected);
            return CartScriptCode.OK;
        });
        return list(memberId);
    }

    /** 读取原始条目（本 Story 视图；DU-BE-802 将替换为商品/价/库存聚合读模型）。 */
    public List<CartItem> list(long memberId) {
        return invokeStorage(() -> repository.findItems(memberId));
    }

    /** M4 预留：内部查询当前勾选的 skuId/数量。 */
    public List<SelectedItem> selectedItems(long memberId) {
        return list(memberId).stream()
                .filter(CartItem::selected)
                .map(item -> new SelectedItem(item.skuId(), item.quantity()))
                .toList();
    }

    /** 选中项内部视图。 */
    public record SelectedItem(long skuId, int quantity) {
    }

    /** 统一把 Redis 故障归一为 503，避免 DataAccessException 泄漏为 500。 */
    private static <T> T invokeStorage(java.util.function.Supplier<T> action) {
        try {
            return action.get();
        } catch (DataAccessException | IllegalStateException ex) {
            throw new BusinessException(CartErrorCode.CART_STORAGE_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE);
        }
    }
}

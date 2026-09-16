package com.ai.mall.cart.application.cart;

import com.ai.mall.cart.application.cart.ProductSkuClient.SkuSnapshot;
import com.ai.mall.cart.domain.cart.CartConstants;
import com.ai.mall.cart.domain.cart.CartErrorCode;
import com.ai.mall.cart.domain.cart.CartRepository;
import com.ai.mall.cart.domain.cart.CartRepository.DroppedSku;
import com.ai.mall.cart.domain.cart.CartRepository.MergeItem;
import com.ai.mall.cart.domain.cart.CartRepository.MergeResult;
import com.ai.mall.cart.domain.cart.CartRepository.MergedSku;
import com.ai.mall.cart.domain.cart.CartRepository.TruncatedSku;
import com.ai.mall.common.web.exception.BusinessException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * 游客购物车合并应用服务（CHG-0018 DU-BE-803）。
 *
 * <p>合并流程：前端登录成功后调 merge-token 取一次性凭证 → 调 merge 提交游客车；
 * 应用层先经 product sku/batch 批量校验可售性（失效条目 dropped，不入 Lua），
 * 有效条目交由 {@link CartRepository#merge} 单 Lua 原子完成 token 消费+合并+TTL。
 *
 * <p>幂等：token 在 Lua 内首行 GET 校验后即 DEL，重放必 TOKEN_MISSING 不累加；
 * 伪造/串号 token 值≠memberId → TOKEN_MISMATCH（401）。
 */
@Service
public class CartMergeService {

    private final CartRepository repository;
    private final ProductSkuClient productSkuClient;

    public CartMergeService(CartRepository repository, ProductSkuClient productSkuClient) {
        this.repository = repository;
        this.productSkuClient = productSkuClient;
    }

    /** 签发一次性合并 token（300s）。 */
    public MergeTokenView issueToken(long memberId) {
        String token = repository.issueMergeToken(memberId);
        return new MergeTokenView(token, CartConstants.MERGE_TOKEN_TTL_SECONDS);
    }

    /**
     * 合并游客车到会员车。
     *
     * @param memberId 当前会员 ID
     * @param token    合并凭证
     * @param items    游客车条目（含 skuId/quantity/selected）
     */
    public MergeResultView merge(long memberId, String token, List<GuestCartItem> items) {
        if (items == null || items.isEmpty()) {
            // 空合并：仍消费 token（前端应避免传空），返回空结果
            MergeResult result = repository.merge(memberId, token, List.of());
            return toView(result, List.of());
        }
        // 1. 批量可售校验（应用层只读，不写 Redis；失效项直接 dropped）
        List<Long> skuIds = items.stream().map(GuestCartItem::skuId).distinct().toList();
        List<SkuSnapshot> snapshots = productSkuClient.findSnapshots(skuIds);
        Map<Long, SkuSnapshot> snapshotBySku = snapshots.stream()
                .collect(Collectors.toMap(s -> s.skuId(), s -> s, (a, b) -> a));

        List<MergeItem> validItems = new ArrayList<>(items.size());
        List<DroppedSku> dropped = new ArrayList<>();
        for (GuestCartItem item : items) {
            SkuSnapshot snap = snapshotBySku.get(item.skuId());
            if (snap == null || !snap.salable() || snap.salePriceInCents() == null) {
                dropped.add(new DroppedSku(item.skuId(), "SKU_NOT_SALABLE"));
                continue;
            }
            int qty = Math.min(item.quantity(), CartConstants.MAX_QUANTITY);
            validItems.add(new MergeItem(item.skuId(), qty, item.selected(), snap.salePriceInCents()));
        }

        // 2. 单 Lua 原子合并
        MergeResult result = repository.merge(memberId, token, validItems);

        // 3. token 错误由 Lua 返回的特殊 dropped reason 标识
        if (!result.dropped().isEmpty()
                && (result.dropped().get(0).reason().equals("TOKEN_MISSING")
                || result.dropped().get(0).reason().equals("TOKEN_MISMATCH"))) {
            String reason = result.dropped().get(0).reason();
            if ("TOKEN_MISSING".equals(reason)) {
                throw new BusinessException(CartErrorCode.MERGE_TOKEN_EXPIRED, HttpStatus.BAD_REQUEST);
            }
            throw new BusinessException(CartErrorCode.MERGE_TOKEN_INVALID, HttpStatus.UNAUTHORIZED);
        }

        // 合并应用层 dropped（失效）与 Lua 层 dropped（超 100 条目）
        List<DroppedSku> allDropped = new ArrayList<>(result.dropped());
        allDropped.addAll(dropped);
        return new MergeResultView(
                result.merged().stream().map(m -> new MergedSkuView(m.skuId(), m.quantity())).toList(),
                result.truncated().stream().map(t -> new TruncatedSkuView(t.skuId(), t.finalQuantity())).toList(),
                allDropped.stream().map(d -> new DroppedSkuView(d.skuId(), d.reason())).toList());
    }

    private static MergeResultView toView(MergeResult result, List<DroppedSku> extraDropped) {
        List<DroppedSku> all = new ArrayList<>(result.dropped());
        all.addAll(extraDropped);
        return new MergeResultView(
                result.merged().stream().map(m -> new MergedSkuView(m.skuId(), m.quantity())).toList(),
                result.truncated().stream().map(t -> new TruncatedSkuView(t.skuId(), t.finalQuantity())).toList(),
                all.stream().map(d -> new DroppedSkuView(d.skuId(), d.reason())).toList());
    }

    /** 游客车条目入参。 */
    public record GuestCartItem(long skuId, int quantity, boolean selected) {
    }

    public record MergeTokenView(String mergeToken, long expiresIn) {
    }

    public record MergeResultView(List<MergedSkuView> merged, List<TruncatedSkuView> truncated,
                                  List<DroppedSkuView> dropped) {
    }

    public record MergedSkuView(long skuId, int quantity) {
    }

    public record TruncatedSkuView(long skuId, int finalQuantity) {
    }

    public record DroppedSkuView(long skuId, String reason) {
    }

    // 抑制未使用告警：MergedSku/TruncatedSku 仅用于类型推导
    @SuppressWarnings("unused")
    private static void unused(MergedSku m, TruncatedSku t) {
    }
}

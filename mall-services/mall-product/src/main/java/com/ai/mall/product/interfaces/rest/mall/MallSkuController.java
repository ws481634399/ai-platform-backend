package com.ai.mall.product.interfaces.rest.mall;

import com.ai.mall.common.core.result.UnifyResult;
import com.ai.mall.product.application.sku.SkuAvailabilityApplicationService;
import com.ai.mall.product.application.sku.SkuBatchApplicationService;
import com.ai.mall.product.application.sku.SkuBatchApplicationService.SkuBatchItem;
import com.ai.mall.product.interfaces.rest.mall.dto.MallSkuAvailabilityDtos.SkuAvailabilityRequest;
import com.ai.mall.product.interfaces.rest.mall.dto.MallSkuAvailabilityDtos.SkuAvailabilityView;
import com.ai.mall.product.interfaces.rest.mall.dto.MallSkuItemsDtos.SkuItemView;
import com.ai.mall.product.interfaces.rest.mall.dto.MallSkuItemsDtos.SkuItemsRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商城公开 SKU 接口：/api/mall/skus（匿名）。
 *
 * <p>白名单：仅暴露公开字段，不暴露精确库存与下架项。
 */
@RestController
@RequestMapping("/api/mall/skus")
public class MallSkuController {

    private final SkuAvailabilityApplicationService availabilityService;
    private final SkuBatchApplicationService skuBatchService;

    public MallSkuController(SkuAvailabilityApplicationService availabilityService,
                             SkuBatchApplicationService skuBatchService) {
        this.availabilityService = availabilityService;
        this.skuBatchService = skuBatchService;
    }

    @PostMapping("/availability")
    public UnifyResult<List<SkuAvailabilityView>> availability(@RequestBody SkuAvailabilityRequest request) {
        return UnifyResult.ok(availabilityService.availability(request.skuIds()));
    }

    /**
     * 公开 SKU 快照批量查询（CHG-0018 DU-BE-803）：游客购物车展示用。
     * 仅返回 product ON_SALE 且 sku ENABLED 的条目；不可售/不存在的 skuId 不返回。
     */
    @PostMapping("/items")
    public UnifyResult<List<SkuItemView>> items(@Valid @RequestBody SkuItemsRequest request) {
        List<SkuBatchItem> snapshots = skuBatchService.batch(request.skuIds());
        List<SkuItemView> views = snapshots.stream()
                .filter(SkuBatchItem::salable)
                .map(s -> new SkuItemView(s.productId(), s.productName(), s.skuId(), s.skuCode(),
                        s.salePriceInCents(), s.mainImageUrl(), s.specifications()))
                .toList();
        return UnifyResult.ok(views);
    }
}

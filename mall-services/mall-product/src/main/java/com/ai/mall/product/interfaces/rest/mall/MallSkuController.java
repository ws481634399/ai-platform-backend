package com.ai.mall.product.interfaces.rest.mall;

import com.ai.mall.common.core.result.UnifyResult;
import com.ai.mall.product.application.sku.SkuAvailabilityApplicationService;
import com.ai.mall.product.interfaces.rest.mall.dto.MallSkuAvailabilityDtos.SkuAvailabilityRequest;
import com.ai.mall.product.interfaces.rest.mall.dto.MallSkuAvailabilityDtos.SkuAvailabilityView;
import java.util.List;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商城公开 SKU 可售状态接口：/api/mall/skus/availability（匿名）。
 *
 * <p>仅返回三态（OUT_OF_STOCK/LOW_STOCK/IN_STOCK/UNKNOWN），不暴露精确库存。
 * inventory 故障时全部降级 UNKNOWN，HTTP 仍 200。
 */
@RestController
@RequestMapping("/api/mall/skus")
public class MallSkuController {

    private final SkuAvailabilityApplicationService service;

    public MallSkuController(SkuAvailabilityApplicationService service) {
        this.service = service;
    }

    @PostMapping("/availability")
    public UnifyResult<List<SkuAvailabilityView>> availability(@RequestBody SkuAvailabilityRequest request) {
        return UnifyResult.ok(service.availability(request.skuIds()));
    }
}

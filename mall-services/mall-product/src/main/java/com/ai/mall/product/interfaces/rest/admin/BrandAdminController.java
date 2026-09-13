package com.ai.mall.product.interfaces.rest.admin;

import com.ai.mall.common.core.result.UnifyResult;
import com.ai.mall.product.application.brand.BrandApplicationService;
import com.ai.mall.product.application.brand.BrandCommands.BrandPageQuery;
import com.ai.mall.product.application.brand.BrandCommands.ChangeBrandStatusCommand;
import com.ai.mall.product.application.brand.BrandCommands.CreateBrandCommand;
import com.ai.mall.product.application.brand.BrandCommands.UpdateBrandCommand;
import com.ai.mall.product.domain.brand.Brand;
import com.ai.mall.product.domain.brand.BrandRepository.BrandPageResult;
import com.ai.mall.product.interfaces.rest.admin.dto.BrandDtos.BrandStatusRequest;
import com.ai.mall.product.interfaces.rest.admin.dto.BrandDtos.BrandView;
import com.ai.mall.product.interfaces.rest.admin.dto.BrandDtos.CreateBrandRequest;
import com.ai.mall.product.interfaces.rest.admin.dto.BrandDtos.PageView;
import com.ai.mall.product.interfaces.rest.admin.dto.BrandDtos.UpdateBrandRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 品牌管理端接口：/api/admin/brands。
 */
@RestController
@RequestMapping("/api/admin/brands")
public class BrandAdminController {

    private final BrandApplicationService service;

    public BrandAdminController(BrandApplicationService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('product:brand:list')")
    public UnifyResult<PageView<BrandView>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        BrandPageResult result = service.page(new BrandPageQuery(keyword, status, page, size));
        List<BrandView> records = result.records().stream().map(BrandAdminController::toView).toList();
        return UnifyResult.ok(new PageView<>(records, result.total(), result.page(), result.size()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('product:brand:list')")
    public UnifyResult<BrandView> get(@PathVariable long id) {
        return UnifyResult.ok(toView(service.getById(id)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('product:brand:create')")
    public UnifyResult<Map<String, Long>> create(@Valid @RequestBody CreateBrandRequest request) {
        long id = service.create(new CreateBrandCommand(request.name(), request.logo(),
                request.description(), request.sort()));
        return UnifyResult.ok(Map.of("id", id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('product:brand:update')")
    public UnifyResult<Void> update(@PathVariable long id, @Valid @RequestBody UpdateBrandRequest request) {
        service.update(id, new UpdateBrandCommand(request.name(), request.logo(),
                request.description(), request.sort()));
        return UnifyResult.ok();
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('product:brand:disable')")
    public UnifyResult<Void> changeStatus(@PathVariable long id,
                                          @Valid @RequestBody BrandStatusRequest request) {
        service.changeStatus(id, new ChangeBrandStatusCommand(request.status()));
        return UnifyResult.ok();
    }

    private static BrandView toView(Brand brand) {
        return new BrandView(brand.getId(), brand.getName(), brand.getLogo(), brand.getDescription(),
                brand.getSort(), brand.getStatus().name());
    }
}

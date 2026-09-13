package com.ai.mall.product.interfaces.rest.admin;

import com.ai.mall.common.core.result.UnifyResult;
import com.ai.mall.product.application.category.CategoryApplicationService;
import com.ai.mall.product.application.category.CategoryCommands.ChangeCategoryStatusCommand;
import com.ai.mall.product.application.category.CategoryCommands.CreateCategoryCommand;
import com.ai.mall.product.application.category.CategoryCommands.UpdateCategoryCommand;
import com.ai.mall.product.domain.category.Category;
import com.ai.mall.product.interfaces.rest.admin.dto.CategoryDtos.CategoryStatusRequest;
import com.ai.mall.product.interfaces.rest.admin.dto.CategoryDtos.CategoryTreeView;
import com.ai.mall.product.interfaces.rest.admin.dto.CategoryDtos.CreateCategoryRequest;
import com.ai.mall.product.interfaces.rest.admin.dto.CategoryDtos.UpdateCategoryRequest;
import com.ai.mall.product.interfaces.rest.admin.dto.CategoryTreeAssembler;
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
import org.springframework.web.bind.annotation.RestController;

/**
 * 分类管理端接口：/api/admin/categories。
 * 细粒度权限取 JWT permissions claim（mall-identity 签发），由资源服务器方法级校验。
 */
@RestController
@RequestMapping("/api/admin/categories")
public class CategoryAdminController {

    private final CategoryApplicationService service;

    public CategoryAdminController(CategoryApplicationService service) {
        this.service = service;
    }

    @GetMapping("/tree")
    @PreAuthorize("hasAuthority('product:category:list')")
    public UnifyResult<List<CategoryTreeView>> tree() {
        return UnifyResult.ok(CategoryTreeAssembler.toTree(service.listTree()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('product:category:list')")
    public UnifyResult<?> get(@PathVariable long id) {
        return UnifyResult.ok(CategoryTreeAssembler.toView(service.getById(id)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('product:category:create')")
    public UnifyResult<Map<String, Long>> create(@Valid @RequestBody CreateCategoryRequest request) {
        long id = service.create(new CreateCategoryCommand(request.name(), request.parentId(), request.sort()));
        return UnifyResult.ok(Map.of("id", id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('product:category:update')")
    public UnifyResult<Void> update(@PathVariable long id, @Valid @RequestBody UpdateCategoryRequest request) {
        service.update(id, new UpdateCategoryCommand(request.name(), request.parentId(), request.sort()));
        return UnifyResult.ok();
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('product:category:disable')")
    public UnifyResult<Void> changeStatus(@PathVariable long id,
                                          @Valid @RequestBody CategoryStatusRequest request) {
        service.changeStatus(id, new ChangeCategoryStatusCommand(request.status()));
        return UnifyResult.ok();
    }
}

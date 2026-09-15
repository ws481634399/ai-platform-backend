package com.ai.mall.product.interfaces.rest.mall;

import com.ai.mall.common.core.result.UnifyResult;
import com.ai.mall.product.application.category.CategoryApplicationService;
import com.ai.mall.product.interfaces.rest.mall.dto.MallCatalogDtos.CategoryNode;
import com.ai.mall.product.interfaces.rest.mall.dto.MallCategoryTreeAssembler;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商城公开分类接口：/api/mall/categories（匿名，仅返回启用分类树）。
 */
@RestController
@RequestMapping("/api/mall/categories")
public class MallCategoryController {

    private final CategoryApplicationService service;

    public MallCategoryController(CategoryApplicationService service) {
        this.service = service;
    }

    @GetMapping("/tree")
    public UnifyResult<List<CategoryNode>> tree() {
        return UnifyResult.ok(MallCategoryTreeAssembler.toTree(service.mallTree()));
    }
}

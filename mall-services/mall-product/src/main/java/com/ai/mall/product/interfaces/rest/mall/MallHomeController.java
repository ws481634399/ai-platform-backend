package com.ai.mall.product.interfaces.rest.mall;

import com.ai.mall.common.core.result.UnifyResult;
import com.ai.mall.product.application.home.HomeApplicationService;
import com.ai.mall.product.interfaces.rest.mall.dto.MallHomeDtos.HomeView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商城首页聚合接口：GET /api/mall/home（匿名）。
 *
 * <p>返回分类入口、新品、推荐（fallback）、banners（空占位）。
 */
@RestController
@RequestMapping("/api/mall/home")
public class MallHomeController {

    private final HomeApplicationService service;

    public MallHomeController(HomeApplicationService service) {
        this.service = service;
    }

    @GetMapping
    public UnifyResult<HomeView> home() {
        return UnifyResult.ok(service.home());
    }
}

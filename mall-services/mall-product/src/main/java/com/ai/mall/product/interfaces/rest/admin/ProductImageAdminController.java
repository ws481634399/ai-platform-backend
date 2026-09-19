package com.ai.mall.product.interfaces.rest.admin;

import com.ai.mall.common.core.result.UnifyResult;
import com.ai.mall.common.web.exception.BusinessException;
import com.ai.mall.product.application.image.ImageScene;
import com.ai.mall.product.application.image.ImageUploadApplicationService;
import com.ai.mall.product.domain.shared.ProductErrorCode;
import com.ai.mall.product.interfaces.rest.admin.dto.ProductImageDtos.ImageUploadResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 管理端商品图片上传接口（CHG-0023 STORY-007-01-01-02）：/api/admin/product-images。
 *
 * <p>multipart：part {@code file} + 表单字段 {@code scene}（BRAND/PRODUCT）。
 * 权限复用既有商品/品牌写权限码并集（不新增权限码）：持其一即可上传。
 */
@RestController
@RequestMapping("/api/admin/product-images")
public class ProductImageAdminController {

    private final ImageUploadApplicationService imageUploadService;

    public ProductImageAdminController(ImageUploadApplicationService imageUploadService) {
        this.imageUploadService = imageUploadService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('product:brand:update') or hasAuthority('product:product:update')")
    public UnifyResult<ImageUploadResponse> upload(
            @RequestPart("file") MultipartFile file,
            // required=false：缺失/空白与非法值统一在方法内映射 A2103，避免框架默认异常码
            @RequestParam(value = "scene", required = false) String scene) throws IOException {
        final ImageScene imageScene;
        try {
            imageScene = ImageScene.from(scene);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ProductErrorCode.IMAGE_SCENE_INVALID, HttpStatus.BAD_REQUEST);
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ProductErrorCode.IMAGE_TYPE_INVALID, HttpStatus.BAD_REQUEST,
                    "图片文件不能为空");
        }
        String url = imageUploadService.upload(imageScene, file.getBytes());
        return UnifyResult.ok(new ImageUploadResponse(url));
    }
}

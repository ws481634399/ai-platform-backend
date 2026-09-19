package com.ai.mall.product.application.image;

import com.ai.mall.common.core.image.ImageFormat;
import com.ai.mall.common.web.exception.BusinessException;
import com.ai.mall.product.application.port.ProductImageStorage;
import com.ai.mall.product.domain.shared.ProductErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * 图片上传应用服务（CHG-0023 STORY-007-01-01-02）。
 *
 * <p>校验顺序（全部在触达对象存储之前短路）：空文件 → 超限 → 魔数白名单；
 * 通过后调用存储端口返回公开 URL。本服务不写业务库（先存后更，URL 由品牌/商品保存接口持久化），
 * 不声明事务。存储故障由端口实现转译 503，本层透传不二次包装。
 */
@Service
public class ImageUploadApplicationService {

    private final ProductImageStorage storage;
    private final long maxBytes;

    public ImageUploadApplicationService(
            ProductImageStorage storage,
            @Value("${mall.upload.image.max-bytes:2097152}") long maxBytes) {
        this.storage = storage;
        this.maxBytes = maxBytes;
    }

    /**
     * 校验并上传图片。
     *
     * @return 可公开访问的图片 URL
     */
    public String upload(ImageScene scene, byte[] content) {
        if (content == null || content.length == 0) {
            throw new BusinessException(ProductErrorCode.IMAGE_TYPE_INVALID, HttpStatus.BAD_REQUEST,
                    "图片文件不能为空");
        }
        if (content.length > maxBytes) {
            throw new BusinessException(ProductErrorCode.IMAGE_TOO_LARGE, HttpStatus.BAD_REQUEST);
        }
        final ImageFormat format;
        try {
            format = ImageFormat.detect(content);
        } catch (IllegalArgumentException ex) {
            // 伪装图片/非白名单格式：拒绝，不接触对象存储
            throw new BusinessException(ProductErrorCode.IMAGE_TYPE_INVALID, HttpStatus.BAD_REQUEST);
        }
        return storage.upload(scene, content, format);
    }
}

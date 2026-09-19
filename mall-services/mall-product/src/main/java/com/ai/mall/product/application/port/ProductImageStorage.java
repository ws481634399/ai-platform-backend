package com.ai.mall.product.application.port;

import com.ai.mall.common.core.image.ImageFormat;
import com.ai.mall.product.application.image.ImageScene;

/**
 * 商品图片对象存储出站端口（CHG-0023 STORY-007-01-01-02）。
 *
 * <p>实现侧（MinIO）负责 bucket 幂等就绪、对象 key 生成（按 scene 前缀）与公开读 URL 拼接；
 * 存储故障统一以 {@code BusinessException(STORAGE_UNAVAILABLE, 503)} 抛出。
 * 本端口不写业务库——URL 仍由品牌/商品既有保存接口持久化（先存后更）。
 */
public interface ProductImageStorage {

    /**
     * 上传商品图片对象。
     *
     * @param scene   业务场景（决定 key 前缀 brand/ 或 product/）
     * @param content 已通过白名单魔数与大小上限校验的图片字节
     * @param format  服务端按魔数判定的格式
     * @return 可直接访问的公开读 URL
     */
    String upload(ImageScene scene, byte[] content, ImageFormat format);
}

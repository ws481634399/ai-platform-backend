package com.ai.mall.member.application.port;

import com.ai.mall.common.core.image.ImageFormat;

/**
 * 头像对象存储出站端口（CHG-0016 STORY-003-01-02-01）。
 *
 * <p>实现侧（MinIO）负责 bucket 幂等就绪、对象 key 生成与公开读 URL 拼接；
 * 存储故障统一以 {@code BusinessException(STORAGE_UNAVAILABLE, 503)} 抛出，
 * 应用层据此保证「先传对象成功后再写库」、无半成品 URL。
 */
public interface AvatarStorage {

    /**
     * 上传头像对象。
     *
     * @param memberId 会员 ID（仅用于 key 归属，禁止使用客户端文件名）
     * @param content  已通过白名单魔数与 2MB 上限校验的图片字节
     * @param format   服务端按魔数判定的格式
     * @return 可直接访问的公开读 URL
     */
    String uploadAvatar(long memberId, byte[] content, ImageFormat format);
}

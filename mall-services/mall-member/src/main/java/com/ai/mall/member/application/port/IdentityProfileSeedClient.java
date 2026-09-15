package com.ai.mall.member.application.port;

/**
 * identity profile-seed 出站端口（CHG-0016 STORY-003-01-02-01）。
 *
 * <p>member 发现本地无档案时，经 {@code GET /api/internal/members/{id}/profile-seed}
 * （X-Internal-Token）反向取回账号种子做懒补偿。实现侧语义约定：
 * <ul>
 *   <li>identity 404/401 → {@code BusinessException(PROFILE_INCONSISTENT, 401)}；</li>
 *   <li>连接失败/5xx/信封失败 → {@code BusinessException(PROFILE_SEED_UNAVAILABLE, 503)}。</li>
 * </ul>
 */
public interface IdentityProfileSeedClient {

    /**
     * 取回账号种子；不存在或鉴权失败按 401 语义抛出。
     *
     * @throws com.ai.mall.common.web.exception.BusinessException 见类注释
     */
    ProfileSeed fetchSeed(long memberId);

    /** identity 种子视图：仅含补偿建档所需字段（DU-BE-601 契约）。 */
    record ProfileSeed(long memberId, String username, String status) {
    }
}

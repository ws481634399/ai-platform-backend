package com.ai.mall.member.application.member;

import com.ai.mall.common.core.image.ImageFormat;
import com.ai.mall.common.core.result.CommonErrorCode;
import com.ai.mall.common.web.exception.BusinessException;
import com.ai.mall.member.application.member.ProfileProvisionService.ProvisionCommand;
import com.ai.mall.member.application.port.AvatarStorage;
import com.ai.mall.member.application.port.IdentityProfileSeedClient;
import com.ai.mall.member.application.port.IdentityProfileSeedClient.ProfileSeed;
import com.ai.mall.member.domain.model.member.Gender;
import com.ai.mall.member.domain.model.member.MemberProfile;
import com.ai.mall.member.domain.repository.MemberProfileRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 会员资料应用服务（CHG-0016 STORY-003-01-02-01）。
 *
 * <p>职责：
 * <ul>
 *   <li>GET：取本人档案；档案缺失时调 identity profile-seed 做一次性懒补偿（幂等 upsert）后重查；</li>
 *   <li>PUT：按「null 保留 / 空串清空」合并可变字段，聚合内校验后持久化；</li>
 *   <li>头像：先做 2MB 与魔数白名单校验（拒绝即无任何对象写入），再传对象存储，
 *       成功后才更新 avatar_url；存储故障 503 不产生半成品数据。</li>
 * </ul>
 * memberId 始终由接口层从 SecurityContext 传入，本服务不接受任何外部 ID。
 */
@Service
public class ProfileApplicationService {

    /** 头像大小上限 2MB（requirement-design §2 / AC-018）。 */
    public static final int MAX_AVATAR_BYTES = 2 * 1024 * 1024;

    private final MemberProfileRepository profiles;
    private final IdentityProfileSeedClient seedClient;
    private final ProfileProvisionService provisionService;
    private final AvatarStorage avatarStorage;

    public ProfileApplicationService(MemberProfileRepository profiles,
                                    IdentityProfileSeedClient seedClient,
                                    ProfileProvisionService provisionService,
                                    AvatarStorage avatarStorage) {
        this.profiles = profiles;
        this.seedClient = seedClient;
        this.provisionService = provisionService;
        this.avatarStorage = avatarStorage;
    }

    /** 查本人档案；缺失则懒补偿后重查，补偿仍无档案按认证不一致处理（401）。 */
    public MemberProfile getProfile(long memberId) {
        return profiles.findByMemberId(memberId)
                .orElseGet(() -> compensate(memberId));
    }

    /** 修改资料（部分更新语义）。 */
    @Transactional
    public MemberProfile updateProfile(long memberId, UpdateProfileCommand command) {
        MemberProfile profile = getProfile(memberId);

        Gender gender = command.gender() == null
                ? profile.gender()
                : parseGender(command.gender());
        String phone = mergeField(profile.phone(), command.phone());
        String email = mergeField(profile.email(), command.email());
        String nickname = command.nickname() == null ? profile.nickname() : command.nickname();

        profile.updateProfile(nickname, gender, phone, email);
        profiles.update(profile);
        return profiles.findByMemberId(memberId)
                .orElseThrow(() -> new IllegalStateException("profile vanished after update: " + memberId));
    }

    /**
     * 更换头像：校验（大小/魔数）→ 传对象 → 更新库。任一步失败都不留脏数据。
     *
     * @return 可公开访问的头像 URL
     */
    @Transactional
    public String updateAvatar(long memberId, byte[] content) {
        if (content == null || content.length == 0) {
            throw new BusinessException(MemberProfileErrorCode.FILE_TYPE_INVALID, HttpStatus.BAD_REQUEST,
                    "头像文件不能为空");
        }
        if (content.length > MAX_AVATAR_BYTES) {
            throw new BusinessException(MemberProfileErrorCode.FILE_TOO_LARGE, HttpStatus.BAD_REQUEST);
        }
        final ImageFormat format;
        try {
            format = ImageFormat.detect(content);
        } catch (IllegalArgumentException ex) {
            // 伪装图片/非白名单格式：拒绝，不接触对象存储
            throw new BusinessException(MemberProfileErrorCode.FILE_TYPE_INVALID, HttpStatus.BAD_REQUEST);
        }

        MemberProfile profile = getProfile(memberId);
        String avatarUrl = avatarStorage.uploadAvatar(memberId, content, format);
        profile.changeAvatar(avatarUrl);
        profiles.update(profile);
        return avatarUrl;
    }

    /**
     * 懒补偿（TC-003）：identity 种子不存在/拒绝 → 401；
     * 以「seed:{memberId}」派生确定性 UUID 作为初始化事件 ID，同一会员多次补偿天然幂等。
     */
    private MemberProfile compensate(long memberId) {
        ProfileSeed seed = seedClient.fetchSeed(memberId);
        String eventId = UUID.nameUUIDFromBytes(
                ("member-profile-seed:" + memberId).getBytes(StandardCharsets.UTF_8)).toString();
        provisionService.provision(new ProvisionCommand(
                eventId, seed.memberId(), seed.username(), null, Instant.now()));
        return profiles.findByMemberId(memberId)
                .orElseThrow(() -> new BusinessException(MemberProfileErrorCode.PROFILE_INCONSISTENT,
                        HttpStatus.UNAUTHORIZED));
    }

    /** null=保留原值；空白串=清空（归一 null）；非空=去空格后返回（格式由聚合校验）。 */
    private static String mergeField(String current, String incoming) {
        if (incoming == null) {
            return current;
        }
        String trimmed = incoming.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static Gender parseGender(String raw) {
        try {
            return Gender.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(CommonErrorCode.PARAM_INVALID, HttpStatus.BAD_REQUEST, "性别取值非法");
        }
    }

    /** PUT /me 命令：字段原样携带，null=保留 / 空串=清空的语义在服务内归一。 */
    public record UpdateProfileCommand(String nickname, String gender, String phone, String email) {
    }
}

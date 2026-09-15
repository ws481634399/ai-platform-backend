package com.ai.mall.member.domain.model.member;

import java.time.Instant;
import java.util.regex.Pattern;

/**
 * 会员档案聚合根（CHG-0016）：mall-member 侧会员资料。
 *
 * <p>由 identity 的 MemberRegistered 事件经 provision 幂等初始化；
 * memberId 即身份侧主键（跨服务一致），initializedEventId 为事件幂等键。
 * 资料维护（STORY-003-01-02-01）开放昵称/性别/手机/邮箱修改与头像更换行为，
 * 全部字段不变量在聚合内收口，拒绝非法状态落库。
 */
public final class MemberProfile {

    /** 昵称长度区间（requirement-design §4 / story-spec §3）。 */
    public static final int NICKNAME_MAX_LENGTH = 32;

    /** 头像 URL 列长（member_profile.avatar_url VARCHAR(512)）。 */
    public static final int AVATAR_URL_MAX_LENGTH = 512;

    /** 中国大陆手机号；null 表示未设置，空串在应用层归一为 null。 */
    private static final Pattern PHONE = Pattern.compile("^1[3-9]\\d{9}$");

    /** 邮箱宽松 RFC 形态；长度上限对齐列长 VARCHAR(128)。 */
    private static final Pattern EMAIL = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)+$");
    private static final int EMAIL_MAX_LENGTH = 128;
    private static final int PHONE_MAX_LENGTH = 20;

    private final long memberId;
    private final String username;
    private String nickname;
    private String avatarUrl;
    private Gender gender;
    private String phone;
    private String email;
    private final String initializedEventId;
    private final Instant createdAt;
    private Instant updatedAt;

    private MemberProfile(long memberId, String username, String nickname, String avatarUrl, Gender gender,
                          String phone, String email, String initializedEventId, Instant createdAt, Instant updatedAt) {
        this.memberId = memberId;
        this.username = username;
        this.nickname = nickname;
        this.avatarUrl = avatarUrl;
        this.gender = gender;
        this.phone = phone;
        this.email = email;
        this.initializedEventId = initializedEventId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * 开通档案（provision）：默认昵称由应用层计算（"会员"+memberId 后 6 位），性别默认 UNKNOWN。
     */
    public static MemberProfile provision(long memberId, String username, String nickname,
                                          String initializedEventId, Instant now) {
        if (memberId <= 0) {
            throw new IllegalArgumentException("memberId must be positive");
        }
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username is required");
        }
        if (nickname == null || nickname.isBlank() || nickname.length() > 32) {
            throw new IllegalArgumentException("nickname is required and must be within 32 chars");
        }
        if (initializedEventId == null || initializedEventId.isBlank()) {
            throw new IllegalArgumentException("initializedEventId is required");
        }
        return new MemberProfile(memberId, username, nickname, null, Gender.UNKNOWN,
                null, null, initializedEventId, now, now);
    }

    /** 持久化重建。 */
    public static MemberProfile reconstitute(long memberId, String username, String nickname, String avatarUrl,
                                             String gender, String phone, String email,
                                             String initializedEventId, Instant createdAt, Instant updatedAt) {
        return new MemberProfile(memberId, username, nickname, avatarUrl, Gender.valueOf(gender),
                phone, email, initializedEventId, createdAt, updatedAt);
    }

    /**
     * 修改可变资料（STORY-003-01-02-01）。
     *
     * <p>不变量：昵称 1–32 字非空白；手机号 null 或合法大陆号；邮箱 null 或合法且 ≤128；
     * 性别非 null（清空语义不适用，默认 UNKNOWN）。参数为应用层完成「null 保留 / 空串清空」
     * 合并与归一后的最终值。
     *
     * @throws IllegalArgumentException 任一字段不合法
     */
    public void updateProfile(String nickname, Gender gender, String phone, String email) {
        if (nickname == null || nickname.isBlank()) {
            throw new IllegalArgumentException("昵称不能为空");
        }
        if (nickname.length() > NICKNAME_MAX_LENGTH) {
            throw new IllegalArgumentException("昵称长度不能超过" + NICKNAME_MAX_LENGTH + "字");
        }
        if (phone != null) {
            if (phone.length() > PHONE_MAX_LENGTH || !PHONE.matcher(phone).matches()) {
                throw new IllegalArgumentException("手机号格式不正确");
            }
        }
        if (email != null) {
            if (email.length() > EMAIL_MAX_LENGTH || !EMAIL.matcher(email).matches()) {
                throw new IllegalArgumentException("邮箱格式不正确");
            }
        }
        if (gender == null) {
            throw new IllegalArgumentException("性别取值非法");
        }
        this.nickname = nickname;
        this.gender = gender;
        this.phone = phone;
        this.email = email;
        this.updatedAt = Instant.now();
    }

    /**
     * 更换头像（STORY-003-01-02-01）：URL 由服务端对象存储拼接返回，不接受客户端 URL。
     *
     * @throws IllegalArgumentException URL 空白或超长
     */
    public void changeAvatar(String avatarUrl) {
        if (avatarUrl == null || avatarUrl.isBlank()) {
            throw new IllegalArgumentException("头像URL不能为空");
        }
        if (avatarUrl.length() > AVATAR_URL_MAX_LENGTH) {
            throw new IllegalArgumentException("头像URL过长");
        }
        this.avatarUrl = avatarUrl;
        this.updatedAt = Instant.now();
    }

    public long memberId() {
        return memberId;
    }

    public String username() {
        return username;
    }

    public String nickname() {
        return nickname;
    }

    public String avatarUrl() {
        return avatarUrl;
    }

    public Gender gender() {
        return gender;
    }

    public String phone() {
        return phone;
    }

    public String email() {
        return email;
    }

    public String initializedEventId() {
        return initializedEventId;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}

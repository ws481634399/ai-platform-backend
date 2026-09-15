package com.ai.mall.member.domain.model.member;

import java.time.Instant;

/**
 * 会员档案聚合根（CHG-0016）：mall-member 侧会员资料。
 *
 * <p>由 identity 的 MemberRegistered 事件经 provision 幂等初始化；
 * memberId 即身份侧主键（跨服务一致），initializedEventId 为事件幂等键。
 * 头像/手机/邮箱/性别在资料维护 Story 扩展修改行为，本 Story 仅建档案。
 */
public final class MemberProfile {

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

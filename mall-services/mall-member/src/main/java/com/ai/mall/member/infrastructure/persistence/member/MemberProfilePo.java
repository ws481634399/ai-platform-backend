package com.ai.mall.member.infrastructure.persistence.member;

import java.time.Instant;

/**
 * member_profile 行对象（CHG-0016）。
 */
public class MemberProfilePo {

    private long memberId;
    private String username;
    private String nickname;
    private String avatarUrl;
    private String gender;
    private String phone;
    private String email;
    private String initializedEventId;
    private Instant createdAt;
    private Instant updatedAt;

    public long getMemberId() { return memberId; }
    public void setMemberId(long memberId) { this.memberId = memberId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getInitializedEventId() { return initializedEventId; }
    public void setInitializedEventId(String initializedEventId) { this.initializedEventId = initializedEventId; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

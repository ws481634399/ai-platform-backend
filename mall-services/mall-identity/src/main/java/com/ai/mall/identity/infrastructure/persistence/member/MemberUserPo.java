package com.ai.mall.identity.infrastructure.persistence.member;

import java.time.Instant;

/**
 * member_user 行对象（CHG-0016）：手写 getter/setter，时间用 {@link Instant}。
 * 不承载任何明文密码，passwordHash 列内恒为 BCrypt 哈希。
 */
public class MemberUserPo {

    private long id;
    private String username;
    private String usernameNorm;
    private String passwordHash;
    private String status;
    private long authVersion;
    private Instant createdAt;
    private Instant updatedAt;

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getUsernameNorm() { return usernameNorm; }
    public void setUsernameNorm(String usernameNorm) { this.usernameNorm = usernameNorm; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public long getAuthVersion() { return authVersion; }
    public void setAuthVersion(long authVersion) { this.authVersion = authVersion; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

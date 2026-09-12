package com.ai.mall.identity.infrastructure.persistence.admin;

import java.time.Instant;

public class AdminUserPo {
    private long id;
    private String username;
    private String passwordHash;
    private String status;
    private long authVersion;
    private long permissionVersion;
    private Instant createdAt;
    private Instant updatedAt;
    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public long getAuthVersion() { return authVersion; }
    public void setAuthVersion(long authVersion) { this.authVersion = authVersion; }
    public long getPermissionVersion() { return permissionVersion; }
    public void setPermissionVersion(long permissionVersion) { this.permissionVersion = permissionVersion; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

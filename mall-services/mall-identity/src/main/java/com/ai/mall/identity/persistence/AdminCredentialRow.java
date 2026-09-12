package com.ai.mall.identity.persistence;

public class AdminCredentialRow {
    private long id;
    private String username;
    private String passwordHash;
    private String status;
    private long authVersion;
    private long permissionVersion;

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
}

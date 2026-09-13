package com.ai.mall.common.security;

/**
 * 授权快照在共享 Redis 中的键规范（mall-identity 写入、各下游资源服务只读，单点定义）。
 *
 * <ul>
 *   <li>{@code authz:{adminId}:{permissionVersion}} —— 版本化授权快照 JSON（含权限码集合）；</li>
 *   <li>{@code authz:current:{adminId}} —— 当前最新 permissionVersion 指针。
 *       JWT 只携带认证版本 auth_version、不携带授权版本，下游先读指针再读对应版本快照。</li>
 * </ul>
 */
public final class AuthorizationKeys {

    public static final String PREFIX = "authz:";
    public static final String CURRENT_PREFIX = PREFIX + "current:";

    private AuthorizationKeys() {
    }

    /** 版本化快照键：authz:{adminId}:{permissionVersion} */
    public static String snapshot(long adminId, long permissionVersion) {
        return PREFIX + adminId + ":" + permissionVersion;
    }

    /** 当前版本指针键：authz:current:{adminId} */
    public static String current(long adminId) {
        return CURRENT_PREFIX + adminId;
    }
}

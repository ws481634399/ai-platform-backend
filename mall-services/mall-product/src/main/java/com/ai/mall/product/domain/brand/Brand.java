package com.ai.mall.product.domain.brand;

import com.ai.mall.product.domain.shared.MasterDataStatus;
import java.net.URI;
import java.time.Instant;

/**
 * 品牌聚合根：名称全局唯一（唯一性判定由仓储 + 唯一索引保证）、
 * 名称/Logo/描述/排序的自身不变量内聚于此。
 */
public class Brand {

    private static final int MAX_NAME_LENGTH = 64;
    private static final int MAX_LOGO_LENGTH = 512;
    private static final int MAX_DESCRIPTION_LENGTH = 255;

    private long id;
    private String name;
    private String logo;
    private String description;
    private int sort;
    private MasterDataStatus status;
    private Instant createdAt;
    private Instant updatedAt;

    private Brand(long id, String name, String logo, String description, int sort,
                  MasterDataStatus status, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.name = name;
        this.logo = logo;
        this.description = description;
        this.sort = sort;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /** 新建尚未落库的品牌，默认 ENABLED。 */
    public static Brand createNew(String name, String logo, String description, int sort) {
        return new Brand(0L, normalizeName(name), normalizeLogo(logo), normalizeDescription(description),
                normalizeSort(sort), MasterDataStatus.ENABLED, null, null);
    }

    /** 持久化数据重建聚合。 */
    public static Brand reconstitute(long id, String name, String logo, String description, int sort,
                                     String status, Instant createdAt, Instant updatedAt) {
        return new Brand(id, name, logo, description, sort, MasterDataStatus.from(status), createdAt, updatedAt);
    }

    /** 更新资料（名称/Logo/描述/排序）；状态流转走专门方法。 */
    public void updateProfile(String newName, String newLogo, String newDescription, int newSort) {
        this.name = normalizeName(newName);
        this.logo = normalizeLogo(newLogo);
        this.description = normalizeDescription(newDescription);
        this.sort = normalizeSort(newSort);
    }

    /** 禁用仅状态流转，数据保留、列表仍可见。 */
    public void disable() {
        this.status = MasterDataStatus.DISABLED;
    }

    public void enable() {
        this.status = MasterDataStatus.ENABLED;
    }

    public boolean isEnabled() {
        return status == MasterDataStatus.ENABLED;
    }

    private static String normalizeName(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("品牌名称不能为空");
        }
        String name = raw.trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("品牌名称不能为空");
        }
        if (name.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("品牌名称最长 " + MAX_NAME_LENGTH + " 个字符");
        }
        return name;
    }

    private static String normalizeLogo(String raw) {
        if (raw == null) {
            return null;
        }
        String logo = raw.trim();
        if (logo.isEmpty()) {
            return null;
        }
        if (logo.length() > MAX_LOGO_LENGTH) {
            throw new IllegalArgumentException("品牌 Logo 地址最长 " + MAX_LOGO_LENGTH + " 个字符");
        }
        try {
            String scheme = URI.create(logo).getScheme();
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                throw new IllegalArgumentException("品牌 Logo 必须是 http/https 链接");
            }
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("品牌 Logo 必须是合法的 http/https 链接");
        }
        return logo;
    }

    private static String normalizeDescription(String raw) {
        if (raw == null) {
            return null;
        }
        String description = raw.trim();
        if (description.isEmpty()) {
            return null;
        }
        if (description.length() > MAX_DESCRIPTION_LENGTH) {
            throw new IllegalArgumentException("品牌描述最长 " + MAX_DESCRIPTION_LENGTH + " 个字符");
        }
        return description;
    }

    private static int normalizeSort(int sort) {
        if (sort < 0) {
            throw new IllegalArgumentException("排序值不能为负");
        }
        return Math.min(sort, 9999);
    }

    public long getId() { return id; }
    public String getName() { return name; }
    public String getLogo() { return logo; }
    public String getDescription() { return description; }
    public int getSort() { return sort; }
    public MasterDataStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void assignCreated(long id, Instant now) {
        this.id = id;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void touch(Instant now) {
        this.updatedAt = now;
    }
}

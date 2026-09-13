package com.ai.mall.product.domain.category;

import com.ai.mall.product.domain.shared.MasterDataStatus;
import java.time.Instant;

/**
 * 分类聚合根：承载名称/层级/排序/启停的自身不变量。
 *
 * <p>跨实体规则（父存在、父启用、3 级上限、循环、同级重名）需要仓储数据，
 * 由 {@link CategoryRules} 领域服务在应用层编排时判定。
 */
public class Category {

    private long id;
    private String name;
    private long parentId;
    private int level;
    private int sort;
    private MasterDataStatus status;
    private Instant createdAt;
    private Instant updatedAt;

    private Category(long id, String name, long parentId, int level, int sort,
                     MasterDataStatus status, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.name = name;
        this.parentId = parentId;
        this.level = level;
        this.sort = sort;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /** 新建尚未落库的分类（id 由数据库生成）。 */
    public static Category createNew(String name, long parentId, int level, int sort) {
        return new Category(0L, normalizeName(name), parentId, level, sort,
                MasterDataStatus.ENABLED, null, null);
    }

    /** 持久化数据重建聚合。 */
    public static Category reconstitute(long id, String name, long parentId, int level, int sort,
                                        String status, Instant createdAt, Instant updatedAt) {
        return new Category(id, name, parentId, level, sort, MasterDataStatus.from(status), createdAt, updatedAt);
    }

    public void rename(String newName) {
        this.name = normalizeName(newName);
    }

    /** 移动到新父级下（新层级由领域服务按父级推导后传入）。 */
    public void moveTo(long newParentId, int newLevel) {
        this.parentId = newParentId;
        this.level = newLevel;
    }

    public void changeSort(int newSort) {
        this.sort = newSort;
    }

    /** 禁用仅做状态流转，不级联子分类（AC-008）。 */
    public void disable() {
        this.status = MasterDataStatus.DISABLED;
    }

    public void enable() {
        this.status = MasterDataStatus.ENABLED;
    }

    public boolean isEnabled() {
        return status == MasterDataStatus.ENABLED;
    }

    /** 子树整体平移层级（移动后的级联修正，差值可为负）。 */
    public void shiftLevel(int delta) {
        this.level += delta;
    }

    private static String normalizeName(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("分类名称不能为空");
        }
        String name = raw.trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("分类名称不能为空");
        }
        if (name.length() > 32) {
            throw new IllegalArgumentException("分类名称最长 32 个字符");
        }
        return name;
    }

    public long getId() { return id; }
    public String getName() { return name; }
    public long getParentId() { return parentId; }
    public int getLevel() { return level; }
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

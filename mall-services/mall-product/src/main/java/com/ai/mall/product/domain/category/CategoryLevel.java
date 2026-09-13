package com.ai.mall.product.domain.category;

/**
 * 分类层级约束：一级分类 parentId=0，最多 3 级。
 */
public final class CategoryLevel {

    public static final long ROOT_PARENT_ID = 0L;
    public static final int MIN_LEVEL = 1;
    public static final int MAX_LEVEL = 3;

    private CategoryLevel() {
    }
}

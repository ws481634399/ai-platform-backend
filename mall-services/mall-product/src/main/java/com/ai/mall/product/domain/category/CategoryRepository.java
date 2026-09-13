package com.ai.mall.product.domain.category;

import java.util.List;
import java.util.Optional;

/**
 * 分类仓储端口（领域层定义，infrastructure 层实现）。
 */
public interface CategoryRepository {

    Optional<Category> findById(long id);

    /** 全量平铺加载（分类量级小，树在内存组装），调用方负责排序。 */
    List<Category> findAll();

    /** 同级（排除自身）是否已存在同名分类。名称比较按数据库排序规则大小写不敏感。 */
    boolean existsSiblingName(long parentId, String name, Long excludeId);

    /** 落库新分类，回写生成的 id 与审计时间。 */
    void insert(Category category);

    /** 按 id 更新（名称/父级/层级/排序/状态），不存在时抛异常由实现决定。 */
    boolean update(Category category);
}

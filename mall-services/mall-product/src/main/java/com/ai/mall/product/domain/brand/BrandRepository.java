package com.ai.mall.product.domain.brand;

import java.util.List;
import java.util.Optional;

/**
 * 品牌仓储端口：domain 不感知 MyBatis-Plus，分页用域内值对象表达。
 */
public interface BrandRepository {

    Optional<Brand> findById(long id);

    /**
     * 全局同名判定（excludeId 用于更新排除自身）。
     * 大小写不敏感由数据库排序规则（MySQL utf8mb4_0900_ai_ci / H2 MySQL 模式）保证。
     */
    boolean existsByName(String name, Long excludeId);

    BrandPageResult page(BrandPageQuery query);

    void insert(Brand brand);

    boolean update(Brand brand);

    /** 分页查询条件（page 从 1 开始，应用层负责参数归一）。 */
    record BrandPageQuery(String keyword, String status, int page, int size) {
    }

    /** 分页结果。 */
    record BrandPageResult(List<Brand> records, long total, int page, int size) {
    }
}

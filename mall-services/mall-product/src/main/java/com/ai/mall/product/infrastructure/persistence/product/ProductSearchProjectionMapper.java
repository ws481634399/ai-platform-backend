package com.ai.mall.product.infrastructure.persistence.product;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 搜索投影只读 Mapper（CHG-0021）。
 *
 * <p>口径冻结：product_spu.status='ON_SALE' AND deleted=0 且 EXISTS 启用 SKU
 * （INNER JOIN 启用 SKU 价区派生表），分类/品牌名 LEFT JOIN（主数据理论上必在）。
 * keywords M5 固定空串（'' AS keywords）。
 */
@Mapper
public interface ProductSearchProjectionMapper {

    String PROJECTION_SELECT =
            "SELECT p.id AS id, p.product_name AS product_name, '' AS keywords, "
            + "p.category_id AS category_id, c.name AS category_name, "
            + "p.brand_id AS brand_id, b.name AS brand_name, "
            + "p.main_image_url AS main_image, p.status AS status, "
            + "pr.lo AS min_price_fen, pr.hi AS max_price_fen, "
            + "p.published_at AS published_at, p.updated_at AS updated_at "
            + "FROM product_spu p "
            + "INNER JOIN ("
            + "  SELECT product_id, MIN(sale_price) AS lo, MAX(sale_price) AS hi "
            + "  FROM product_sku WHERE status = 'ENABLED' AND deleted = 0 GROUP BY product_id"
            + ") pr ON pr.product_id = p.id "
            + "LEFT JOIN product_category c ON c.id = p.category_id "
            + "LEFT JOIN product_brand b ON b.id = p.brand_id "
            + "WHERE p.status = 'ON_SALE' AND p.deleted = 0 ";

    /** 分页投影（按 id 稳定排序，配合 search 端 500/批游标拉取）。 */
    @Select(PROJECTION_SELECT + " ORDER BY p.id ASC")
    Page<ProductSearchProjectionPo> selectProjectionPage(Page<ProductSearchProjectionPo> page);

    /** 单条投影；非在架/无启用 SKU/不存在均返回 null（调用方据此走删除分支）。 */
    @Select(PROJECTION_SELECT + " AND p.id = #{productId} LIMIT 1")
    ProductSearchProjectionPo selectProjectionById(@Param("productId") long productId);
}

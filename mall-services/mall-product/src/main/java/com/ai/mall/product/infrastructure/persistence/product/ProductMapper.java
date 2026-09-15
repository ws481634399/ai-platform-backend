package com.ai.mall.product.infrastructure.persistence.product;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ProductMapper extends BaseMapper<ProductPo> {

    /**
     * 商城商品列表：LEFT JOIN 启用 SKU 价区派生表，支持价区排序（禁止内存排序）。
     *
     * <p>派生表 pr 仅含启用 SKU 的商品；pr.product_id IS NOT NULL 等价于 EXISTS 启用 SKU。
     * sort 通过 MyBatis choose 白名单映射固定 ORDER BY 片段，列名禁止字符串拼接。
     */
    @Select("<script>"
            + "SELECT p.* FROM product_spu p "
            + "LEFT JOIN ("
            + "  SELECT product_id, MIN(sale_price) AS lo, MAX(sale_price) AS hi "
            + "  FROM product_sku WHERE status = 'ENABLED' AND deleted = 0 GROUP BY product_id"
            + ") pr ON pr.product_id = p.id "
            + "WHERE p.status = 'ON_SALE' AND p.deleted = 0 AND pr.product_id IS NOT NULL "
            + "<if test='categoryIds != null and categoryIds.size() > 0'>"
            + "  AND p.category_id IN "
            + "  <foreach collection='categoryIds' item='cid' open='(' separator=',' close=')'>#{cid}</foreach>"
            + "</if>"
            + "<if test='brandIds != null and brandIds.size() > 0'>"
            + "  AND p.brand_id IN "
            + "  <foreach collection='brandIds' item='bid' open='(' separator=',' close=')'>#{bid}</foreach>"
            + "</if>"
            + "<if test='keyword != null and keyword != \"\"'>"
            + "  AND p.product_name LIKE CONCAT('%', #{keyword}, '%')"
            + "</if>"
            + "ORDER BY "
            + "<choose>"
            + "  <when test='sort == \"PRICE_ASC\"'>pr.lo ASC, p.created_at DESC</when>"
            + "  <when test='sort == \"PRICE_DESC\"'>pr.hi DESC, p.created_at DESC</when>"
            + "  <otherwise>p.created_at DESC</otherwise>"
            + "</choose>"
            + "</script>")
    Page<ProductPo> selectMallPage(Page<ProductPo> page,
                                   @Param("categoryIds") List<Long> categoryIds,
                                   @Param("brandIds") List<Long> brandIds,
                                   @Param("keyword") String keyword,
                                   @Param("sort") String sort);
}

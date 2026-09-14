package com.ai.mall.product.infrastructure.persistence.product;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SkuMapper extends BaseMapper<SkuPo> {

    /**
     * 批量聚合商品启用 SKU 价区（CHG-0015）：单条分组 SQL，禁止 N+1。
     * 仅统计 status=ENABLED 且未逻辑删除的 SKU；无启用 SKU 的商品不会出现在结果中。
     *
     * @param productIds 当页商品 ID 集合
     * @return 每个商品一行价区投影
     */
    @Select("<script>"
            + "SELECT product_id AS productId, MIN(sale_price) AS minPrice, MAX(sale_price) AS maxPrice "
            + "FROM product_sku "
            + "WHERE status = 'ENABLED' AND deleted = 0 AND product_id IN "
            + "<foreach collection='productIds' item='pid' open='(' separator=',' close=')'>#{pid}</foreach> "
            + "GROUP BY product_id"
            + "</script>")
    List<PriceRangePo> selectEnabledPriceRanges(@Param("productIds") List<Long> productIds);
}

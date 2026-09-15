package com.ai.mall.product.interfaces.rest.mall.dto;

import com.ai.mall.common.web.annotation.StringId;
import java.util.List;

/**
 * 商城公开分类/品牌 DTO。
 *
 * <p>CHG-0017：所有业务 ID 标注 {@link StringId} 输出字符串；分类树仅含启用节点（禁用父整枝剪枝）。
 */
public final class MallCatalogDtos {

    private MallCatalogDtos() {
    }

    /** 公开分类树节点（仅启用；禁用父节点整枝不返回）。 */
    public record CategoryNode(
            @StringId long id,
            String name,
            int sort,
            List<CategoryNode> children
    ) {
    }

    /** 公开品牌视图（仅 ENABLED）。 */
    public record BrandView(
            @StringId long id,
            String name,
            String logoUrl,
            int sort
    ) {
    }

    /** 品牌分页包装。 */
    public record BrandPageView(List<BrandView> items, long total, int page, int size) {
    }
}

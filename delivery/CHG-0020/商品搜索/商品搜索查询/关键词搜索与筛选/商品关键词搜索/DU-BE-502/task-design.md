# DU Task Design — DU-BE-502

> 层级：实现仓（Repository）侧产物——Expected Implementation 之"怎么做"（DU 技术方案）。
> 权威 DU 划分：外部 story-design.md §5（SSOT）。
> 本 DU 对应 STORY-005-01-02-01（商品关键词搜索）；筛选排序拆分至 DU-BE-511。

## 1. Goal

mall-search 关键词查询主链路：multi_match 关键词、ON_SALE 过滤、分页与无 keyword 浏览态、SearchDTO/Controller；mall-gateway 匿名路由。

## 2. Repository

repo-1（mall-services/mall-search、mall-gateway）

## 3. Scope

- domain.search：SearchDocument（字段冻结见 requirement-design §2.2）、SearchQuery（keyword/page/size，筛选/排序字段由 BE-511 扩展）、SearchPage、SortMode（DEFAULT 先行，其余 BE-511）、ProductSearchItem（productId/productName/mainImage/minPrice/maxPrice/categoryId/categoryName/brandId/brandName）。
- application.search.ProductSearchService：分页归一（trim、page 默认 1/≤1 回退、size 默认 20 上限 100、keyword ≤64）。
- infrastructure.elasticsearch.ElasticsearchProductSearchRepository：
  bool.must[multi_match(productName^3,keywords,brandName,categoryName operator or)]（keyword 空则不加 must）；
  filter：term status=ON_SALE；
  DEFAULT 排序（_score desc,updatedAt desc）；from/size 分页；total hits.total.value。
- interfaces.rest.mall.MallSearchController：GET /api/mall/search/products，UnifyResult 包裹；本地 security permitAll。
- mall-gateway：Path=/api/mall/search/** → lb://mall-search；匿名白名单；/api/internal/** 不新增网关路由。

## 4. Design References

- CHG-0020 requirement-design.md §2.2（SearchDocument 冻结）/§2.3（查询与排序）/§4（跨仓契约）；STORY-005-01-02-01 story-design §1/§2。

## 5. Dependencies

权威表：无。实际前置 DU-BE-501（Client/健康基线）、DU-BE-510（异常口径）、DU-WS-501（ES 运行环境）。

## 6. Implementation Sketch

```
Gateway(permitAll /api/mall/search/**)
 → MallSearchController.search(params)
    → ProductSearchService.normalize()  // 分页/keyword 归一
       → ElasticsearchProductSearchRepository.search(SearchQuery)
          → ElasticsearchClient.search(bool+ON_SALE+defaultSort+page)
             → SearchDocument → ProductSearchItemMapper → SearchPage
    ← UnifyResult<SearchPage<ProductSearchItem>>
异常：ES 异常→DU-BE-510 Advice→B0501
```

## 7. Pseudocode

N/A（查询组装为 ES Java Client Builder 直用，分支简单；价格/排序伪代码在 DU-BE-511）。

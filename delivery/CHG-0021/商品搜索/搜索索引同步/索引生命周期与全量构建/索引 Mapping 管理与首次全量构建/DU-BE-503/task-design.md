# DU Task Design — DU-BE-503

> 层级：实现仓（Repository）侧产物——Expected Implementation 之"怎么做"（DU 技术方案）。
> 权威 DU 划分：外部 story-design.md §5（SSOT）。

## 1. Goal

索引生命周期（幂等建 mall_products_v1+别名）、mall_search V1 两表、mall-product 搜索投影端点（分页/单条，SERVICE）、首轮全量构建（500/批 Bulk）。

## 2. Repository

repo-1（mall-services/mall-search、mall-services/mall-product）

## 3. Scope

- mall-search resources/db/migration/V1__create_search_sync_tables.sql：search_index_rebuild_task、search_sync_failure_record（列/索引见 story-design §1）。
- infrastructure.elasticsearch.SearchIndexLifecycleManager（ApplicationRunner）：exists→create(mapping/settings)→putAlias 全幂等；ES 异常 catch+ERROR 不阻止启动。
- resources:es/product-mapping.json（或 Java DSL 常量）：1 shard/0 replica；字段冻结（mainImage index:false）。
- domain/search + infrastructure.persistence：RebuildTask/FailureRecord 实体与 Mapper。
- application.search.SearchIndexBuildService：分页 500 拉投影 → BulkRequest 写目标物理索引；批次失败抛错带批次信息。
- infrastructure.client.ProductProjectionClient（OpenFeign name=mall-product）：GET /api/internal/products/search-projection?page&size、GET /api/internal/products/{id}/search-projection。
- mall-product：ProductSearchProjectionMapper（ON_SALE 且 EXISTS ENABLED SKU；min/max priceFen 极值）；InternalProductController 两端点（SERVICE 鉴权同既有 internal）。

## 4. Design References

- CHG-0021 requirement-design.md §2.0/§2.1（生命周期/Mapping/投影）；STORY-005-02-01-01 story-design §1–§3；CHG-0020 requirement-design §2.2（字段冻结）。

## 5. Dependencies

权威表：无。实际前置 DU-WS-501、DU-BE-501。

## 6. Implementation Sketch

```
启动: SearchIndexLifecycleManager.run()
  try: exists(v1)?  : create(v1, mapping/settings)
       alias mall_products 存在? : putAlias(v1)
  catch ES异常: log.error（不抛出，进程继续）

全量构建 build(targetIndex):
  page=0; total
  loop: page = projectionClient.fetch(page,500)
        es.bulk(index=targetIndex, docs.map(upsert docId=productId))
        indexed += size; 空页结束
  失败 → 记录/抛带批次号异常

mall-product:
  InternalProductController.searchProjection(page,size) [SERVICE]
    → ProductMapper.selectSearchProjectionPage:
       WHERE p.status='ON_SALE'
         AND EXISTS(SELECT 1 FROM sku s WHERE s.product_id=p.id AND s.status='ENABLED')
       minPrice/maxPrice 聚合 ENABLED SKU
```

## 7. Pseudocode

```
ensureIndex():
  if !es.indices().exists("mall_products_v1"):
     es.indices().create(name, settings(1,0), mapping(PRODUCT_MAPPING))
  aliases = es.indices().getAlias("mall_products")
  if !aliases.containsKey("mall_products_v1"):
     es.indices().putAlias("mall_products_v1","mall_products")

fullBuild(target):
  page=0
  do:
    resp = productClient.searchProjection(page, 500)   // SERVICE token 由内部调用约定注入
    if resp.content: es.bulk(resp.content.map(d => indexOp(target, d.productId, d)))
    indexed += resp.content.size
    page++
  while indexed < resp.total
```

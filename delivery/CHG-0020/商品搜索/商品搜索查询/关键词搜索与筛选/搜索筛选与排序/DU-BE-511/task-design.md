# DU Task Design — DU-BE-511

> 层级：实现仓（Repository）侧产物——Expected Implementation 之"怎么做"（DU 技术方案）。
> 权威 DU 划分：外部 story-design.md §5（SSOT）。
> 本 DU 对应 STORY-005-01-02-02（搜索筛选与排序）；查询主链路见 DU-BE-502，同模块增量发布。

## 1. Goal

在 BE-502 查询链路基础上增量：分类/品牌/价格区间（区间相交）三维过滤、四排序（DEFAULT/PRICE_ASC/PRICE_DESC/NEWEST）、价格与分页参数校验。

## 2. Repository

repo-1（mall-services/mall-search）

## 3. Scope

- domain.search：SearchQuery 扩展 categoryId/brandId/minPriceFen/maxPriceFen/sort；SortMode 增 PRICE_ASC/PRICE_DESC/NEWEST。
- application.search.ProductSearchService：参数归一增量——价格非负且 min≤max（否则 IllegalArgumentException→B0502）、非法 sort 回退 DEFAULT、categoryId/brandId 正整数校验。
- infrastructure.elasticsearch.ElasticsearchProductSearchRepository 增量：
  filter：term categoryId、term brandId、range minPrice lte maxPriceFen 且 range maxPrice gte minPriceFen（区间相交，单边缺失取开边界）；
  sort：PRICE_ASC(minPrice asc,_score)/PRICE_DESC(maxPrice desc,_score)/NEWEST(publishedAt desc 缺失排尾,_score)。
- MallSearchController：新增请求参数绑定（minPriceFen/maxPriceFen/categoryId/brandId/sort），类型绑定失败→400。
- src/test：FilterSortIT（2 类目/3 品牌/多价格段/含未发布造数矩阵）、QueryNormalizeTest。

## 4. Design References

- CHG-0020 requirement-design.md §2.2（价格 long 分/区间相交）/§2.3（排序口径）；STORY-005-01-02-02 story-design §1/§2/§4。

## 5. Dependencies

权威表：无。实际为 DU-BE-502 同模块增量，前置 DU-BE-501/510 基线。

## 6. Implementation Sketch

```
MallSearchController.search(+categoryId,+brandId,+minPriceFen,+maxPriceFen,+sort)
 → ProductSearchService.normalize()  // 价格非负/min≤max；非法 sort→DEFAULT
    → Repository bool 追加 filter(term categoryId/brandId, range 区间相交)
       sortOf(mode): price_asc/price_desc/newest(missing=_last)
```

## 7. Pseudocode

```
normalize(q):
  if q.minPriceFen != null && q.minPriceFen < 0: throw IllegalArgument
  if q.maxPriceFen != null && q.maxPriceFen < 0: throw IllegalArgument
  if both && q.minPriceFen > q.maxPriceFen:      throw IllegalArgument
  q.mode = SortMode.from(q.sort) ?: DEFAULT      // 非法值静默回退
build(b, q):
  if q.categoryId: b.filter(term("categoryId", q.categoryId))
  if q.brandId:    b.filter(term("brandId", q.brandId))
  if q.minPriceFen!=null: b.filter(range("minPrice").lte(q.maxPriceFen ?? LONG_MAX))
  if q.maxPriceFen!=null: b.filter(range("maxPrice").gte(q.minPriceFen ?? 0))
sortOf(mode):
  PRICE_ASC  -> [minPrice asc, _score desc]
  PRICE_DESC -> [maxPrice desc, _score desc]
  NEWEST     -> [publishedAt desc(missing=_last), _score desc]
  DEFAULT    -> [_score desc, updatedAt desc]
```

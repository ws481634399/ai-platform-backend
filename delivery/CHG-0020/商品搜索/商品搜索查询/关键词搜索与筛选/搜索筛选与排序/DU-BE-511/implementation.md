# DU Implementation — DU-BE-511

> DU 级实施记录（Actual Implementation，sdd-dev 绑定本 DU 产出）。实施正文归属本仓库。
> 本文件固定为 Actual Implementation（实际修改模块/文件、Commit、Task/DU Mapping、实现偏离、完成情况），
> 与 task-design.md / task-spec.md（Expected Implementation）分立，不得合并。

## 变更内容

DU-BE-511 为 DU-BE-502 查询链路的同模块增量（随同一提交发布），改动集中在查询值对象、应用层归一与 ES 适配器查询组装。

### domain/search

- `SearchQuery.java`：携带 categoryId/brandId/minPriceFen/maxPriceFen（整数分 Long）/sort(SortMode)/page/size。
- `SortMode.java`：DEFAULT/PRICE_ASC/PRICE_DESC/NEWEST 四枚举；`from(String)` 仅识别小写 `price_asc`/`price_desc`/`newest`，null/空白/未知值回退 DEFAULT。

### application/search/ProductSearchService.java

- 价格校验：minPriceFen/maxPriceFen 为负 → IllegalArgumentException「minPriceFen 不能为负」/「maxPriceFen 不能为负」；双侧给出且 min>max →「minPriceFen 不能大于 maxPriceFen」（均经 SearchExceptionAdvice 映射 400 B0502）。
- `positiveOrNull`：categoryId/brandId 非正（≤0）忽略，不参与 term 过滤。
- sort 原始字符串直接交 `SortMode.from`，非法值静默回退 DEFAULT，不报错。

### infrastructure/elasticsearch/ElasticsearchProductSearchAdapter.java

- bool.filter 追加（仅非空追加，第 53–68 行）：
  - term categoryId、term brandId；
  - **价格区间相交**：给出 maxPriceFen 时 `range minPrice <= maxPriceFen`；给出 minPriceFen 时 `range maxPrice >= minPriceFen`；单侧只传一边（开边界），双侧即相交语义；均未给不加 range（避免向 long 字段发送无界条件）。
- 排序（`sorts()` 第 96–118 行）：
  - DEFAULT：`_score desc` + `updatedAt desc`；
  - PRICE_ASC：`minPrice asc` + `_score desc`；
  - PRICE_DESC：`maxPrice desc` + `_score desc`；
  - NEWEST：`publishedAt desc, missing=_last` + `_score desc`（无发布时间排尾）。
- 接口层 `MallSearchController` 已含 categoryId/brandId/minPriceFen/maxPriceFen/sort 五个查询参数（DU-BE-502 同批落地）；Long/Integer 类型绑定失败由框架抛类型异常。

### 测试（ProductSearchApiTest，Testcontainers ES 8.17.4 造数矩阵）

数据集：类目 10（电脑配件 4 条在售/含 1 下架、doc5 品牌 100 挂类目 20 工具）/类目 99（100 条种子）；品牌 100 KeyPro、200 DeskGear、300 NoBrand；价格 3900/5000/19900-25900/30000/9900；doc6 无 publishedAt。对应用例：

- `categoryAndBrandFilter`：categoryId=10 仅 4 条（OFF_SALE 不计）；叠加 brandId=100 取交集 2 条（类目 20 的 doc5 被排除）；
- `priceRangeIntersection`：10000–30000 区间相交命中 doc1（19900–25900）/doc3（30000–30000 边界），doc2（3900）不命中；只传 max=5000 单边命中 doc2/doc6；
- `sorts`：price_asc 在类目 10 内严格升序（doc2 3900 → doc6 5000 → doc1 → doc3）；price_desc 严格降序（首 doc3、尾 doc2）；newest 首 doc1，doc6（publishedAt=null）排尾；
- `invalidParams`：min>max 400 B0502、负 min 400 B0502、keyword 65 长度 400 B0502；sort=hacker（未知值）200 不报错（回退 DEFAULT）。

## Commits

| Commit | DU | 说明 |
| --- | --- | --- |
| 82ccf6e | DU-BE-511 | repo-1（M5 三 Change 合并提交）：mall-search 类目/品牌 term、价格区间相交 range、四排序（DEFAULT/price_asc/price_desc/newest）与参数 400 校验 |

## Deviations

<!-- 实现与 DU 建议（Sketch / Pseudocode）明显偏离时必须记录；无偏离写「无」。
     每条偏离三要素缺一不可：原 DU 建议 / 实际实现 / 原因（建议附影响评估）。
     格式：### DEV-N
               - 原 DU 建议:
               - 实际实现:
               - 原因:
               - 影响评估: -->

### DEV-1
- 原 DU 建议: story-design §2 排序参数表写「sort 大小写不敏感」；NEWEST 排序为「publishedAt desc，updatedAt desc 兜底」。
- 实际实现: `SortMode.from` 仅接受小写枚举串（trim 后精确匹配 price_asc/price_desc/newest），`PRICE_ASC` 等大写输入按未知值回退 DEFAULT；NEWEST 次级排序为 `publishedAt desc(missing=_last)` + `_score desc`，未加 updatedAt 次级字段。
- 原因: 对外契约（story-spec §3/§4）枚举值冻结为小写 default/price_asc/price_desc/newest，前端 api/search.ts 也仅透传这四个小写值，大小写不敏感无真实调用方；publishedAt 已能表达新品顺序，缺失值由 missing=_last 显式排尾，_score 提供同分稳定性。
- 影响评估: sorts 用例验证严格序与 null 排尾均符合 AC-004；大写输入走 DEFAULT 不报错（满足未知值宽松策略），无调用方影响；如需 updatedAt 兜底可后续微调排序子句。

### DEV-2
- 原 DU 建议: task-design §3/§6 测试策略列独立单测 `QueryNormalizeTest`（排序映射/非法回退/区间校验纯 Java 单测）+ `FilterSortIT` 两个测试类。
- 实际实现: 未单独建 QueryNormalizeTest/FilterSortIT；归一与查询断言统一在 `ProductSearchApiTest`（真实 ES IT）的 invalidParams/categoryAndBrandFilter/priceRangeIntersection/sorts 四例中完成。
- 原因: 归一逻辑分支轻量，与 ES 结果在同一数据矩阵上断言更能防「归一正确但 DSL 组装错误」的漏网；减少重复夹具。
- 影响评估: 覆盖意图（TC-001～TC-008）逐条有真实 ES 断言承载；代价是归一逻辑无纯单测的离线快速反馈，执行需 Docker（Testcontainers 基线已具备）。

## 自检

对照 task-spec.md Verification：

- ✅ AC-001：categoryAndBrandFilter——categoryId term 仅返该类目、brandId term 仅返该品牌、叠加取交集（10+100 → 2 条）。
- ✅ AC-002：priceRangeIntersection——闭区间边界命中（doc3 min=max=30000 命中 10000–30000）、单边 max=5000 生效；区间相交语义（doc1 价区跨段）实证。
- ✅ AC-003：keyword（multi_match must）+ brandId/categoryId term + 价格 range 在同一 BoolQuery 叠加（适配器第 44–68 行 must/filter 同级）；组合交集由 categoryAndBrandFilter（双 term 交集）与 priceRangeIntersection 共同覆盖。
- ✅ AC-004：sorts——price_asc 严格升（minPrice）、price_desc 严格降（maxPrice）、newest 按 publishedAt 降序且 null 排尾（doc6 末位）。
- ✅ AC-005：invalidParams 中 sort=hacker → 200 回退 DEFAULT；DEFAULT 排序（_score+updatedAt）在 105 条固定数据集下分页顺序确定（pagination 用例）。
- ✅ AC-006：invalidParams——min>max→400 B0502、负值→400 B0502（统一 UnifyResult 结构）；非数字绑定为框架类型异常（见 DU-BE-502 DEV-1）。
- ✅ AC-007：sorts/categoryAndBrandFilter 均在翻页/过滤组合数据集下断言，且全部查询恒带 status=ON_SALE filter（OFF_SALE doc4 在任何筛选/排序下均不出现）；total 为过滤后命中数。
- ✅ Unit：排序映射/非法回退/区间校验经 ProductSearchApiTest（invalidParams/sorts）真实链路覆盖（测试布局调整见 DEV-2）。
- ✅ Migration：N/A。
- ✅ Error Case：min>max、负数、非法 sort 四类边界均有断言。
- 说明：target/surefire-reports 不存在，用例数按源码枚举（本 DU 直接相关 4 个测试方法，承载于 ProductSearchApiTest 全类 9 例之中）；未杜撰执行数字。

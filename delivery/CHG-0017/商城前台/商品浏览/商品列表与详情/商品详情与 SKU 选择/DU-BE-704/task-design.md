# DU Task Design — DU-BE-704

> 层级：实现仓（Repository）侧产物——Expected Implementation 之"怎么做"（DU 技术方案）。
> 权威 DU 划分：外部 story-design.md §5（SSOT）。

## 1. Goal

商品详情聚合：图集、brandName、categoryPath、富文本、specDimensions（规格维度归并）、skuIndex（组合→SKU 索引），可售/不存在 404 口径，含防御性装配。

## 2. Repository

repo-1（mall-product 8103）

## 3. Scope

- interfaces `GET /api/mall/products/{id}`（公开）；不存在 → 404 PRODUCT_NOT_FOUND；status≠ON_SALE 或无启用 SKU → 404 NOT_SALABLE（商品页统一 404 口径，不泄露存在性差异给前台，错误码可区分但前端都渲 404 页）。
- DetailAssembler：product 主信息 + image 表（图集排序）+ brand 名 + category 路径（沿 parent_id 上溯 ≤3 拼 categoryPath[{id,name}]）+ detail 富文本 + SKU 矩阵。
- specDimensions：从启用 SKU 的 spec JSON（如 {"颜色":"红","尺码":"L"}）归并：维度名首次出现序（dimensionsOrder），每维 values 去重保序（值出现序）；skuIndex：键 = 按维度顺序拼 value 的组合键（如 "红|L"）→ {skuId(字符串), priceFen, imageUrl, status}。
- 防御：组合键冲突（脏数据：两个启用 SKU 同规格）→ 取 skuId 较小一条并记 warn，不抛 500；缺规格值的 SKU 跳过矩阵但保留在 sku 列表（不进选择器）。

## 4. Design References

- CHG-0017 requirement-design.md §2.4（详情矩阵装配）、§4（详情契约）；STORY-003-02-02-02 story-design.md §1/§2/§4。

## 5. Dependencies

权威表：无。实际依赖 DU-BE-501（@StringId/白名单）；三态展示数据由 DU-BE-705 提供（前端跨 Story 依赖）。

## 6. Implementation Sketch

- 装配顺序：product → 并行取 brand/category/images/skus（同库顺序查询即可）→ 任一缺失按部分数据策略（brand 名缺失给 null，不 500；分类路径断链给已上溯段）。
- 归并一遍扫描：LinkedHashMap<String,LinkedHashSet<String>> dimensions；遍历 SKU specs 填充；同时建 skuIndex map。
- 价格整数分；富文本字段原样透传（前端 v-html 需净化：由前端做 sanitize，后端存运营受信内容 M3）。
- 禁用/下架 SKU：status 透传进 skuIndex（供前端禁用组合），矩阵维度值仍展示但组合命中 disabled 时不可选。

## 7. Pseudocode

N/A（metadata `pseudocode: false`）。矩阵为 map 归并直线算法，§6 已明确冲突防御。

# DU Task Design — DU-BE-702

> 层级：实现仓（Repository）侧产物——Expected Implementation 之"怎么做"（DU 技术方案）。
> 权威 DU 划分：外部 story-design.md §5（SSOT）。

## 1. Goal

公开分类树与品牌查询：GET /api/mall/categories/tree（禁用父整枝剪枝）、GET /api/mall/brands（启用过滤、keyword、size≤200），匿名公开。

## 2. Repository

repo-1（mall-product 8103 + mall-gateway）

## 3. Scope

- interfaces 公开 Controller；CategoryAppService.tree()：一次查全部 ENABLED 分类 → 内存建树（深度 ≤3 既有数据约束）→ 剪枝（父禁用则整枝剔除，即使子启用）→ 按 sort 排序；节点字段 id(字符串)/name/iconImageUrl/children。
- BrandAppService.list(keyword, page, size)：仅 status=ENABLED；keyword 对 name 模糊（LIKE，参数化）；size 收敛 min(size,200)，默认值；id 字符串。
- 网关白名单：/api/mall/categories/tree、/api/mall/brands permitAll；/api/internal/** denyAll 沿用 DU-BE-501。
- 空结果返 [] 非 null。

## 4. Design References

- CHG-0017 requirement-design.md §2.2（树剪枝）、§4（契约）；STORY-003-02-01-02 story-design.md §1/§2/§4。

## 5. Dependencies

权威表：无。实际依赖 DU-BE-501（白名单/denyAll/@StringId）。

## 6. Implementation Sketch

- 剪枝自顶向下：先按 parent 关系建全量 ENABLED 节点 map；从根（parent_id IS NULL 且启用）开始 DFS，禁用节点不入树且不递归其子（子即使 ENABLED 也随枝剪掉）。
- brand 空 keyword 不加 LIKE 条件；分页用 MyBatis-Plus Page（product 服务分页插件已在 M0 具备）。
- 防御：keyword 转义 %/_（LIKE ESCAPE）防通配注入；SQL 全部参数化。

## 7. Pseudocode

N/A（metadata `pseudocode: false`）。建树/剪枝为常规 DFS，§6 已述。

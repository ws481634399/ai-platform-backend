# DU Task Spec — DU-BE-303

> 层级：实现仓（Repository）侧产物——Expected Implementation 之"做什么/验收"（DU 契约与验收）。
> 权威 DU 划分：外部 requirement-design.md §6（SSOT）；本文件只按 DU id DU-BE-303 引用该表，不新造 DU。
> 互链：通过 DU id DU-BE-303 与同目录 task-design.md（"怎么做"）配对互链。
> verifies 约定：任务项绑定 TC-NNN（红绿灯对象）；TC 定义在外部对应 Story 的 test-design.md，本文件只引用不新造。

## 0. 元信息

- DU id: DU-BE-303
- Change ID: CHG-0010
- Feature Path: 商品与库存/分类与品牌管理/品牌管理/品牌管理
- 权威来源: requirement-design.md §6 / DU-BE-303

## 任务清单

- [ ] 任务 1 — V1 迁移追加 product_brand 段（name VARCHAR64 全局唯一、logo VARCHAR512 可空、description VARCHAR255、sort、status、审计列、uk_product_brand_name、idx_sort）（verifies: TC-001）
- [ ] 任务 2 — domain/brand：Brand 聚合（create/updateProfile/disable/enable，trim/非空/长度内聚）、BrandRepository 端口（existsByName/page）、BrandException 与错误码（verifies: TC-002, TC-003, TC-006）
- [ ] 任务 3 — infrastructure：BrandPO、BrandMapper（LambdaQuery 分页/模糊/状态过滤/排序）、BrandRepositoryImpl、MybatisPlusConfig 分页插件（verifies: TC-005）
- [ ] 任务 4 — application：BrandApplicationService 创建/更新/启停/分页，同名预判与 DuplicateKeyException 转换，分页参数归一（verifies: TC-002, TC-003, TC-005, TC-009）
- [ ] 任务 5 — interfaces：BrandAdminController + DTO，五端点 @PreAuthorize('product:brand:*')，PageView 响应（verifies: TC-001, TC-004, TC-005, TC-007）
- [ ] 任务 6 — 测试：领域单测、API 集成测试（分页/搜索/冲突/403）、并发同名恰一成功测试（verifies: TC-001, TC-002, TC-003, TC-004, TC-005, TC-006, TC-007, TC-009）

## Acceptance Criteria

- [ ] AC-001 — POST 合法品牌成功并可在列表查询到，默认 status=ENABLED、sort=0。
- [ ] AC-002 — 同名（含 trim 差异、大小写差异）创建返回业务错误且无落库。
- [ ] AC-003 — 改名撞名返回业务错误且原值不变；改成未占用名称成功。
- [ ] AC-004 — logo/description/sort 可修改并持久化；非法 logo 长度/形态被校验拦截。
- [ ] AC-005 — 分页返回 records/total/page/size 正确，keyword 与 status 过滤、sort,id 排序生效，分页参数越界被归一/截断。
- [ ] AC-006 — 禁用后数据仍在、列表可见；启用恢复状态。
- [ ] AC-007 — 无 product:brand:* 权限调用各端点返回 403 且无写入；有权限 200。
- [ ] AC-009 — 并发同名创建恰一成功，另一返回名称冲突，库中仅一条。

## 执行顺序（Execution Order）

1. 任务 1 → 任务 2 → 任务 3 → 任务 4 → 任务 5
2. 任务 6 随任务 2~5 同步迭代，最终全量执行
3. 前置：repo-1 必须已合入 DU-BE-302（安全/权限/路由）

## 并行度（Parallelization）

任务 1 与任务 2 可并行；任务 3 之后串行。

## Verification

- Unit: `mvn -pl mall-services/mall-product test -Dtest='com.ai.mall.product.domain.brand.**'`。
- Integration: `mvn -pl mall-services/mall-product verify`（分页 SQL、唯一冲突转换、安全切片 403/200）。
- API: MockMvc 对五端点契约断言；PageView 字段与 UnifyResult 包裹结构。
- Migration: 干净 mall_product 库 V1（含 brand 段）执行成功；uk_product_brand_name 存在性验证。
- Error Case: 同名冲突、logo 非法、分页越界、并发插入冲突、401/403 JSON。

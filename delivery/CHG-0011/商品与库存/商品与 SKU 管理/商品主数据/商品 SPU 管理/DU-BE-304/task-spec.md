# DU Task Spec — DU-BE-304

> 权威 DU 划分：story-design.md §5 / DU-BE-304
> verifies 绑定 TC-NNN（定义在 Story test-design.md）

## 0. 元信息

- DU id: DU-BE-304
- Change ID: CHG-0011
- Feature Path: 商品与库存/商品与 SKU 管理/商品主数据/商品 SPU 管理
- 权威来源: story-design.md §5 / DU-BE-304

## 任务清单

- [ ] 任务 1 — mall-identity V3 注册 product:product:* 权限码与商品菜单（verifies: TC-008）
- [ ] 任务 2 — Flyway V2 建 product_spu/product_image/product_attribute 表（verifies: TC-005）
- [ ] 任务 3 — Product 聚合根与值对象（ProductImage/ProductAttribute/ProductStatus）、领域事件（verifies: TC-009, TC-010）
- [ ] 任务 4 — ProductRepository 端口与 MyBatis-Plus 实现（verifies: TC-001）
- [ ] 任务 5 — ProductAdminAppService create/update/changeStatus/get/page（verifies: TC-001, TC-002, TC-004, TC-007）
- [ ] 任务 6 — ProductAdminController REST 与权限注解（verifies: TC-008）

## Acceptance Criteria

- [ ] AC-001 — 合法 Product 创建成功，status=DRAFT
- [ ] AC-002 — 无效分类/品牌拒绝
- [ ] AC-007 — 主图唯一，图集可多
- [ ] AC-008 — 修改后查询一致
- [ ] AC-009 — product_spu 无 stock 字段
- [ ] AC-010 — 创建即 DRAFT，四态枚举
- [ ] AC-012 — 属性键值对维护
- [ ] AC-013 — 无权限 403
- [ ] AC-014 — 领域事件注册
- [ ] AC-015 — DISABLED 不可发布

## 执行顺序

1. 任务 1 → 2 → 3 → 4 → 5 → 6

## 并行度

无（串行，依赖链明确）

## Verification

- Unit: Product 聚合行为测试（domain.product.*Test）
- Integration: ProductAdminAppServiceTest（@SpringBootTest + H2）
- API: ProductAdminControllerTest（MockMvc + 安全切片）
- Migration: V2/V3 执行成功，表结构断言
- Error Case: 无效引用、product_code 重复、权限 403

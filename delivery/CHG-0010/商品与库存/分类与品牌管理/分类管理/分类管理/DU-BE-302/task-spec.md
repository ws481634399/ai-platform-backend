# DU Task Spec — DU-BE-302

> 层级：实现仓（Repository）侧产物——Expected Implementation 之"做什么/验收"（DU 契约与验收）。
> 权威 DU 划分：外部 requirement-design.md §6（SSOT）；本文件只按 DU id DU-BE-302 引用该表，不新造 DU。
> 互链：通过 DU id DU-BE-302 与同目录 task-design.md（"怎么做"）配对互链。
> verifies 约定：任务项绑定 TC-NNN（红绿灯对象）；TC 定义在外部对应 Story 的 test-design.md，本文件只引用不新造。

## 0. 元信息

- DU id: DU-BE-302
- Change ID: CHG-0010
- Feature Path: 商品与库存/分类与品牌管理/分类管理/分类管理
- 权威来源: requirement-design.md §6 / DU-BE-302

## 任务清单

- [ ] 任务 1 — mall-identity Flyway V2：插入 8 权限码（product:category:{list,create,update,disable}、product:brand:{list,create,update,disable}，type/API pattern/http_method 与 V1 既有行一致）、3 菜单（商品管理目录/分类/品牌页面，component_key 与前端 registry 对齐）、超管角色授权（按 role code 子查询定位，可重复执行）（verifies: TC-010）
- [ ] 任务 2 — mall-product 接入安全：pom 增 mall-common-security；ProductSecurityConfiguration（JWT 解码 + JwtSubjectConverter + /api/admin/\*\* hasRole ADMIN + 401/403 JSON + @EnableMethodSecurity）；application.yml jwt 配置（verifies: TC-010）
- [ ] 任务 3 — mall-gateway 静态路由：/api/admin/categories/** 与 /api/admin/brands/** → mall-product，uri 走可覆写配置项（默认 lb://，本地 profile http://localhost:8103）（verifies: TC-010）
- [ ] 任务 4 — Flyway V1 product_category 段建表（parent_id NOT NULL DEFAULT 0、level、sort、status、uk_parent_name、idx_parent_sort、审计列）（verifies: TC-001）
- [ ] 任务 5 — domain/category 聚合与端口：Category（create/rename/move/disable/enable + 六类不变量）、CategoryLevel(MAX=3,ROOT=0)、CategoryRepository、CategoryException 与错误码（verifies: TC-002, TC-003, TC-004, TC-012）
- [ ] 任务 6 — infrastructure 持久化：CategoryPO、CategoryMapper、CategoryRepositoryImpl（含 existsSiblingName/loadSubTree/shiftSubtreeLevels）、CategoryTreeAssembler（verifies: TC-007, TC-009）
- [ ] 任务 7 — application 服务：CategoryApplicationService 编排创建/更新移动/启停/树查询，@Transactional，DuplicateKeyException 转换（verifies: TC-005, TC-006, TC-008）
- [ ] 任务 8 — interfaces REST：CategoryAdminController + DTO（validation），五个端点与 @PreAuthorize 权限码，UnifyResult 包裹（verifies: TC-001, TC-007, TC-009, TC-010）
- [ ] 任务 9 — 测试：领域参数化单测 + API 集成测试（含安全切片 403/200、错误路径、树结构与排序断言）（verifies: TC-001, TC-002, TC-003, TC-004, TC-005, TC-006, TC-007, TC-008, TC-009, TC-010, TC-012）

## Acceptance Criteria

- [ ] AC-001 — 合法一级分类 POST 返回成功，GET tree 出现 level=1、parentId=0 节点。
- [ ] AC-002 — 可连续建至第三级；第四级返回业务错误且无落库。
- [ ] AC-003 — parentId 指向自身返回业务错误，原数据不变。
- [ ] AC-004 — 把祖先挂到自己后代下返回循环错误；合法移动成功且子树 level 正确重算。
- [ ] AC-005 — parentId 不存在返回"父分类不存在"。
- [ ] AC-006 — 禁用父下新增/移入子分类被拒，启用父下成功。
- [ ] AC-007 — tree 全量返回多层嵌套（含禁用节点及 status），结构与请求数据一致。
- [ ] AC-008 — 禁用分类不级联子分类；启用后恢复可选。
- [ ] AC-009 — 同级次序按 sort 升序、相同 sort 按 id 升序。
- [ ] AC-010 — 无对应权限码身份调用返回 403 且无写入；有权限返回 200；权限码已在 RBAC 注册。
- [ ] AC-012 — 六类不变量均有自动化测试且全部通过。

## 执行顺序（Execution Order）

1. 任务 1 → 任务 2 → 任务 3（公共前置先行，可启动应用验证鉴权链路）
2. 任务 4 → 任务 5 → 任务 6 → 任务 7 → 任务 8
3. 任务 9 与任务 5~8 同步迭代（测试先行/随实现补齐），最终全量执行

## 并行度（Parallelization）

- 任务 1/2/3 可在公共前置确认后并行编写，但合并前需联调一次。
- 任务 4 与任务 5（表结构与领域模型）可并行；其余串行。

## Verification

- Unit: `mvn -pl mall-services/mall-product test`（domain/category 聚合参数化单测；无 Spring 依赖）。
- Integration: `mvn -pl mall-services/mall-product verify`（Spring + 测试库基线；Controller→Mapper 全链路；安全切片 authorities 403/200）。
- API: MockMvc 对五端点契约断言（路径/方法/权限码/UnifyResult 结构/错误码）。
- Migration: Flyway 在干净 mall_product 库执行 V1 成功；mall_identity 库执行 V2 成功且权限/菜单行数正确、脚本可重复执行不报错。
- Error Case: 六类不变量错误路径、DuplicateKeyException 转换、401（无 token）/403（无权限）JSON 形态。

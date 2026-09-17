# DU Task Spec — DU-BE-901

> 层级：实现仓（Repository）侧产物——Expected Implementation 之"做什么/验收"（DU 契约与验收）。
> 权威 DU 划分：外部 story-design.md §5（SSOT）；本文件只按 DU id DU-BE-901 引用该表，不新造 DU。

## 0. 元信息

- DU id: DU-BE-901
- Change ID: CHG-0019
- Feature Path: 订单交易/订单预览与创建/订单预览与创建/订单预览
- 权威来源: story-design.md §5 / DU-BE-901

## 任务清单

- [ ] 任务 1 — mall-order 模块骨架（pom：web/security/oauth2-resource-server/redis/mybatis-plus/flyway；启动类；application.yml port 8105、mall_order 数据源、JWT、mall.order.*-uri）（verifies: TC-012）
- [ ] 任务 2 — OrderSecurityConfiguration 三角色矩阵 + ApiTestSecurityConfig（仿 mall-cart）（verifies: TC-009, TC-012）
- [ ] 任务 3 — 四下游端口与 RestClient 实现（product skus/batch、inventory availability、cart selected-items、member address 单个/列表），UnifyResult 解包/503/ID 字符串 parseLong（verifies: TC-008）
- [ ] 任务 4 — mall-member 新增 `GET /api/internal/members/{memberId}/addresses/{addressId}` + repository findByIdForMember，401/404（verifies: TC-009）
- [ ] 任务 5 — 领域：Money（整数分不变量）、PreviewItem 聚合结果、stockStatus/issueCodes 判定（verifies: TC-010, TC-011）
- [ ] 任务 6 — CheckoutPreviewService：CART/BUY_NOW 编排（CART 强制选中项、忽略 body items）、双状态/库存/地址校验、金额服务端计算、可下单签发 submitToken（verifies: TC-001~007）
- [ ] 任务 7 — SubmitTokenStore Redis 实现（Lua GETDEL 消费、TTL 600、指纹载荷）（verifies: TC-007）
- [ ] 任务 8 — MemberOrderController POST /preview + PreviewView assembler（ID 字符串、Fen 字段）（verifies: TC-001~006）
- [ ] 任务 9 — 依赖失败 503 ORDER_DEPENDENCY_UNAVAILABLE 归一（verifies: TC-008）

## Acceptance Criteria

- [ ] AC-001 — CART 预览逐行服务端重算商品快照（名称/图/规格/价），伪造 items 被忽略；本接口不锁库存不建单。
- [ ] AC-002 — BUY_NOW 只预览传入行；非法数量/source/行数 → 400。
- [ ] AC-003 — 商品下架/SKU 禁用/不可识别 → issueCodes 且 availableToSubmit=false。
- [ ] AC-004 — 库存不足 OUT_OF_STOCK、紧张 LOW、充足 OK；不足时不可下单。
- [ ] AC-005 — 金额一律服务端 product 单价重算，与前端是否传价无关。
- [ ] AC-006 — addressId 为空/非归属/不存在 → 不可下单；有效返回收货快照。
- [ ] AC-007 — 可下单返 submitToken（Redis 600s + 指纹载荷）；不可下单不签发。
- [ ] AC-008 — 下游不可用 → 503 ORDER_DEPENDENCY_UNAVAILABLE。
- [ ] AC-009 — 401/403/内部端点凭证；member 地址内部端点非归属 404。
- [ ] AC-010 — 金额整数分 Long 与不变量；上下文 smoke 绿。

## 执行顺序（Execution Order）

1. 任务 1 → 2 → 3/4 → 5 → 6/7 → 8 → 9。

## 并行度（Parallelization）

任务 4（member 端点）与任务 3/5 可并行。

## Verification

- Unit: Money 不变量、assembler 矩阵（Test）；命令 `mvn -pl mall-services/mall-order test`。
- Integration: Redis Testcontainers 验 token 写入/TTL/GETDEL；端口用 MockRestServiceServer 编排下游。
- API: MockMvc 验 200/400/401/403/404/503 契约；断言 preview 无 lock/无订单写入。
- Migration: N/A（本 DU 无建表，V1 在 DU-BE-902）。
- Error Case: 四下游分别 5xx/连接拒绝 → 503 统一错误码。

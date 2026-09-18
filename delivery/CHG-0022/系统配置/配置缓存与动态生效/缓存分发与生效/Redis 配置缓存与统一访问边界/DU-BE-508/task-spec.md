# DU Task Spec — DU-BE-508

> 层级：实现仓（Repository）侧产物——Expected Implementation 之"做什么/验收"（DU 契约与验收）。
> 权威 DU 划分：外部 story-design.md §5（SSOT）。

## 0. 元信息

- DU id: DU-BE-508
- Change ID: CHG-0022
- Feature Path: 系统配置/配置缓存与动态生效/缓存分发与生效/Redis 配置缓存与统一访问边界
- 权威来源: story-design.md §5 / DU-BE-508

## 任务清单

- [ ] 任务 1 — mall-common-config 模块骨架 + AutoConfiguration imports（verifies: TC-008）
- [ ] 任务 2 — LocalConfigCache（60s/空标记/无 Caffeine）与 ConfigClient（分批/missingKeys）（verifies: TC-004）
- [ ] 任务 3 — FeatureGate/SystemParameterProvider 类型读取与错误类型默认+WARN（verifies: TC-004）
- [ ] 任务 4 — Redis 层 key 规范/600s/负缓存 60s（verifies: TC-001）
- [ ] 任务 5 — InternalConfigController（SERVICE/keys 校验/只返请求键/missingKeys）（verifies: TC-003）
- [ ] 任务 6 — AFTER_COMMIT 单键+聚合键删除与读新值验证（verifies: TC-002）
- [ ] 任务 7 — Redis+HTTP 全失败回退默认不抛；可注入 TTL 验证 60s 上限（verifies: TC-005, TC-006）
- [ ] 任务 8 — 静态门禁：消费服务无 mall_system DAO/数据源（verifies: TC-007）

## Acceptance Criteria

- [ ] AC-001 — 首读后 Redis 规范 key 存在 TTL≈600s；二次读零回源。
- [ ] AC-002 — 更新后单键与聚合键被删；再读得新值。
- [ ] AC-003 — 内部端点鉴权/网关 404；只返请求键；missingKeys 正确。
- [ ] AC-004 — Gate/Provider 可注入且类型读取正确；错误类型默认+WARN。
- [ ] AC-005 — Redis 与 system 均不可用时安全默认不抛不中断。
- [ ] AC-006 — 更新后消费端最长 60s 读到新值（可注入短 TTL 验证）。
- [ ] AC-007 — 消费服务无 mall_system 数据源/DAO；mvn test 全绿。

## 执行顺序（Execution Order）

1. 任务 1→2/4→3→5→6→7→8。

## 并行度（Parallelization）

任务 5/6（mall-system 侧）与 2–4（common 侧）可并行。

## Verification

- Unit: 三级读顺序/回填/负缓存/分批/限速 WARN/故障回退（Fake Redis + WireMock）。
- Integration: testcontainers Redis 真实 TTL 断言；AFTER_COMMIT 删键后再读新值。
- API: MockMvc 401/403/400/200/missingKeys。
- Migration: N/A。
- Error Case: Redis 停、HTTP 5xx、keys 超 100、类型错配。

# DU Task Spec — DU-BE-603

> 层级：实现仓（Repository）侧产物——Expected Implementation 之"做什么/验收"。
> 权威 DU 划分：外部 story-design.md §5（SSOT）。

## 0. 元信息

- DU id: DU-BE-603
- Change ID: CHG-0016
- Feature Path: 商城前台/商城会员/会员资料/会员资料维护
- 权威来源: story-design.md §5 / DU-BE-603

## 任务清单

- [ ] 任务 1 — MemberProfile 领域校验 + GET/PUT /me（主体取 memberId，无入参 ID）（verifies: TC-001, TC-002）
- [ ] 任务 2 — profile 缺失 seed 懒补偿（幂等 upsert，404/401 → 401）（verifies: TC-003）
- [ ] 任务 3 — MinioStorageClient + bucket 幂等 ensure + 头像端点（魔数/大小校验，503 映射）（verifies: TC-004, TC-005）
- [ ] 任务 4 — multipart 限制配置与 2.1MB/伪装 gif 拒绝用例（verifies: TC-005）

## Acceptance Criteria

- [ ] AC-016 — GET /me 返回字符串 memberId/username/昵称/头像/手机/邮箱，无 memberId 入参；删 profile 后 seed 重建且不重复。
- [ ] AC-017 — PUT /me 昵称 1-32 成功；空/超长、手机邮箱格式错 → 400。
- [ ] AC-018 — jpeg/png/webp ≤2MB 上传 200 且可访问、库内更新；非图片/超限 400 且无对象写入；存储故障 503 STORAGE_UNAVAILABLE。

## 执行顺序（Execution Order）

1. 任务 1 → 2 → 3 → 4（2 依赖 DU-BE-601 seed 端点；3 可与 2 并行）。

## 并行度（Parallelization）

任务 2 与任务 3/4 并行。

## Verification

- Unit: 字段校验矩阵；魔数判定；MinIO SDK mock（put 抛异常 → 503）。
- Integration: MinIO Testcontainer（缺失退 mock 留证）；seed 404 → 401；懒补偿重复调用仅一行。
- API: MockMvc multipart 200/400/503 契约。
- Migration: N/A（V1 在 DU-BE-601）。
- Error Case: 存储不可用不产生脏数据；超限与伪装拒绝。

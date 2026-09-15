# DU Implementation — DU-BE-604

> DU 级实施记录（Actual Implementation，sdd-dev 绑定本 DU 产出）。实施正文归属本仓库。
> 本文件固定为 Actual Implementation（实际修改模块/文件、Commit、Task/DU Mapping、实现偏离、完成情况），
> 与 task-design.md / task-spec.md（Expected Implementation）分立，不得合并。

## 变更内容

mall-member（8102）新增收货地址全量能力，覆盖 AC-019~024 / TC-001~010。仅改动 mall-member 一个模块，
新增 1 个迁移 + 8 个主干类 + 4 个测试类（14 个新增文件），无配置改动（网关 /api/mall/shipping-addresses/**
MEMBER 规则与 member 安全链 /api/mall/** 均在 DU-BE-602 预置）。

### 1. 迁移 V2（任务 1，TC-010）

`src/main/resources/db/migration/V2__create_shipping_address.sql` 新建 shipping_address：
id 自增主键、member_id、收货人/手机/省市区/详址/邮编、is_default TINYINT、双时间戳（NOW(6) 维护）；
默认唯一采用生成列技巧——`default_member_flag BIGINT GENERATED ALWAYS AS (CASE WHEN is_default = 1
THEN member_id ELSE NULL END)` + `CONSTRAINT uk_address_default UNIQUE (default_member_flag)`
（多行 NULL 不冲突，等效「每会员至多一条默认」）；INDEX(member_id, is_default, updated_at) 服务列表排序。

### 2. 领域层（任务 2）

- `domain/model/address/ShippingAddress.java`：聚合根。create/reconstitute/revise 三径；
  常量 RECEIVER 32 / REGION 64 / DETAIL 128 / MEMBER_ADDRESS_LIMIT=20；手机 `^1[3-9]\d{9}$`、
  邮编 `^\d{6}$`（null 合法）；非法状态中文 IAE 不允许落库；assignPersistedId 仅仓储 insert 后回填。
- `domain/repository/AddressRepository.java`：端口。单条读/改/删全部 memberId+id 双条件；
  listByMember / countByMember / clearDefault / markDefault 分语句（默认互斥在应用服务编排）。

### 3. 应用层（任务 2/3/4）

- `application/address/AddressErrorCode.java`：B0201 ADDRESS_NOT_FOUND(404)、
  B0202 ADDRESS_LIMIT(409)、B0203 ADDRESS_DEFAULT_CONFLICT(409)，沿用 B 段中文文案。
- `application/address/AddressApplicationService.java`（@Transactional）：
  - create：count ≥20 → 409 B0202；count=0 强制 isDefault；入参 trim、邮编空串归一 null；insert 后重查拿 DB 时间戳；
  - update：先 findByIdForMember 归属加载（零行 404）→ revise → 按双条件 update → 重查；
  - delete：双条件删除，0 行 404；删除默认不自动重选；
  - setDefault：归属加载 → clearDefault → markDefault 同事务；DuplicateKeyException → 409 B0203（回滚无半态）；
  - list 返回 AddressListResult(items, defaultId)；getDefault 空 Optional（接口包 {item:null} 200）。

### 4. 基础设施层

`infrastructure/persistence/address/`：AddressPo（不含生成列，应用永不写）、AddressMapper
（注解 SQL；COLUMNS 复用；insert useGeneratedKeys 回填；updated_at=NOW(6)）、
MyBatisAddressRepository（PO↔聚合，空时间戳防御同 MemberProfile 先例）。

### 5. 接口层

`interfaces/rest/mall/ShippingAddressController.java` + `dto/ShippingAddressDtos.java`：
类级 @PreAuthorize MEMBER；memberId 仅取 SecurityContext subject（复制 DU-BE-603 currentMemberId 模式）。

| 方法 | 路径 | 状态/错误 |
| --- | --- | --- |
| GET | /api/mall/shipping-addresses | 200 {items, defaultId}，默认优先/updated_at DESC |
| POST | 同根 | 201 AddressView；400 校验；409 B0202 |
| PUT | /{id} | 200；404 B0201（不存在/越权同码同文案） |
| DELETE | /{id} | 204；404 |
| PUT | /{id}/default | 200；404；409 B0203 |
| GET | /default | 200 {item:null}（无默认） |

DTO：@NotBlank/@Size/@Pattern Bean Validation；出参 id @StringId 字符串、无 memberId；
createdAt/updatedAt Instant ISO 出参。字面路径 /default 与 /{id} 无 GET 单条端点，无路由歧义。

### 6. Task/AC Mapping

| 任务 | 落地 | TC/AC |
| --- | --- | --- |
| 任务 1 V2 生成列+uk | V2 SQL；ShippingAddressApiTest#migrationGeneratedColumnAndUnique | TC-010 / AC-024 |
| 任务 2 工厂+create | ShippingAddress(18 例)、ServiceTest(5 例)、ApiTest 新增/上限 | TC-001/008/009 / AC-019/024 |
| 任务 3 list+归属 | ApiTest listOrderedAndScoped/updateOthers404/deleteMatrix | TC-002/003/004 / AC-020/021 |
| 任务 4 默认事务 | ApiTest setDefaultClearsOld/deleteDefaultThenEmpty + ConcurrencyTest + ServiceTest | TC-005/006/007 / AC-022/023 |

新增测试 35 例：ShippingAddressTest 18、AddressApplicationServiceTest 5、AddressDefaultConcurrencyTest 1、
ShippingAddressApiTest 11。mall-member 44 → **79/79 全绿**；全仓 `mvn clean package` 24 模块
**311/311**（276 基线 + 35），0 failure/0 error/0 skipped。日志：
`evidence/logs/be-member-test-run1.log`、`evidence/logs/backend-full-package-run1.log`。

## Commits

| Commit | 类型 | 内容 |
| --- | --- | --- |
| aef0f83… | baseline | DU-BE-603 completed 收尾点（本 DU 起点） |
| e6068a1bf813dacf353fd509ab0535563f868230 | feat(member) | V2 迁移 + 地址领域/应用/基础设施/接口 + 35 例测试 |
| （本提交） | docs(sdd) | implementation.md + evidence 四件套 |
| （下一提交） | chore(sdd) | metadata 回填 baseline/result/paths/symbols 置 completed |

## Deviations

### DEV-1 生成列表达式改标准 CASE WHEN 且省略 STORED（H2 MODE=MySQL 兼容）

- 原 DU 建议: task-design/§3 与 story-design §3 DDL：`default_member_flag BIGINT GENERATED ALWAYS AS (IF(is_default=1, member_id, NULL)) STORED`。
- 实际实现: `GENERATED ALWAYS AS (CASE WHEN is_default = 1 THEN member_id ELSE NULL END)`，不写 STORED。
- 原因: 测试库 H2（MODE=MySQL）实测两处不兼容——解析器不接受 MySQL 方言 `IF()` 函数（42001 期望表达式），
  也不接受 `STORED` 关键字（H2 计算列恒为存储式，语法位只允许列约束子句）。
- 影响评估: CASE WHEN 是标准 SQL，MySQL 8 原生支持，语义与 IF 完全等价；省略 STORED 后 MySQL 8 默认
  VIRTUAL，但 UNIQUE 索引作用于虚拟生成列时在二级索引中物化为实体键值，唯一约束保证与 STORED 列一致
  （该列不参与 SELECT/过滤，仅服务 uk）。TC-010 断言列/唯一索引存在并以双默认插入实测冲突，
  MySQL 真实 DDL 归 M3 Test 启动复验。

### DEV-2 detailAddress 上限取 SSOT story 的 128（task-design 误写 200）

- 原 DU 建议: task-design §3 Scope 写「detail ≤200」。
- 实际实现: VARCHAR(128) + 聚合/DTO 1–128 校验。
- 原因: story-spec §3 与 story-design §3 DDL（SSOT，权威 DU 划分来源）均为详细地址 1–128；200 为笔误。
- 影响评估: 与外部 Story 契约一致；前端 DU-FE-603 按 128 对齐。

### DEV-3 set-default 路径采用 PUT /{id}/default（story-design §2）

- 原 DU 建议: story-spec §4 字段规格写 `POST /{id}/set-default`。
- 实际实现: `PUT /api/mall/shipping-addresses/{id}/default`。
- 原因: requirement-design §4 契约清单与 story-design §2 接口契约表（DU 技术权威）均为 PUT /{id}/default，
  且幂等动作（设置状态）语义上 PUT 更准确。
- 影响评估: 网关 DU-BE-602 预置规则为目录级 /api/mall/shipping-addresses/**，两种路径形态都被覆盖；
  前端 DU-FE-603 按 PUT /{id}/default 对接。

### DEV-4 并发 409 的验证拆为「确定性单测 + H2 不变量集成」，MySQL 语句级阻塞留 M3 Test

- 原 DU 建议: test-design TC-006「两线程同时把不同地址设默认 → 其一 409，最终仅一个默认」。
- 实际实现: AddressDefaultConcurrencyTest 用双线程+双闸门在真实 H2 库竞跑（无预置默认，最大化 markDefault
  重叠窗口），断言「最终仅一条 is_default=1」，且任一失败线程异常必须是 BusinessException B0203；
  uk→409 的转译路径另由 AddressApplicationServiceTest#defaultConflictTranslated（Mockito，markDefault
  抛 DuplicateKeyException）确定性验证 InOrder 先清后设。
- 原因: H2 MVCC 对「clearDefault 命中同一旧默认行」存在行锁串行化倾向，竞争可能被引擎排成两个成功事务
  （结果仍唯一），无法在 H2 上稳定复现「其一 409」；MySQL InnoDB 在唯一索引条目上语句级阻塞、
  对手事务提交后即报 duplicate key，转译分支才会高频出现。
- 影响评估: uk 兜底有效性（双默认必被拒）与 409 转译代码均有自动化证据；真实 MySQL 行为差异已登记，
  M3 Test 五集成场景中以真实 MySQL 复跑并发（若 M3 Test 不便造并发，至少复验单库 uk 行为）。

### DEV-5 H2 元数据命名差异：TC-010 按约束名前缀 + 唯一类型断言

- 原 DU 建议: test-design TC-010「生成列与 uk 存在」。
- 实际实现: INFORMATION_SCHEMA.COLUMNS 断言 DEFAULT_MEMBER_FLAG 列；INDEXES 视图以
  `INDEX_TYPE_NAME LIKE '%UNIQUE%'` + `INDEX_NAME LIKE 'UK_ADDRESS_DEFAULT%'` 断言唯一索引；
  再以双默认 INSERT 实测冲突（异常信息含 uk_address_default）+ 第三条非默认 INSERT 成功。
- 原因: H2 2.x INDEXES 视图无 IS_UNIQUE 列（唯一性别在 INDEX_TYPE_NAME='UNIQUE INDEX'），
  且约束支持索引被自动命名为 `<约束名>_INDEX_n`（实测 uk_address_default_INDEX_2）。
- 影响评估: 断言不依赖引擎自生成后缀，MySQL 上（索引名即约束名 uk_address_default）同样可手工核验；
  行为级双默认冲突断言是跨引擎的核心证据。

## 自检

- [x] mvn -pl mall-services/mall-member clean test：79/79（0 failure/0 error/0 skipped）
- [x] mvn clean package：24 模块 BUILD SUCCESS，全仓 311/311
- [x] memberId 无外部入参；越权（不存在/他人）同码 B0201 不枚举
- [x] 生成列不进 PO/INSERT；V2 同 SQL 在 H2 测试库 Flyway 通过
- [x] 真实 MySQL DDL 复验、网关转发与真实浏览器联调登记归 M3 Test

# Red-Green Evidence — DU-BE-601

> 会员注册全链路：identity 账号库 + Outbox-Lite 投递 + member 双幂等建档 + issuer SubjectType 泛化。
> 测试采用测试先行/同步编写；红基线为联调跑通阶段真实失败，修复后两模块转绿并全量回归。

## Red（联调首轮失败基线）

| 模块 | 失败事实 | 根因 |
|---|---|---|
| mall-identity | MemberProvisionRelayTest 3 例中 2 ERROR/1 FAILURE：`IllegalStateException: member event payload deserialization failed`（Caused by `JsonNode.get("memberId")` 为 null 的 NPE）；MemberRegistrationApiTest 主注册用例 outbox 停留 PENDING | H2 MySQL 兼容模式下 VARCHAR 参数写入 JSON 列被当作 JSON 字符串标量，回读为双编码文本 `"{\"eventId\":...}"`，readTree 得到 TextNode；afterCommit 投递全部失败。探针实测回读值留痕于调试过程后已删除（H2JsonProbeTest 临时用例） |
| mall-identity | MemberProvisionRelayTest 事务回滚用例 `SELECT COUNT(*) FROM member_event_outbox` 断言失败；MemberRegistrationApiTest 400 无副作用用例同类计数失败 | 同 mem 库 + Spring context 复用，其他用例提交的 outbox/账号行跨类可见；无条件 COUNT(*) 被污染 |

修复：payload_json 改 VARCHAR(2048)（DEV-1，对齐 product_sku.specification_data 先例）；两处计数改为按本用例 username_norm / JOIN 本会员范围断言。

## Green（转绿结果）

| 模块 | 证据日志 | 命令 | 结果 |
|---|---|---|---|
| mall-member | logs/member-test-green.log | `mvn -pl mall-services/mall-member test` | **10/10**（ProfileProvisionServiceTest 6 + InternalMemberProvisionApiTest 3 + 冒烟 1） |
| mall-identity | logs/identity-test-green.log | `mvn -pl mall-services/mall-identity test` | **67/67**（新增会员 17 + ADMIN/M1 既有 50，零回归） |

## TC → 测试映射（test-design.md 9 个 TC）

| TC | 验证落点（类::方法） | 结果 |
|---|---|---|
| TC-001 注册 201 + memberId 字符串 + 无明文 | identity `MemberRegistrationApiTest::validRegistrationReturnsStringIdAndProvisions`（断言 `data.memberId` textual、响应体不含明文与 password 键、afterCommit 投递被调用） | passed |
| TC-002 大小写变体 409 仅一行 | identity `MemberRegistrationApiTest::duplicateUsernameCaseInsensitiveConflict`（Dup_User/dUp_user，409「用户名已存在」，username_norm 计数=1） | passed |
| TC-003 规则矩阵 400 | identity `MemberRegistrationApiTest::invalidUsernameOrPasswordRejectedWithFieldMessages`（短名/数字开头/非法字符；短密码/纯数字/纯字母 6 组）+ `blankFieldsRejected` + 域层 `MemberAccountTest`（UsernameRules 2 + PasswordRules 4） | passed |
| TC-004 provision 建档 + memberId 一致 + 默认昵称 | member `InternalMemberProvisionApiTest::provisionCreatesProfileWithDefaultNickname`（200、provisioned=true、profile 行、默认昵称「会员」+ 后 6 位）+ `ProfileProvisionServiceTest`（建档内容映射） | passed |
| TC-005 同 eventId 重放仅一行/provisioned=false | member `InternalMemberProvisionApiTest::replayReturnsFalseAndKeepsSingleRow` + `ProfileProvisionServiceTest`（eventId 命中、memberId 已存在、并发唯一冲突三重幂等） | passed |
| TC-006 member 不可用→重试 DONE + seed 种子 | identity `MemberProvisionRelayTest::pendingRetriedUntilDone`（首发失败 PENDING，调度再投 DONE）+ `retryCapKeepsPendingAndStopsRetrying`（20 次上限留 PENDING 不 DLQ）+ `InternalMemberSeedApiTest`（seed 200/404/401）。注：删 profile 后 /me 触发懒补偿的端到端验证随 DU-BE-603（/me 端点所在 DU），见 implementation.md DEV-3 | passed（种子/重试侧） |
| TC-007 outbox 失败事务回滚、同名可复用 | identity `MemberProvisionRelayTest::outboxFailureRollsBackAccountAndNameIsReusable`（spy append 抛错→注册抛出；member_user 与本会员 outbox JOIN 计数均 0；reset spy 后同名注册成功） | passed |
| TC-008 BCrypt 哈希/无明文 | identity `MemberRegistrationApiTest::validRegistration...`（库内 password_hash `$2a$12$` 前缀且不含明文；响应体与 payload_json 均不含 password 键） | passed |
| TC-009 迁移干净库成功 + uk 约束 | 两服务全部 @SpringBootTest 上下文启动即 Flyway 全量 migrate（H2 MySQL 模式）：V7 三表/V1 member_profile 建表成功；uk_member_username_norm 由 TC-002 409 路径实证、uk_profile_event 由 TC-005 重放路径实证、fk_member_refresh_member 随 V7 建表验证 | passed |

跨服务说明：单测/切片中 identity 的 MemberProvisioner 端口以 MockitoBean 替换真实 HTTP（不发网络调用）；契约两侧分别由
identity `MemberProvisionClient` 调用方测试（relay 3 例）与 member `InternalMemberProvisionApiTest`（真实控制器 + 真实安全链）锁定；
真实两服务联调在 M3 Test 集成场景阶段执行。

## Regression（全量回归）

| 证据日志 | 命令 | 结果 |
|---|---|---|
| logs/backend-full-package.log | `mvn clean package`（24 模块，全部测试） | BUILD SUCCESS；13 个测试模块合计 **224 passed，0 failures，0 errors，0 skipped**；全部可执行模块 spring-boot repackage 成功 |

重点回归面：AccessTokenIssuer 签名变更（增 SubjectType 参）影响 ADMIN 登录/刷新全链路——
M1AcceptanceScenariosTest（11）、M1SecurityScenariosTest（5）、M1TokenValidationHttpTest（6）、
TokenPairApplicationService 相关应用测试、product/inventory 内部凭证用例全部通过。

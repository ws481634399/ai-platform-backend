# DU Implementation — DU-BE-601

> DU 级实施记录（Actual Implementation，sdd-dev 绑定本 DU 产出）。实施正文归属本仓库。
> 本文件固定为 Actual Implementation（实际修改模块/文件、Commit、Task/DU Mapping、实现偏离、完成情况），
> 与 task-design.md / task-spec.md（Expected Implementation）分立，不得合并。

## 变更内容

会员注册全链路（CHG-0016 STORY-003-01-01-01），全部落在 repo-1，涉及 mall-identity 与 mall-member 两个服务：

1. **mall-identity 会员账号域与三表迁移（V7）**：
   - 迁移 `V7__create_member_account.sql`：`member_user`（id 自增、username/username_norm、uk_member_username_norm、status、auth_version、password_hash）、`member_refresh_token`（FK member_user，本 DU 仅建表，登录族在 DU-BE-602 接入）、`member_event_outbox`（event_id PK、event_type、member_id、payload_json、status、retry_count、idx_member_outbox_status）。
   - 域模型 `domain.model.member`：`MemberStatus`、`MemberUsername`（record；正则 `[A-Za-z][A-Za-z0-9_]{3,19}`，`norm()` 小写化）、`MemberAccount`（register/reconstitute/ensureCanSignIn）、`MemberRegisteredEvent`（eventId/memberId/username/nickname/occurredAt，create/reconstitute）、`PendingMemberEvent`；`domain.service.MemberPasswordPolicy`（8-32 位且含字母与数字）；端口 `MemberUserRepository`、`MemberEventOutboxRepository`（append/findPending/findPendingByEventId/markDone/advanceRetry）。
2. **注册应用服务 + Outbox-Lite relay**：
   - `application.member.MemberRegistrationService`（@Transactional：用户名规则→密码策略→查重→BCrypt(12)→insert 捕获 DuplicateKey 转 409 UseCaseException「用户名已存在」→outbox append；注册 TransactionSynchronization afterCommit 触发一次 relay 投递，afterCommit 异常被吞不影响注册结果）。
   - `application.member.MemberProvisionRelay`（@Component；MAX_RETRIES=20、BATCH_LIMIT=100；`@Scheduled(fixedDelay=30_000, initialDelay=30_000)` 扫描 PENDING；投递成功 markDone，失败 advanceRetry；达 20 次保留 PENDING 打 ERROR 告警，不建 DLQ）；端口 `application.port.MemberProvisioner`。
   - 基础设施：`infrastructure.persistence.member` 两套 PO/Mapper/Repository（手写 getter/setter + Instant，@Select 用 AS camelCase，insert useGeneratedKeys 回填 id）；`infrastructure.client.MemberProvisionClient`（RestClient base `${mall.member.service-uri:http://localhost:8102}`，默认头 X-Internal-Token，POST /api/internal/members/provision，解析 UnifyResult；success=false/异常→抛错触发重试）；`SchedulingConfiguration`（@EnableScheduling）。outbox 仓储自建 ObjectMapper（JavaTimeModule、ISO-8601），载荷 memberId 显式序列化为字符串。
3. **接口层（identity）**：
   - `interfaces.rest.member.MemberAuthController`：`POST /api/auth/member/register` → 201 `{memberId}` 字符串（@StringId），400 字段校验 / 409 用户名冲突。
   - `interfaces.rest.internal.InternalMemberController`：`GET /api/internal/members/{id}/profile-seed` → `{memberId 字符串, username, status}`；不存在 404「会员不存在」；X-Internal-Token 鉴权（见 DEV-3）。
   - 安全：`JwtConfiguration` 放行 register 路径；`/api/internal/**` hasRole SERVICE 并 addFilterBefore InternalIdentityFilter；控制器不加 @Profile（测试走真实安全链，与 CHG-0015 一致）。
4. **issuer SubjectType 泛化**：`AccessTokenIssuer.issue(long, String, long, SubjectType)`；`RsaAccessTokenIssuer` 参数化 subject_type claim（拒绝 null/GUEST）；ADMIN 两处既有调用补 SubjectType.ADMIN，行为不变（M1 全量回归锁定）。
5. **mall-member 建档侧**：
   - 迁移 `V1__create_member_tables.sql`：`member_profile`（member_id PK、username、nickname、avatar_url、gender DEFAULT UNKNOWN、phone、email、initialized_event_id CHAR36、uk_profile_event）。
   - 域：`domain.model.member.{Gender, MemberProfile}`（provision/reconstitute 工厂）、`DuplicateResourceException`、端口 `MemberProfileRepository`（existsByInitializedEventId/existsByMemberId/add/findByMemberId）。
   - 应用：`application.member.ProfileProvisionService`（@Transactional 双幂等：eventId 命中→false；memberId 已存在→false；insert catch DuplicateResource→重查；默认昵称「会员」+ memberId 后 6 位，包私有 MemberNicknames）。
   - 基础设施：member 持久化三件套；`MemberSecurityConfiguration`（@Profile("!test")，JWT 解码 + JwtSubjectConverter，/api/internal/** hasRole SERVICE + InternalIdentityFilter）；`MybatisPlusConfig`（分页插件，mybatis-plus-jsqlparser）。
   - 接口：`InternalMemberProvisionController` POST /api/internal/members/provision（@Valid），DTO 入参 memberId 为 Long（Jackson 原生吃字符串），响应 `{provisioned:boolean}`。
   - pom 新增 mall-common-security、spring-boot-starter-oauth2-resource-server、mybatis-plus-jsqlparser；两服务 application.yml 增加 mall.security.jwt、internal shared-secret 占位与 mall.member.service-uri。
6. **测试**（新增 26 例，两服务 0 失败）：
   - identity：MemberAccountTest（用户名/密码规则矩阵 6 例）、MemberRegistrationApiTest（5 例：201 字符串 ID+BCrypt+payload 无明文、大小写 409、规则矩阵 400、空白 400、400 无副作用）、MemberProvisionRelayTest（3 例：首次失败重试 DONE、20 次上限留 PENDING、outbox 失败事务回滚且同名可复用）、InternalMemberSeedApiTest（3 例：200/404/401）、ApiTestSecurityConfig（进程内 RSA + InternalIdentityFilter）。
   - member：ProfileProvisionServiceTest（6 例双幂等/默认昵称/异常路径）、InternalMemberProvisionApiTest（3 例：凭证 401、重放 false、默认昵称 +400）。

详细文件清单见 evidence/changeset.md，红/绿与 TC 映射见 evidence/red-green.md。

## Commits

| Commit | 说明 |
|---|---|
| 2f70309a7834513300387327a7e7048ebbea0ab8 | feat(identity,member): 会员注册全链路 outbox-lite 与 provision（DU-BE-601 全部代码/迁移/测试/配置） |

完整 hash/消息对照见 evidence/commits.md。

## Deviations

<!-- 实现与 DU 建议（Sketch / Pseudocode）明显偏离时必须记录；无偏离写「无」。
     每条偏离三要素缺一不可：原 DU 建议 / 实际实现 / 原因（建议附影响评估）。
     格式：### DEV-N
               - 原 DU 建议:
               - 实际实现:
               - 原因:
               - 影响评估: -->

### DEV-1 outbox 载荷列类型 JSON → VARCHAR(2048)

- 原 DU 建议: story-design.md §3 / requirement-design.md §5 DDL：`payload_json JSON NOT NULL`。
- 实际实现: `payload_json VARCHAR(2048) NOT NULL`，迁移注释说明；应用层 ObjectMapper 保证 JSON 合法性。
- 原因: H2（MySQL 兼容模式，项目统一测试库）把 VARCHAR 绑定参数写入 JSON 列时按 JSON 字符串标量处理，回读得到双编码文本（`"{\"eventId\":...}"`），Jackson readTree 得到 TextNode，relay 反序列化必失败；项目既有先例 product_sku.specification_data 即刻意以 VARCHAR 存 JSON 字符串规避同类问题。
- 影响评估: 载荷仅 5 个短字段（<1KB），2048 余量充足；丧失 DB 侧 JSON 类型校验，但载荷只由应用层 ObjectMapper 写入、读取即反序列化，无其他写入方；MySQL 端 VARCHAR 行为与 H2 一致，跨环境统一。已由 MemberProvisionRelayTest（读回→重投递→DONE）与 MemberRegistrationApiTest（payload 内容断言）锁定。

### DEV-2 identity 迁移版本号 V2 → V7

- 原 DU 建议: requirement-design.md §3.1 与 story-design.md 头部均写 `V2__create_member_account.sql`。
- 实际实现: `V7__create_member_account.sql`。
- 原因: DU 编写后 identity 服务已存在 V1~V6 迁移（Flyway 版本号不可复用/插队），V7 为当前唯一合法下一号。
- 影响评估: 仅文件名/版本号差异，DDL 内容与 story-design §3 一致（payload_json 见 DEV-1）；干净库启动迁移成功由全部 @SpringBootTest 上下文加载验证。

### DEV-3 profile-seed 端点归属：member 控制器 → identity 内部控制器

- 原 DU 建议: story-design.md §1 mall-member 段「另在本 Story 提供 GET /api/internal/members/{memberId}/profile-seed」。
- 实际实现: 端点落于 mall-identity `interfaces.rest.internal.InternalMemberController`（3 例 InternalMemberSeedApiTest：200 种子/404 会员不存在/无凭证 401）；mall-member 侧发起懒补偿的 RestClient 调用随 DU-BE-603 的 /me 链路一并接入。
- 原因: requirement-design.md §2 关键组件清单将 InternalMemberController(profile-seed) 列在 mall-identity；种子数据源是 member_user（仅 identity 持有），由 identity 直接出数据避免 member 反向持有账号库访问；契约路径/鉴权/响应体与 story-design §2 表格完全一致。
- 影响评估: 服务间契约不变；TC-006 的「定时重试 DONE」本 DU 已验证，「删 profile 后首次 /me 懒补偿重建」端到端验证随 DU-BE-603（/me 端点所在 DU）补齐，端点本身本 DU 已就绪并锁定。

## 自检

- [x] 全量构建：`mvn clean package` → 24 模块 BUILD SUCCESS，13 个测试模块合计 **224/0/0/0**
      （通过/失败/错误/跳过），日志 evidence/logs/backend-full-package.log。
- [x] 单模块转绿：mall-identity **67/67**（新增 17 例 + M1 ADMIN 链路 50 例零回归），
      mall-member **10/10**（新增 9 例 + 冒烟 1 例），日志 evidence/logs/identity-test-green.log、
      evidence/logs/member-test-green.log。
- [x] issuer 泛化零回归：AccessTokenIssuer 增加 SubjectType 参后，RsaAccessTokenIssuerTest、
      M1TokenValidationHttpTest（6）、M1AcceptanceScenariosTest（11）、M1SecurityScenariosTest（5）
      等全部 ADMIN 认证授权用例通过。
- [x] AC 映射：9 个 TC 与测试类/方法的逐项映射见 evidence/red-green.md（TC-006 懒补偿端到端随 DU-BE-603，
      本 DU 交付种子端点，DEV-3 已记）。
- [x] 安全边界：注册响应与 outbox payload 均无密码字段；库内仅 BCrypt($2a$12$) 哈希；
      internal 端点无 X-Internal-Token 一律 401 INTERNAL_UNAUTHORIZED（两服务各有用例）。
- [x] 幂等/原子性：eventId+memberId 双幂等（6 例应用层测试）；outbox append 失败 member_user 同步回滚、
      同名可立即再注册（MemberProvisionRelayTest 第 3 例）。
- [x] 范围克制：member_refresh_token 仅建表未写逻辑（DU-BE-602）；网关白名单/路由、MinIO、地址表
      均未提前实现。
- [x] 未引入计划外依赖（mall-common-security/oauth2-resource-server/mybatis-plus-jsqlparser
      均在 task-design §3 与 CHG-0015 先例范围内）。

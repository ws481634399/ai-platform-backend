# DU Implementation — DU-BE-602

> DU 级实施记录（Actual Implementation，sdd-dev 绑定本 DU 产出）。实施正文归属本仓库。
> 本文件固定为 Actual Implementation（实际修改模块/文件、Commit、Task/DU Mapping、实现偏离、完成情况），
> 与 task-design.md / task-spec.md（Expected Implementation）分立，不得合并。

## 变更内容

会员登录与会话（CHG-0016 STORY-003-01-01-02），全部落在 repo-1，涉及 mall-identity 与 mall-gateway
两个模块。member_refresh_token 表已在 DU-BE-601 V7 迁移建好（结构与 auth_refresh_token 平行），
本 DU 只写 Java 侧，**无新迁移、不改任何 ADMIN 既有代码**。

1. **会员会话域模型与端口（mall-identity）**：
   - `domain.model.member.MemberRefreshSession`（record：digest/familyId/memberId/authVersion/
     expiresAt/usedAt/revokedAt，`isActive(now)` 三态判定：未旋转未撤销未过期）。
   - 端口 `domain.repository.MemberRefreshSessionRepository`（findByDigest/save/consume/
     revokeFamily/revokeAllForMember）；`MemberUserRepository` 增 `findByUsernameNorm`、
     `incrementAuthVersion`（退出时原子 +1）。
2. **三个会员应用服务（application.member，与 ADMIN 平行、类物理隔离）**：
   - `MemberAuthenticationApplicationService`：用户名 trim 后经 MemberUsername 规则归一查
     username_norm；用户不存在 / 密码不匹配 / 用户名格式非法 / 密码长度越界统一抛
     UseCaseException(UNAUTHORIZED,「用户名或密码错误」)，不可区分账号存在性；密码校验通过后
     `ensureCanSignIn` 失败（DISABLED）抛 FORBIDDEN「账号已禁用」。
   - `MemberRefreshSessionService`：移植 ADMIN family 算法——32 字节 SecureRandom→Base64URL
     明文只出参一次，库内存 SHA-256 hex digest；issue 新建 family；rotate 先校验
     ACTIVE+authVersion，再条件 consume（used_at 为空且未过期的 UPDATE），成功后同 family
     签发新令牌；任何异常路径（已旋转/已撤销/过期/版本陈旧/并发 consume 失败）revokeFamily
     后抛 401；TTL `mall.security.jwt.refresh-ttl:P7D`。
   - `MemberTokenPairApplicationService`：issue 签发 SubjectType.MEMBER 的 access + refresh；
     refresh 先按 digest 加载会话→findById 查账号当前状态（不存在/禁用 → 401）→rotate→按当前
     authVersion 重签 access；`signOut` 单事务撤销该会员全部 refresh + auth_version+1。
     本类不带 `@Profile("!test")`（CHG-0016 会员侧新惯例，测试走真实安全链）。
3. **基础设施持久化**：
   - `infrastructure.persistence.member` 新增 MemberRefreshSessionPo（record）/
     MemberRefreshSessionMapper（文本块 SQL：find AS camelCase、save ON DUPLICATE KEY UPDATE、
     consume 条件 UPDATE、revokeFamily/revokeAllForMember 仅置未撤销行）/
     MyBatisMemberRefreshSessionRepository。
   - MemberUserMapper 增 findByUsernameNorm 与 `auth_version = auth_version + 1` 原子递增；
     MyBatisMemberUserRepository 同步实现，PO→domain 映射收敛为私有 toDomain。
   - **revokeFamily/revokeAllForMember 标注 `@Transactional(REQUIRES_NEW)`**：rotate 在撤销后
     必抛 401，外层事务回滚会连带撤销 UPDATE 回滚（同事务则重放检测形同虚设），独立事务保证
     整族撤销一定落库（见 DEV-1）。
4. **接口层 MemberAuthController 扩展（同文件追加，register 行为不变）**：
   - `POST /api/auth/member/login`（匿名）：200 响应体 `{accessToken, accessExpiresAt,
     refreshToken, memberId}`（refreshToken 在 body 是 story-design §2 商城前端契约；
     memberId @StringId 字符串），同时双发 refresh_token cookie（HttpOnly+Secure+
     SameSite=Strict，Path=/api/auth/member，maxAge 按 refreshExpiresAt 相对时长，与
     AdminAuthController 现状写法对齐）。
   - `POST /api/auth/member/refresh`（匿名）：body.refreshToken 优先、缺省读 cookie；
     旋转成功 200 同构体 + 刷新 cookie；无效/缺失一律 401。
   - `POST /api/auth/member/logout`（MEMBER）：204，撤全部 refresh + auth_version+1 +
     Max-Age=0 清 cookie；非 MEMBER 主体 403、匿名 401。
   - JWT claim：sub=memberId 字符串、subject_type=MEMBER、username、auth_version
     （issuer 已在 DU-BE-601 泛化，本 DU 只传 SubjectType.MEMBER）。
   - UseCaseException.Kind 新增 FORBIDDEN，IdentityExceptionHandler 映射 403（INVALID→400/
     NOT_FOUND→404/CONFLICT→409/UNAUTHORIZED→401 不变）。
5. **生产/测试安全链**：JwtConfiguration（@Profile("!test")）白名单增 login/refresh，
   显式 `/api/auth/member/logout` hasRole MEMBER（置于 /api/admin/** 规则之前）；
   测试支撑 ApiTestSecurityConfig 同步规则。新增测试自动配置
   `support/TestTokenIssuerAutoConfiguration`（test-scope AutoConfiguration.imports 注册，
   @ConditionalOnMissingBean：已有 JwtEncoder 则复用保证与解码器同密钥，否则建进程内 RSA；
   无条件补 AccessTokenIssuer），让不带 profile 开关的会员令牌服务在仓储/relay 等无安全链
   切片中也能装配（见 DEV-2）。
6. **网关 mall-gateway**：
   - `GatewaySecurityConfiguration`：converter 由「仅 ADMIN 发角色」改为 subject_type ∈
     {ADMIN, MEMBER} → ROLE_<TYPE>（未知/缺失不给角色，CHG-0015 MEMBER→admin 403 矩阵
     仍然成立）；白名单增 /api/auth/member/{register,login,refresh}；新增
     `/api/mall/members/**`、`/api/mall/shipping-addresses/**` hasRole MEMBER（与
     /api/admin/** 路径不重叠，双向 403）；内部 denyAll 404 与 products 公开例外不动。
   - application.yml 增两条路由：mall-identity-member（/api/auth/member/** → 8101，
     复用 MALL_GATEWAY_IDENTITY_URI）、mall-member（/api/mall/members/** 与
     /api/mall/shipping-addresses/** → 8102，新增 MALL_GATEWAY_MEMBER_URI 覆写点）。
7. **测试**（新增 17 例，全仓 0 失败 0 回归）：
   - identity：MemberAuthenticationApplicationServiceTest（5 例：大小写正确登录、不存在 401、
     错密码 401 同文案、格式非法/长度越界/null 同文案、DISABLED 403）；MemberSessionApiTest
     （7 例：登录双令牌+cookie 四属性+JWT claim 解码、错密码/不存在统一 401、DISABLED 403、
     旋转+重放整族撤库断言、伪造/缺失 refresh 401、logout 204+清 cookie+auth_version=2+
     全族 revoked+refresh 再用 401、MEMBER JWT 打 /api/admin 403+匿名 logout 401）。
   - gateway：CHG0016GatewaySecurityChainTest（5 例：会员三端点匿名放行、products 公开例外
     保持、会员域匿名 401、MEMBER 200、ADMIN 403、MEMBER→admin 403）；CHG0015 既有 7 例
     零改动全绿。

详细文件清单见 evidence/changeset.md，红/绿与 TC 映射见 evidence/red-green.md。

## Commits

| Commit | 说明 |
|---|---|
| f1367cb5617cae51a3de1c78256f6992915f5fa6 | feat(identity,gateway): 会员登录/刷新/退出双令牌与网关 MEMBER 隔离（DU-BE-602 全部代码/配置/测试） |

## Deviations

<!-- 实现与 DU 建议（Sketch / Pseudocode）明显偏离时必须记录；无偏离写「无」。 -->

### DEV-1 整族撤销改 REQUIRES_NEW 独立事务

- 原 DU 建议: task-design.md 移植基线描述为「直接移植 ADMIN RefreshSessionApplicationService
  的 rotate：异常路径 revokeFamily 后抛 401」，未提事务传播方式。
- 实际实现: MyBatisMemberRefreshSessionRepository 的 revokeFamily/revokeAllForMember 标注
  `@Transactional(propagation = REQUIRES_NEW)`，撤销在挂起外层事务的独立新事务中提交。
- 原因: rotate 与 signOut 均为 @Transactional，方法内 revoke 后必抛 UNAUTHORIZED 运行时异常，
  Spring 默认回滚当前事务——同事务下「整族撤销」会随异常一起回滚（MemberSessionApiTest
  重放后再刷 r2 实测仍 200 暴露此问题；ADMIN 侧仅在无事务的内存仓储单测中验证过撤销，HTTP
  链路存在同样的潜在缺陷，本 DU 不修改 admin 代码）。
- 影响评估: 撤销动作独立持久化，语义反而更贴合「重放检测到即不可反悔地杀死整族」的安全意图；
  consume+签发新令牌仍在同一事务保持原子；signOut 中即便后续 auth_version 递增失败，refresh
  已撤销（更安全的失败方向）。由 MemberSessionApiTest「重放后 r2 也 401 + 库内全行
  revoked_at 非空」锁定。

### DEV-2 测试切片新增全局 AccessTokenIssuer 自动配置

- 原 DU 建议: 调研记录设想「在 ApiTestSecurityConfig 里补一个 AccessTokenIssuer bean」。
- 实际实现: 改为 test-scope 自动配置 `TestTokenIssuerAutoConfiguration`
  （META-INF/spring/...AutoConfiguration.imports），@ConditionalOnMissingBean 兜底
  JwtEncoder 与 AccessTokenIssuer；ApiTestSecurityConfig 不再声明 issuer。
- 原因: 会员令牌服务按 CHG-0016 新惯例不带 @Profile("!test")，所有 @SpringBootTest 上下文
  （MemberProvisionRelayTest、IdentityRepositoryIntegrationTest、M1AuditAppendOnlyTest、
  M1MenuTreeIntegrationTest、M1TokenValidationHttpTest 等）启动时都需要 AccessTokenIssuer；
  逐切片 @Import 或加 bean 会污染全部既有测试类。自动配置在已有安全切片中复用其 JwtEncoder
  （保证与 JwtDecoder 同一密钥对），无安全链切片则自建临时 RSA，两类上下文均装配成功。
- 影响评估: 仅 test classpath 生效，生产包不含该文件；既有 5 个受影响上下文与新会员切片
  全部启动成功，全量 242 例零回归。

### DEV-3 403 语义复用 UseCaseException 新增 FORBIDDEN Kind（而非控制器局部 BusinessException）

- 原 DU 建议: 调研记录设想「Kind 无 FORBIDDEN，可能仿 AdminAuthController.InvalidCredentials
  在控制器抛 BusinessException(HttpStatus.FORBIDDEN)」。
- 实际实现: 在 identity 自有 UseCaseException.Kind 增加枚举值 FORBIDDEN，
  IdentityExceptionHandler switch 增 403 分支；应用层直接抛领域语义异常。
- 原因: 「账号已禁用」是应用服务认证决策而非 HTTP 层关注点，放应用层可被 MemberAuthentication
  单元测试直接断言；该枚举与异常处理器均为 identity 模块内部类型（非 common 共享），新增
  枚举值不影响 ADMIN 与其他服务。
- 影响评估: 既有 INVALID/NOT_FOUND/CONFLICT/UNAUTHORIZED 映射全部不变，M1 用例零回归；
  错误码仍为 BUSINESS_ERROR、文案「账号已禁用」，响应同构体与其他业务错误一致。

## 自检

- [x] 全量构建：`mvn clean package` → 24 模块 BUILD SUCCESS，全部测试模块合计 **242/0/0/0**
      （通过/失败/错误/跳过），日志 evidence/logs/backend-full-package.log。
- [x] 单模块转绿：mall-identity **79/79**（新增 12 例 + 既有 67 例零回归），
      mall-gateway **19/19**（新增 5 例 + 既有 14 例零回归，含 CHG-0015 7 例矩阵）。
- [x] AC 映射：AC-008~AC-013 与 8 个 TC 的逐项映射见 evidence/red-green.md。
- [x] 安全边界：明文 refresh 只出现在登录/刷新响应一次；库内仅 SHA-256 摘要；
      错密码/不存在/格式非法/伪造 refresh 全部同文案同 401，不泄露账号存在性；
      cookie HttpOnly+Secure+SameSite=Strict 且 Path 限定前缀；logout 清 cookie。
- [x] 版本语义：logout 后 auth_version 原子 +1 且全族 refresh revoked_at 落库，
      旧 refresh 再用 401（MemberSessionApiTest 查库断言）。
- [x] 双向隔离：网关 MEMBER→/api/admin 403、ADMIN→/api/mall/members/me 403 均有用例；
      identity 服务内 MEMBER JWT→/api/admin/** 403 由切片再锁一层。
- [x] 范围克制：无新迁移（member_refresh_token 已在 V7）；未改任何 ADMIN 类；
      mall-member 8102 会员域端点（/me、地址）未提前实现，路由仅为后续 DU 预置。
- [x] 未引入计划外依赖（全部为既有 spring-security/mybatis/jwt 组件）。

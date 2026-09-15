# Changeset — DU-BE-602

> 仓库：repo-1（ai-platform-backend），分支 M3-dev。
> 代码提交见 commits.md（feat(identity,gateway): 会员登录/刷新/退出双令牌与网关 MEMBER 隔离）。
> 共 2 个模块、22 个代码/配置/测试文件（13 新增 + 9 修改），无数据库迁移（member_refresh_token
> 已由 DU-BE-601 V7 建好）。下文全量列出。

## 1. mall-identity：主干新增（会员会话域/应用/基础设施）

| 文件 | 变更类型 |
|---|---|
| `mall-services/mall-identity/src/main/java/com/ai/mall/identity/domain/model/member/MemberRefreshSession.java` | 新增（record；isActive 三态判定） |
| `mall-services/mall-identity/src/main/java/com/ai/mall/identity/domain/repository/MemberRefreshSessionRepository.java` | 新增（端口 find/save/consume/revokeFamily/revokeAllForMember） |
| `mall-services/mall-identity/src/main/java/com/ai/mall/identity/application/member/MemberAuthenticationApplicationService.java` | 新增（登录认证；统一 401 文案；DISABLED 403；AuthenticatedMember） |
| `mall-services/mall-identity/src/main/java/com/ai/mall/identity/application/member/MemberRefreshSessionService.java` | 新增（family issue/rotate/inspect/revokeAll；SHA-256 摘要；@Autowired 公有构造器） |
| `mall-services/mall-identity/src/main/java/com/ai/mall/identity/application/member/MemberTokenPairApplicationService.java` | 新增（issue/refresh/signOut；SubjectType.MEMBER；signOut 单事务撤族+版本+1） |
| `mall-services/mall-identity/src/main/java/com/ai/mall/identity/infrastructure/persistence/member/MemberRefreshSessionPo.java` | 新增（record PO） |
| `mall-services/mall-identity/src/main/java/com/ai/mall/identity/infrastructure/persistence/member/MemberRefreshSessionMapper.java` | 新增（find/ON DUPLICATE/条件 consume/revokeFamily/revokeAllForMember） |
| `mall-services/mall-identity/src/main/java/com/ai/mall/identity/infrastructure/persistence/member/MyBatisMemberRefreshSessionRepository.java` | 新增（撤销两方法 REQUIRES_NEW，DEV-1） |

## 2. mall-identity：主干修改

| 文件 | 变更类型 |
|---|---|
| `mall-services/mall-identity/src/main/java/com/ai/mall/identity/domain/repository/MemberUserRepository.java` | 修改（增 findByUsernameNorm、incrementAuthVersion） |
| `mall-services/mall-identity/src/main/java/com/ai/mall/identity/infrastructure/persistence/member/MemberUserMapper.java` | 修改（findByUsernameNorm SELECT；auth_version 原子 UPDATE） |
| `mall-services/mall-identity/src/main/java/com/ai/mall/identity/infrastructure/persistence/member/MyBatisMemberUserRepository.java` | 修改（两方法实现；toDomain 收敛） |
| `mall-services/mall-identity/src/main/java/com/ai/mall/identity/application/exception/UseCaseException.java` | 修改（Kind 增 FORBIDDEN，DEV-3） |
| `mall-services/mall-identity/src/main/java/com/ai/mall/identity/interfaces/rest/IdentityExceptionHandler.java` | 修改（FORBIDDEN→403） |
| `mall-services/mall-identity/src/main/java/com/ai/mall/identity/interfaces/rest/member/MemberAuthController.java` | 修改（追加 login/refresh/logout 三端点与 DTO；register 行为不变） |
| `mall-services/mall-identity/src/main/java/com/ai/mall/identity/infrastructure/config/JwtConfiguration.java` | 修改（白名单 login/refresh；logout hasRole MEMBER） |

## 3. mall-identity：测试

| 文件 | 变更类型 |
|---|---|
| `mall-services/mall-identity/src/test/java/com/ai/mall/identity/application/member/MemberAuthenticationApplicationServiceTest.java` | 新增（5 例；内存 FakeMemberUserRepository + 真实 BCrypt(12)） |
| `mall-services/mall-identity/src/test/java/com/ai/mall/identity/application/member/MemberSessionApiTest.java` | 新增（7 例；真实安全链 + 查库断言，用户名唯一过滤） |
| `mall-services/mall-identity/src/test/java/com/ai/mall/identity/support/TestTokenIssuerAutoConfiguration.java` | 新增（test-scope 自动配置兜底 JwtEncoder/AccessTokenIssuer，DEV-2） |
| `mall-services/mall-identity/src/test/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` | 新增（注册上述自动配置） |
| `mall-services/mall-identity/src/test/java/com/ai/mall/identity/support/ApiTestSecurityConfig.java` | 修改（白名单/hasRole MEMBER 同步；issuer 改由自动配置提供） |

## 4. mall-gateway：主干与配置

| 文件 | 变更类型 |
|---|---|
| `mall-gateway/src/main/java/com/ai/mall/gateway/security/GatewaySecurityConfiguration.java` | 修改（converter 支持 MEMBER→ROLE_MEMBER；会员三端点白名单；会员域 hasRole MEMBER） |
| `mall-gateway/src/main/resources/application.yml` | 修改（mall-identity-member、mall-member 两条路由与 MALL_GATEWAY_MEMBER_URI 覆写点） |
| `mall-gateway/src/test/java/com/ai/mall/gateway/security/CHG0016GatewaySecurityChainTest.java` | 新增（5 例双向隔离矩阵；进程内 RSA + WebTestClient） |

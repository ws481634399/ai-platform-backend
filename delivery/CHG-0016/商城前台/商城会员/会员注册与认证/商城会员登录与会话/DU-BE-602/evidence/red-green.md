# Red-Green Evidence — DU-BE-602

> 会员登录与会话：统一凭据错误 401、DISABLED 403、refresh family 旋转/重放整族撤销、
> logout 撤族+auth_version+1、网关 MEMBER/ADMIN 双向隔离。
> 测试与代码同步编写；红基线为联调真实失败，修复后 identity/gateway 转绿并全量回归。

## Red（联调首轮失败基线）

| 模块 | 失败事实 | 根因 | 修复 |
|---|---|---|---|
| mall-identity | 全部 @SpringBootTest 上下文加载失败：memberRefreshSessionService「No default constructor found」，连带 smoke/registration/seed 等 6 个测试类 threshold 跳过 | 类有两个构造器（Spring 注入公有 + 包私有测试构造器），未声明注入构造器时 Spring 尝试无参构造 | 公有构造器加 @Autowired（对齐 ADMIN RefreshSessionApplicationService） |
| mall-identity | 重放 r1 返回 401 后，同 family 的 r2 再刷仍 **200**，整族撤销未生效 | rotate 为 @Transactional，revokeFamily 后抛 UNAUTHORIZED 运行时异常，撤销 UPDATE 随外层事务一并回滚（ADMIN 仅在无事务内存仓储单测覆盖该路径，HTTP 链路存在同样潜在缺陷，按不改 admin 原则未动） | 仓储 revokeFamily/revokeAllForMember 改 REQUIRES_NEW 独立提交（DEV-1），测试查库断言全行 revoked_at 非空 |
| mall-identity | MemberProvisionRelayTest 等 5 个既有上下文报 NoSuchBeanDefinitionException: AccessTokenIssuer | 会员令牌服务不带 @Profile("!test")，生产 JwtConfiguration 在 test profile 不装配 issuer，非安全切片上下文无该 bean | test-scope 自动配置 TestTokenIssuerAutoConfiguration 兜底（DEV-2），既有上下文零改动 |
| mall-identity | JWT claim auth_version 断言 1 失败（actual 1L）；InternalMemberSeedApiTest 首轮编译中断后 400「parameter name information not available」 | 前者为 Long/Integer 包装类型差异（改断言 1L）；后者为首次编译失败后 javac 输出的无 -parameters 残留 class，clean 后消失（非产品缺陷） | 断言改 1L；以 mvn clean test 复验 |

## Green（转绿结果）

| 模块 | 证据日志 | 命令 | 结果 |
|---|---|---|---|
| mall-identity | logs/identity-test-green.log | `mvn -pl mall-services/mall-identity test` | **79/79**（新增会员会话 12 + DU-BE-601 会员 67 中既有部分与 M1 ADMIN 链路，零回归） |
| mall-gateway | logs/gateway-test-green.log | `mvn -pl mall-gateway test` | **19/19**（新增 5 + CHG-0015 既有 7 + 其余 7，零回归） |

## TC → 测试映射（test-design.md 8 个 TC）

| TC | 验证落点（类::方法） | 结果 |
|---|---|---|
| TC-001 登录 200 双 Token + claim | identity `MemberSessionApiTest::loginIssuesTokenPairAndMemberClaims`（body 四字段、memberId textual、Set-Cookie 含 refresh_token/Path=/api/auth/member/HttpOnly/SameSite=Strict；JwtDecoder 验签后 sub=memberId、subject_type=MEMBER、username、auth_version=1）+ `MemberAuthenticationApplicationServiceTest::validCredentialsReturnAuthenticatedMember`（大小写/前后空格归一） | passed |
| TC-002 错密码/不存在统一文案 | identity `MemberSessionApiTest::invalidCredentialsUnifiedMessage`（两种 401 同文案）+ `MemberAuthenticationApplicationServiceTest::unknownUserGivesUnified401`、`wrongPasswordGivesUnified401`、`malformedInputGivesUnified401`（格式非法/短密码/null 同文案） | passed |
| TC-003 DISABLED 拒绝 | identity `MemberSessionApiTest::disabledAccountRejectedWith403`（库内置 DISABLED → 403「账号已禁用」）+ `MemberAuthenticationApplicationServiceTest::disabledAccountGives403`（Kind.FORBIDDEN） | passed |
| TC-004 refresh 旋转 + 伪造/缺失 401 | identity `MemberSessionApiTest::refreshRotatesAndReplayRevokesFamily`（r2≠r1、a2≠a1、a2 可验签且 sub 一致）+ `invalidRefreshTokenRejected`（伪造 token/空 body 均 401） | passed |
| TC-005 重放整族撤销 | identity `MemberSessionApiTest::refreshRotatesAndReplayRevokesFamily`（r1 重放 401 后 r2 也 401；JOIN member_user 按唯一用户名断言该 family 全部行 revoked_at 非空，总数≥2） | passed |
| TC-006 logout 撤族 + auth_version+1 | identity `MemberSessionApiTest::logoutRevokesFamilyAndBumpsAuthVersion`（204 + Max-Age=0 cookie；auth_version=2；该会员 refresh 行 revoked_at 非空；旧 refresh 再刷 401） | passed |
| TC-007 网关会员白名单 | gateway `CHG0016GatewaySecurityChainTest::memberAuthEndpointsWhitelisted`（register/login/refresh 匿名 200）+ `mallProductsStillPublic`（公开商品例外保持） | passed |
| TC-008 MEMBER/ADMIN 双向 403 矩阵 | gateway `memberScopeRequiresAuthentication`（匿名 401 同构体）、`memberTokenAcceptedForMemberScope`（MEMBER→members/me、shipping-addresses 200）、`adminTokenForbiddenForMemberScope`（ADMIN→会员域 403）、`memberTokenForbiddenForAdminScope`（MEMBER→/api/admin 403）；identity 侧 `MemberSessionApiTest::memberTokenCannotReachAdminScopeAndLogoutRequiresAuth`（MEMBER→/api/admin/** 403、匿名 logout 401）；CHG-0015 既有 7 例零改动全绿 | passed |

## AC → 测试覆盖

| AC | 覆盖测试 |
|---|---|
| AC-008 双 Token + claim subjectType=MEMBER/sub=memberId | TC-001 全部落点 |
| AC-009 错密码/不存在同文案 | TC-002 全部落点 |
| AC-010 DISABLED「账号已禁用」 | TC-003 全部落点 |
| AC-011 旋转/旧 token 失效/重放整族撤销/伪造过期 401 | TC-004、TC-005（过期路径由 ACTIVE 三态 expiresAt 判定覆盖于 isActive 逻辑；H2 集成中伪造/缺失显式断言） |
| AC-012 logout 后 refresh 401 + 版本语义 | TC-006（auth_version=2 + 撤族后旧 refresh 401；access claim 版本陈旧的下游拒绝由 M1 版本校验链路与后续 M3 Test 联调覆盖） |
| AC-013 MEMBER↔ADMIN 双向 403 | TC-008 全部落点（网关 + identity 双层） |

## Regression（全量回归）

| 证据日志 | 命令 | 结果 |
|---|---|---|
| logs/backend-full-package.log | `mvn clean package`（24 模块，全部测试） | BUILD SUCCESS；全部测试模块合计 **242 passed，0 failures，0 errors，0 skipped**；可执行模块 spring-boot repackage 成功 |

重点回归面：GatewaySecurityConfiguration 的 converter 变更影响所有经网关角色判定——
CHG0015GatewaySecurityChainTest（7，含 MEMBER→admin 403）、M1GatewayTokenValidationTest（3）全绿；
UseCaseException.Kind 增枚举影响 identity 全局异常映射——M1 全部认证授权/仓储/审计测试零回归；
MemberAuthController 同文件追加端点后注册链路 5 例保持 201/409/400 原行为。

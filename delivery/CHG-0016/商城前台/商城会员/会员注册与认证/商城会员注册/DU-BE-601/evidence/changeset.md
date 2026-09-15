# Changeset — DU-BE-601

> 仓库：repo-1（ai-platform-backend），分支 M3-dev。
> 代码提交：2f70309a7834513300387327a7e7048ebbea0ab8（feat(identity,member): 会员注册全链路 outbox-lite 与 provision）。
> 共 2 个服务、49 个代码/配置/迁移/测试文件（33 新增 + 8 修改主干文件 + 8 测试支撑，下文全量列出）。

## 1. mall-identity：迁移与配置

| 文件 | 变更类型 |
|---|---|
| `mall-services/mall-identity/src/main/resources/db/migration/V7__create_member_account.sql` | 新增（member_user / member_refresh_token / member_event_outbox；DEV-1/DEV-2） |
| `mall-services/mall-identity/src/main/resources/application.yml` | 修改（mall.security.jwt、internal shared-secret 占位、mall.member.service-uri、注册路径放行） |
| `mall-services/mall-identity/src/main/java/com/ai/mall/identity/infrastructure/config/JwtConfiguration.java` | 修改（register permitAll；/api/internal/** hasRole SERVICE + InternalIdentityFilter） |
| `mall-services/mall-identity/src/main/java/com/ai/mall/identity/infrastructure/config/SchedulingConfiguration.java` | 新增（@EnableScheduling） |

## 2. mall-identity：会员域与应用层

| 文件 | 变更类型 |
|---|---|
| `domain/model/member/MemberStatus.java` | 新增 |
| `domain/model/member/MemberUsername.java` | 新增（record，规则正则 + norm 小写化） |
| `domain/model/member/MemberAccount.java` | 新增（聚合根 register/reconstitute/ensureCanSignIn） |
| `domain/model/member/MemberRegisteredEvent.java` | 新增（eventId/memberId/username/nickname/occurredAt） |
| `domain/model/member/PendingMemberEvent.java` | 新增 |
| `domain/service/MemberPasswordPolicy.java` | 新增（8-32 含字母+数字） |
| `domain/repository/MemberUserRepository.java` | 新增（端口） |
| `domain/repository/MemberEventOutboxRepository.java` | 新增（端口） |
| `application/port/MemberProvisioner.java` | 新增（端口） |
| `application/port/AccessTokenIssuer.java` | 修改（issue 增 SubjectType 参） |
| `application/member/MemberRegistrationService.java` | 新增（单事务注册 + afterCommit 触发） |
| `application/member/MemberProvisionRelay.java` | 新增（定时重试/上限告警/afterCommit 立即投递） |
| `application/member/MemberNicknames.java` | 新增（identity 侧默认昵称工具，包私有） |
| `application/service/TokenPairApplicationService.java` | 修改（ADMIN 两处调用补 SubjectType.ADMIN） |

## 3. mall-identity：基础设施与接口

| 文件 | 变更类型 |
|---|---|
| `infrastructure/persistence/member/MemberUserPo.java` | 新增 |
| `infrastructure/persistence/member/MemberUserMapper.java` | 新增 |
| `infrastructure/persistence/member/MyBatisMemberUserRepository.java` | 新增（DuplicateKey→DuplicateResource/查重） |
| `infrastructure/persistence/member/MemberEventOutboxPo.java` | 新增 |
| `infrastructure/persistence/member/MemberEventOutboxMapper.java` | 新增 |
| `infrastructure/persistence/member/MyBatisMemberEventOutboxRepository.java` | 新增（自建 ObjectMapper，memberId 字符串化，ISO-8601） |
| `infrastructure/client/MemberProvisionClient.java` | 新增（RestClient + X-Internal-Token + UnifyResult 解析） |
| `infrastructure/security/RsaAccessTokenIssuer.java` | 修改（subject_type claim 参数化，拒绝 null/GUEST） |
| `interfaces/rest/member/MemberAuthController.java` | 新增（POST /api/auth/member/register → 201） |
| `interfaces/rest/internal/InternalMemberController.java` | 新增（GET /api/internal/members/{id}/profile-seed，DEV-3） |

## 4. mall-identity：测试

| 文件 | 变更类型 |
|---|---|
| `src/test/java/.../domain/model/member/MemberAccountTest.java` | 新增（规则矩阵 6 例） |
| `src/test/java/.../application/member/MemberRegistrationApiTest.java` | 新增（5 例） |
| `src/test/java/.../application/member/MemberProvisionRelayTest.java` | 新增（3 例：重试 DONE/上限/事务回滚） |
| `src/test/java/.../interfaces/rest/internal/InternalMemberSeedApiTest.java` | 新增（3 例：200/404/401） |
| `src/test/java/.../support/ApiTestSecurityConfig.java` | 新增（进程内 RSA + InternalIdentityFilter） |
| `src/test/java/.../infrastructure/security/RsaAccessTokenIssuerTest.java` | 修改（补 SubjectType 参） |

## 5. mall-member：从骨架建成建档服务

| 文件 | 变更类型 |
|---|---|
| `pom.xml` | 修改（mall-common-security、oauth2-resource-server、mybatis-plus-jsqlparser） |
| `src/main/resources/db/migration/V1__create_member_tables.sql` | 新增（member_profile + uk_profile_event） |
| `src/main/resources/application.yml` | 修改（mall.security.jwt、internal shared-secret 占位） |
| `domain/model/member/Gender.java` | 新增 |
| `domain/model/member/MemberProfile.java` | 新增（provision/reconstitute 工厂） |
| `domain/exception/DuplicateResourceException.java` | 新增 |
| `domain/repository/MemberProfileRepository.java` | 新增（端口） |
| `application/member/ProfileProvisionService.java` | 新增（@Transactional 双幂等建档） |
| `application/member/MemberNicknames.java` | 新增（「会员」+ 后 6 位） |
| `infrastructure/persistence/member/MemberProfilePo.java` | 新增 |
| `infrastructure/persistence/member/MemberProfileMapper.java` | 新增 |
| `infrastructure/persistence/member/MyBatisMemberProfileRepository.java` | 新增 |
| `infrastructure/config/MemberSecurityConfiguration.java` | 新增（@Profile("!test")，JWT + 内部过滤器） |
| `infrastructure/config/MybatisPlusConfig.java` | 新增（分页插件） |
| `interfaces/rest/internal/InternalMemberProvisionController.java` | 新增（POST /api/internal/members/provision） |
| `interfaces/rest/internal/dto/InternalMemberDtos.java` | 新增（ProvisionRequest/Response） |
| `src/test/java/.../application/member/ProfileProvisionServiceTest.java` | 新增（6 例） |
| `src/test/java/.../interfaces/rest/internal/InternalMemberProvisionApiTest.java` | 新增（3 例） |
| `src/test/java/.../support/ApiTestSecurityConfig.java` | 新增（进程内 RSA + InternalIdentityFilter） |

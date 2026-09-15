# Changeset — DU-BE-603

> 仓库：repo-1（ai-platform-backend），分支 M3-dev。
> 代码提交见 commits.md（feat(member): 会员资料 GET/PUT /me、MinIO 头像上传与 profile-seed 懒补偿）。
> 共 2 个模块（mall-bom、mall-member）、26 个代码/配置/测试文件（17 新增 + 9 修改），
> 无数据库迁移（member_profile 七列已由 DU-BE-601 V1 建好）。下文全量列出。

## 1. 依赖管理（mall-bom）

| 文件 | 变更类型 |
|---|---|
| `mall-bom/pom.xml` | 修改（properties 增 minio.version=8.5.17；dependencyManagement 登记 io.minio:minio） |
| `mall-services/mall-member/pom.xml` | 修改（引入 io.minio:minio，版本由 BOM 管；validation 走传递依赖不重复声明） |

## 2. mall-member：主干新增（领域/应用/基础设施/接口）

| 文件 | 变更类型 |
|---|---|
| `mall-services/mall-member/src/main/java/com/ai/mall/member/domain/model/member/AvatarFormat.java` | 新增（JPEG/PNG/WEBP 魔数枚举；detect 重算 contentType/扩展名，伪装拒绝） |
| `mall-services/mall-member/src/main/java/com/ai/mall/member/application/port/AvatarStorage.java` | 新增（端口 uploadAvatar(memberId, content, format)→URL） |
| `mall-services/mall-member/src/main/java/com/ai/mall/member/application/port/IdentityProfileSeedClient.java` | 新增（端口 fetchSeed + ProfileSeed record） |
| `mall-services/mall-member/src/main/java/com/ai/mall/member/application/member/MemberProfileErrorCode.java` | 新增（B0101/S0101/A0101/A0102/S0102 五码，中文文案） |
| `mall-services/mall-member/src/main/java/com/ai/mall/member/application/member/ProfileApplicationService.java` | 新增（getProfile 懒补偿；updateProfile 合并语义；updateAvatar 大小→魔数→存储→写库） |
| `mall-services/mall-member/src/main/java/com/ai/mall/member/infrastructure/storage/MinioStorageProperties.java` | 新增（@ConfigurationProperties mall.storage.minio record） |
| `mall-services/mall-member/src/main/java/com/ai/mall/member/infrastructure/storage/MinioAvatarStorage.java` | 新增（key 规则；懒 ensureBucket+公开读 policy；故障 503，DEV-2） |
| `mall-services/mall-member/src/main/java/com/ai/mall/member/infrastructure/config/MinioConfiguration.java` | 新增（MinioClient bean 无网络构造 + Storage bean 注册） |
| `mall-services/mall-member/src/main/java/com/ai/mall/member/infrastructure/client/RestIdentityProfileSeedClient.java` | 新增（X-Internal-Token；404/401→401，其他/连接故障→503） |
| `mall-services/mall-member/src/main/java/com/ai/mall/member/interfaces/rest/mall/MemberProfileController.java` | 新增（GET/PUT /me、POST /me/avatar；类级 hasRole MEMBER；memberId 仅取 subject） |
| `mall-services/mall-member/src/main/java/com/ai/mall/member/interfaces/rest/mall/dto/MemberProfileDtos.java` | 新增（ProfileView/UpdateProfileRequest/AvatarUploadResponse 收口） |
| `mall-services/mall-member/src/main/java/com/ai/mall/member/interfaces/rest/mall/MemberWebExceptionHandler.java` | 新增（HIGHEST_PRECEDENCE：超限→400 A0102、AccessDenied→403，DEV-3/4） |

## 3. mall-member：主干修改

| 文件 | 变更类型 |
|---|---|
| `mall-services/mall-member/src/main/java/com/ai/mall/member/domain/model/member/MemberProfile.java` | 修改（常量与手机/邮箱正则；updateProfile、changeAvatar 行为与中文不变量文案） |
| `mall-services/mall-member/src/main/java/com/ai/mall/member/domain/repository/MemberProfileRepository.java` | 修改（增 int update(MemberProfile)） |
| `mall-services/mall-member/src/main/java/com/ai/mall/member/infrastructure/persistence/member/MemberProfileMapper.java` | 修改（@Update 五列 + updated_at=NOW(6)） |
| `mall-services/mall-member/src/main/java/com/ai/mall/member/infrastructure/persistence/member/MyBatisMemberProfileRepository.java` | 修改（update 实现；toPo 抽取复用） |
| `mall-services/mall-member/src/main/java/com/ai/mall/member/infrastructure/config/MemberSecurityConfiguration.java` | 修改（/api/mall/** hasRole MEMBER 路径层收口，DEV-4） |
| `mall-services/mall-member/src/main/resources/application.yml` | 修改（multipart 2MB；mall.identity.service-uri；mall.storage.minio.* env 覆写点） |

## 4. mall-member：测试

| 文件 | 变更类型 |
|---|---|
| `mall-services/mall-member/src/test/java/com/ai/mall/member/domain/model/member/AvatarFormatTest.java` | 新增（3 例魔数识别/伪装/截断） |
| `mall-services/mall-member/src/test/java/com/ai/mall/member/domain/model/member/MemberProfileUpdateTest.java` | 新增（6 例聚合不变量，邮箱边界 129） |
| `mall-services/mall-member/src/test/java/com/ai/mall/member/application/member/ProfileApplicationServiceTest.java` | 新增（11 例纯 Mockito：补偿/合并/拒绝零存储/先传后写/503） |
| `mall-services/mall-member/src/test/java/com/ai/mall/member/infrastructure/storage/MinioAvatarStorageTest.java` | 新增（5 例 mock MinioClient：建桶 policy、key/contentType、503 重试） |
| `mall-services/mall-member/src/test/java/com/ai/mall/member/interfaces/rest/mall/MemberProfileApiTest.java` | 新增（9 例 MockMvc 全链路 TC-001~005 + 401/403/seed 401） |
| `mall-services/mall-member/src/test/java/com/ai/mall/member/support/ApiTestSecurityConfig.java` | 修改（/api/mall/** hasRole MEMBER 同步，DEV-4） |

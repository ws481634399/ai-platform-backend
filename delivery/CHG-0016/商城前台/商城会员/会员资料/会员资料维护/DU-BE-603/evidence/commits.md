# Commits — DU-BE-603

> 仓库：repo-1（implementation/ai-platform-backend），分支 M3-dev。
> 基线：cefbfb78add704880dff8ce9bef0ee138b276635（DU-BE-602 收尾后 HEAD）。

| Commit（完整 hash） | 类型 | 说明 |
|---|---|---|
| 8c5a1e690845b85e03ee839318d4bcdbce88893b | feat | 会员资料 GET/PUT /me、MinIO 头像上传与 profile-seed 懒补偿（DU-BE-603 全部代码/配置/测试，26 文件） |

提交内容对应 evidence/changeset.md 的全量文件清单：
- mall-bom：minio 8.5.17 版本登记；mall-member pom 引入依赖。
- mall-member：主干新增 12（AvatarFormat + 两端口 + 错误码 + 应用服务 + MinIO 三件套 +
  seed client + Controller/Advice/DTO）、主干修改 6（MemberProfile/Repository/Mapper/
  RepositoryImpl/安全链/application.yml）；测试新增 5（34 例）+ 修改 1（测试安全链）。

验证：该提交对应工作树 `mvn clean package` BUILD SUCCESS、mall-member **44/44**、
全仓 **276/0/0/0**（见 evidence/logs/be-member-test-run1.log、backend-full-package-run1.log）。

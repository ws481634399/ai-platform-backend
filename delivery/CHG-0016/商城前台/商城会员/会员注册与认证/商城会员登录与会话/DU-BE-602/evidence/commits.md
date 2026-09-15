# Commits — DU-BE-602

> 仓库：repo-1（implementation/ai-platform-backend），分支 M3-dev。
> 基线：b867e89055c48cd23c58d2182015953bb5a1fcd9（DU-BE-601 收尾后）。

| Commit（完整 hash） | 类型 | 说明 |
|---|---|---|
| f1367cb5617cae51a3de1c78256f6992915f5fa6 | feat | 会员登录/刷新/退出双令牌与网关 MEMBER 隔离（DU-BE-602 全部代码/配置/测试，22 文件） |

提交内容对应 evidence/changeset.md 的全量文件清单：
- mall-identity：会员会话域模型/端口 2、应用服务 3、持久化三件套 3、主干修改 7
  （MemberUserRepository/Mapper/Impl、UseCaseException、IdentityExceptionHandler、
  MemberAuthController、JwtConfiguration）；测试新增 3 + 资源 1 + 修改 1。
- mall-gateway：安全配置与路由 2 修改、网关切片测试 1 新增。

验证：该提交对应工作树 `mvn clean package` BUILD SUCCESS、242/0/0/0
（见 evidence/logs/backend-full-package.log）。

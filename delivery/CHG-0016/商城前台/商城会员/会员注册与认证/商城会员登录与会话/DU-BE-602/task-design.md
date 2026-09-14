# DU Task Design — DU-BE-602

> 层级：实现仓（Repository）侧产物——Expected Implementation 之"怎么做"（DU 技术方案）。
> 权威 DU 划分：外部 story-design.md §5（SSOT）。

## 1. Goal

会员登录、刷新令牌旋转与重放撤销（family）、退出；网关 ROLE_MEMBER 双向隔离与 /api/auth/member/** 白名单。

## 2. Repository

repo-1（mall-identity 8101 + mall-gateway 8080）

## 3. Scope

- mall-identity：MemberAuthService（login/refresh/logout）；interfaces `POST /api/auth/member/login`、`/refresh`、`/logout`；member_refresh_token 表（id 哈希存储、family_id、member_id、status ACTIVE/ROTATED/REVOKED、expires_at、created_at，索引 family_id）；login 失败统一文案（用户不存在与密码错误同一错误码 INVALID_CREDENTIALS）；status=DISABLED → ACCOUNT_DISABLED；JWT claim sub=字符串 memberId、subjectType=MEMBER；logout 使 refresh 撤销并 bump member_user.auth_version（access 短过期，撤权以版本校验为准）。
- mall-gateway：白名单增 /api/auth/member/**；`/api/mall/**` 要求 ROLE_MEMBER（/api/mall/products 等公开路径显式 permitAll 例外，与 CHG-0017 首页/公开分类一并配置）；/api/admin/** ROLE_ADMIN 不变；任何 MEMBER→admin、ADMIN→mall/member 403。

## 4. Design References

- CHG-0016 requirement-design.md §2.2（令牌旋转与重放检测）、§4（认证路径与网关矩阵）；STORY-003-01-01-02 story-design.md §1/§2/§4。
- 代码先例：admin 端 AuthService/JwtIssuer（M1）；JwtSubjectConverter 已为非 GUEST 生成 ROLE_<TYPE>。

## 5. Dependencies

权威表：无。实际前置：DU-BE-601（member_user/issuer SubjectType）与 DU-BE-501（白名单/过滤器机制）。

## 6. Implementation Sketch

- login：username_norm 查账号；不存在/BCrypt 不匹配统一 401 INVALID_CREDENTIALS("用户名或密码错误")；DISABLED 403 ACCOUNT_DISABLED("账号已禁用")；成功：revoke 旧 family（可选策略=单设备：登录即撤销该用户旧 refresh）→ 新建 family，签发 access+refresh（refresh 仅存 SHA-256 哈希）。
- refresh：按哈希查 token；不存在/过期/REVOKED → 401；ACTIVE → ROTATED 并新建同 family ACTIVE 一行（旋转）；发现 ROTATED 状态被再次提交（重放）→ 撤销整个 family（全部 REVOKED）→ 401。
- logout：当前 refresh REVOKED + auth_version+1（access 携带 version claim，受保护端点/网关解析版本，过期窗口内旧 access 自然失效或被版本校验拒绝）。
- 网关链序：白名单 permitAll → internal denyAll → mall 鉴权矩阵（公开 mall 路径例外）→ admin hasRole ADMIN → any authenticated。

## 7. Pseudocode

N/A（metadata `pseudocode: false`）。family 状态判定为三态直线分支，§6 已完整；无复杂算法。

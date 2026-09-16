# DU Implementation — DU-BE-803

> DU 级实施记录（Actual Implementation，sdd-dev 绑定本 DU 产出）。实施正文归属本仓库。

## 变更内容

### mall-cart 改动
| 文件 | 改动 |
|------|------|
| `domain/cart/CartConstants.java` | 新增 `MERGE_TOKEN_KEY_PREFIX="cart:merge:"`、`MERGE_TOKEN_TTL_SECONDS=300L`、`mergeTokenKey(token)` |
| `domain/cart/CartErrorCode.java` | 新增 `MERGE_TOKEN_EXPIRED(B0305,400)`、`MERGE_TOKEN_INVALID(B0306,401)` |
| `domain/cart/CartRepository.java` | 新增 `merge()`、`issueMergeToken()` 及 `MergeItem`/`MergeResult`/`MergedSku`/`TruncatedSku`/`DroppedSku` record |
| `infrastructure/redis/RedisCartRepository.java` | 实现 `merge()`（调 cart_merge.lua）、`issueMergeToken()`、`loadScript()` 泛型重载 |
| `resources/scripts/cart_merge.lua`（NEW） | 单 Lua 原子：token GET 校验→DEL 消费→逐条合并（相加/999 截断/100 dropped）→EXPIRE 90 天；手写 `encodeArray()` 兼容 Redis 7.4 cjson 空表问题 |
| `application/cart/CartMergeService.java`（NEW） | `issueToken()`、`merge()`：应用层先用 ProductSkuClient 批量校验可售性，失效项 dropped；TOKEN_MISSING→400，TOKEN_MISMATCH→401 |
| `interfaces/rest/mall/CartController.java` | 新增 `POST /api/mall/cart/merge-token`、`POST /api/mall/cart/merge` |
| `interfaces/rest/mall/dto/CartDtos.java` | 新增 `GuestCartItemDto`/`MergeCartRequest`/`MergeTokenResponse`/`MergeCartResponse` 等 |

### mall-product 改动
| 文件 | 改动 |
|------|------|
| `interfaces/rest/mall/dto/MallSkuItemsDtos.java`（NEW） | `SkuItemsRequest`、`SkuItemView` |
| `interfaces/rest/mall/MallSkuController.java` | 新增 `POST /api/mall/skus/items`：复用 `SkuBatchApplicationService.batch()`，filter `salable=true`，缺失项不返回 |

### 测试
- `RedisCartRepositoryLuaTest.java`：7 个合并用例（tokenMissing/tokenMismatch/累加/截断/超100/幂等/TTL）
- `CartApiTest.java`：5 个 API 用例（merge-token/合并/失效dropped/重放400/伪造400+串号401）

## Commits

未提交（待用户确认）。

## Deviations

### DEV-1
- 原 DU 建议：Lua 用 cjson 编码返回 JSON
- 实际实现：手写 `encodeArray()` 拼接数组元素，再用 cjson 编码对象
- 原因：Redis 7.4-alpine 的 cjson 不支持 `encode_empty_table_as_object`，空数组会编成 `{}` 而非 `[]`，导致前端类型错误
- 影响评估：返回结构与契约一致（数组始终为 `[]`），无功能影响

## 自检

- [x] mall-cart 测试 53/53 通过（含 12 个新增合并用例）
- [x] mall-product 测试 95/95 通过
- [x] mall-cart + mall-product package 成功

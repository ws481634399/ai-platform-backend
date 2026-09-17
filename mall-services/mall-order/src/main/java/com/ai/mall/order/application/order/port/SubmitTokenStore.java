package com.ai.mall.order.application.order.port;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * 下单令牌存储端口（CHG-0019）。
 *
 * <p>preview 可下单时签发一次性 UUID；create 时原子消费（Redis Lua GETDEL），
 * 构成双层幂等的第一层（DB 唯一索引 (member_id, submit_token) 为第二层）。
 * 令牌与会员命名空间绑定，键：{@code order:submit-token:{memberId}:{token}}。
 */
public interface SubmitTokenStore {

    /** 签发令牌（载荷含来源/地址/行指纹）。 */
    void issue(long memberId, String token, Payload payload, Duration ttl);

    /** 原子取出并删除令牌；不存在/已消费/已过期 → empty。 */
    Optional<Payload> consume(long memberId, String token);

    /** 令牌载荷：创建订单时的权威依据（请求体不可覆盖）。 */
    record Payload(String source, Long addressId, List<Line> items) {

        public record Line(long skuId, int quantity) {
        }
    }
}

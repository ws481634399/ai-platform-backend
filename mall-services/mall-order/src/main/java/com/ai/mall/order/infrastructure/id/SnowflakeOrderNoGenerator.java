package com.ai.mall.order.infrastructure.id;

import com.ai.mall.order.domain.order.OrderNoGenerator;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

/**
 * 订单号生成器（CHG-0019）：{@code ORD + yyyyMMddHHmmssSSS + 4 位 JVM 内循环序列}。
 *
 * <p>同一毫秒内由 {@link AtomicLong} 0..9999 循环序列区分；M4 单实例部署足够，
 * 应用层在撞 order_no 唯一键时最多重新生成 3 次兜底。
 */
@Component
public class SnowflakeOrderNoGenerator implements OrderNoGenerator {

    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS").withZone(ZoneId.systemDefault());

    private final AtomicLong sequence = new AtomicLong(0);

    @Override
    public String next() {
        String timePart = TIMESTAMP.format(Instant.now());
        long seq = sequence.getAndIncrement() % 10_000;
        return "ORD" + timePart + String.format("%04d", seq);
    }
}

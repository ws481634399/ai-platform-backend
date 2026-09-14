package com.ai.mall.common.web.annotation;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * CHG-0015 TC-006/007/008：@StringId 元注解的 Jackson 行为契约。
 */
@DisplayName("@StringId 字符串 ID 序列化")
class StringIdJacksonTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 测试夹具：模拟对外 DTO record——业务 ID 标注，金额保持普通 long。 */
    public record IdView(
            @StringId long id,
            @StringId Long categoryId,
            long priceInCents,
            int quantity,
            String name) {}

    @Test
    @DisplayName("TC-006 标注字段输出为字符串，19 位雪花值不丢精度")
    void annotatedLongSerializesAsString() throws Exception {
        long snowflakeId = 2099123456789012345L;
        String json = objectMapper.writeValueAsString(new IdView(snowflakeId, 100L, 9900L, 3, "sku"));

        JsonNode tree = objectMapper.readTree(json);
        assertThat(tree.get("id").isTextual()).isTrue();
        assertThat(tree.get("id").asText()).isEqualTo("2099123456789012345");
        assertThat(tree.get("categoryId").isTextual()).isTrue();
        assertThat(tree.get("categoryId").asText()).isEqualTo("100");
    }

    @Test
    @DisplayName("TC-007 金额/数量等非 ID 数值仍为 JSON number")
    void nonIdNumbersStayNumeric() throws Exception {
        String json = objectMapper.writeValueAsString(new IdView(1L, 2L, 9900L, 3, "sku"));

        JsonNode tree = objectMapper.readTree(json);
        assertThat(tree.get("priceInCents").isNumber()).isTrue();
        assertThat(tree.get("priceInCents").asLong()).isEqualTo(9900L);
        assertThat(tree.get("quantity").isNumber()).isTrue();
        assertThat(tree.get("quantity").asInt()).isEqualTo(3);
    }

    @Test
    @DisplayName("TC-008 入参字符串 \"123\" 与数字 123 均可反序列化为 Long")
    void inputAcceptsBothStringAndNumber() throws Exception {
        IdView fromString = objectMapper.readValue(
                "{\"id\":\"123\",\"categoryId\":\"456\",\"priceInCents\":9900,\"quantity\":1,\"name\":\"x\"}",
                IdView.class);
        IdView fromNumber = objectMapper.readValue(
                "{\"id\":123,\"categoryId\":456,\"priceInCents\":9900,\"quantity\":1,\"name\":\"x\"}",
                IdView.class);

        assertThat(fromString.id()).isEqualTo(123L);
        assertThat(fromString.categoryId()).isEqualTo(456L);
        assertThat(fromNumber.id()).isEqualTo(123L);
        assertThat(fromNumber.categoryId()).isEqualTo(456L);
    }
}

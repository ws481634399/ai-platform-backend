package com.ai.mall.common.web.annotation;

import com.fasterxml.jackson.annotation.JacksonAnnotationsInside;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 字符串业务 ID 标注（CHG-0015）：标注于对外 REST DTO 的业务 ID 字段（含 record 组件），
 * 序列化为 JSON 字符串，规避雪花 ID（19 位）超出 JS Number.MAX_SAFE_INTEGER 的精度问题。
 *
 * <p>仅作用于出参序列化（{@link ToStringSerializer}）；金额、库存数量、分页元数据等
 * 非 ID 数值禁止标注，必须保持 JSON number。入参 ID 声明为 Long/long，Jackson/Spring
 * 原生即可同时接受 "123" 与 123 两种形态，无需反序列化器。
 */
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@JacksonAnnotationsInside
@JsonSerialize(using = ToStringSerializer.class)
public @interface StringId {
}

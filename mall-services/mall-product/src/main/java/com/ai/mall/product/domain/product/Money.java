package com.ai.mall.product.domain.product;

/**
 * 精确金额值对象：以分为单位存储，禁止浮点。
 */
public record Money(long amountInCents) {

    public Money {
        if (amountInCents < 0) {
            throw new IllegalArgumentException("金额不能为负");
        }
    }

    public static Money ofCents(long cents) {
        return new Money(cents);
    }
}

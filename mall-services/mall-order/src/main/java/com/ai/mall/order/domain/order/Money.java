package com.ai.mall.order.domain.order;

/**
 * 订单金额值对象（整数分，CHG-0019）。
 *
 * <p>全链路金额一律整数分 Long，禁止 float/double；M4 无优惠/运费（恒 0），
 * 但模型保留 goods/discount/freight/pay 四项为后续营销预留。
 * 不变量：各项非负；{@code pay = goods - discount + freight}。
 *
 * @param goodsFen    商品总金额（分）
 * @param discountFen 优惠金额（分，M4 恒 0）
 * @param freightFen  运费（分，M4 恒 0）
 * @param payFen      应付金额（分）
 */
public record Money(long goodsFen, long discountFen, long freightFen, long payFen) {

    public Money {
        if (goodsFen < 0 || discountFen < 0 || freightFen < 0 || payFen < 0) {
            throw new IllegalArgumentException("订单金额不能为负");
        }
        if (discountFen > goodsFen) {
            throw new IllegalArgumentException("优惠金额不能超过商品金额");
        }
        if (goodsFen - discountFen + freightFen != payFen) {
            throw new IllegalArgumentException("应付金额必须等于 商品金额-优惠+运费");
        }
    }

    /** M4：无优惠无运费，应付等于商品金额。 */
    public static Money ofM4(long goodsFen) {
        return new Money(goodsFen, 0L, 0L, goodsFen);
    }

    /** 持久化重建（不变量仍被紧凑构造器校验）。 */
    public static Money reconstitute(long goodsFen, long discountFen, long freightFen, long payFen) {
        return new Money(goodsFen, discountFen, freightFen, payFen);
    }
}

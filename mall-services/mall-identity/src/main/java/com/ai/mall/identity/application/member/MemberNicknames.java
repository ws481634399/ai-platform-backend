package com.ai.mall.identity.application.member;

/**
 * 会员默认昵称规则（CHG-0016）：{@code "会员" + memberId 后 6 位}。
 *
 * <p>memberId 不足 6 位时取完整 ID（自增主键早期值较短）。
 */
final class MemberNicknames {

    private static final int TAIL_LENGTH = 6;

    private MemberNicknames() {
    }

    static String defaultNickname(long memberId) {
        String digits = Long.toString(memberId);
        String tail = digits.length() <= TAIL_LENGTH ? digits : digits.substring(digits.length() - TAIL_LENGTH);
        return "会员" + tail;
    }
}

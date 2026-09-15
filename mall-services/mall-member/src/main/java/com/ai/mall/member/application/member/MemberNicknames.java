package com.ai.mall.member.application.member;

/**
 * 会员默认昵称规则（CHG-0016）：{@code "会员" + memberId 后 6 位}，与 identity 侧一致。
 * memberId 不足 6 位时取完整 ID。
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

package com.ai.mall.identity.domain.service;

import java.util.regex.Pattern;

/**
 * 会员密码策略（CHG-0016）：8-32 位且必须同时包含字母与数字（不允许纯数字/纯字母）。
 *
 * <p>与管理员策略（{@link PasswordPolicy}，12 位含大小写+数字）相互独立，
 * 符合商城会员注册的产品规则。领域层零框架依赖。
 */
public final class MemberPasswordPolicy {

    private static final int MIN_LENGTH = 8;
    private static final int MAX_LENGTH = 32;
    private static final Pattern LETTER = Pattern.compile(".*[A-Za-z].*");
    private static final Pattern DIGIT = Pattern.compile(".*\\d.*");

    /** 校验原始密码（哈希前调用）；违例抛 {@link IllegalArgumentException}（400 字段级提示）。 */
    public void ensureValid(String raw) {
        if (raw == null || raw.length() < MIN_LENGTH || raw.length() > MAX_LENGTH
                || !LETTER.matcher(raw).matches() || !DIGIT.matcher(raw).matches()) {
            throw new IllegalArgumentException("密码长度需为8-32位且必须同时包含字母与数字");
        }
    }
}

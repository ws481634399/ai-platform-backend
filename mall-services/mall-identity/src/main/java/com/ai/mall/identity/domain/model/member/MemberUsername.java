package com.ai.mall.identity.domain.model.member;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 会员用户名值对象（CHG-0016）：承载用户名规则与大小写归一化。
 *
 * <p>规则：4-20 位、字母开头、仅允许字母/数字/下划线；归一化一律 LOWER，
 * 唯一约束建立在 {@link #norm()} 上（AbC 与 abc 视为同名）。
 * 领域层零框架依赖，违例抛 {@link IllegalArgumentException}（接口层映射 400 字段级提示）。
 */
public record MemberUsername(String value) {

    /** 字母开头 + 字母数字下划线，总长 4-20。 */
    private static final Pattern PATTERN = Pattern.compile("[A-Za-z][A-Za-z0-9_]{3,19}");

    public MemberUsername {
        if (value == null || !PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("用户名需为4-20位、字母开头且仅包含字母/数字/下划线");
        }
    }

    /** 工厂：从原始入参构造（去除首尾空白由调用前保证，用户名本身不允许空白）。 */
    public static MemberUsername of(String raw) {
        return new MemberUsername(raw);
    }

    /** 归一化形式（小写），唯一查重与唯一索引均以此为准。 */
    public String norm() {
        return value.toLowerCase(Locale.ROOT);
    }
}

package com.ai.mall.member.domain.exception;

/**
 * 唯一约束冲突（CHG-0016）：基础设施捕获 {@code DuplicateKeyException} 后转译，
 * 应用层据此做幂等判定（如 provision 并发重放）。
 */
public final class DuplicateResourceException extends RuntimeException {

    public DuplicateResourceException(String message, Throwable cause) {
        super(message, cause);
    }
}

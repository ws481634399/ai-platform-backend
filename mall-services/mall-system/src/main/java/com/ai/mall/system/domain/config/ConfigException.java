package com.ai.mall.system.domain.config;

import com.ai.mall.common.web.exception.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * 系统配置域异常工厂（CHG-0022）。
 */
public final class ConfigException {

    private ConfigException() {
    }

    public static BusinessException valueInvalid(String message) {
        return new BusinessException(SystemErrorCode.CONFIG_VALUE_INVALID, HttpStatus.BAD_REQUEST, message);
    }

    public static BusinessException keyDuplicate(String key) {
        return new BusinessException(SystemErrorCode.CONFIG_KEY_DUPLICATE, HttpStatus.CONFLICT,
                "配置键已存在: " + key);
    }

    public static BusinessException notFound(String key) {
        return new BusinessException(SystemErrorCode.CONFIG_NOT_FOUND, HttpStatus.NOT_FOUND,
                "配置不存在: " + key);
    }

    public static BusinessException versionConflict(String key) {
        return new BusinessException(SystemErrorCode.CONFIG_VERSION_CONFLICT, HttpStatus.CONFLICT,
                SystemErrorCode.CONFIG_VERSION_CONFLICT.getMessage() + ": " + key);
    }
}

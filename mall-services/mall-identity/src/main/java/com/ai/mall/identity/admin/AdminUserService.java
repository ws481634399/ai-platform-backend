package com.ai.mall.identity.admin;

import com.ai.mall.common.core.result.CommonErrorCode;
import com.ai.mall.common.web.exception.BusinessException;
import com.ai.mall.identity.audit.AuthAuditService;
import com.ai.mall.identity.auth.AdminCredential;
import com.ai.mall.identity.auth.AdminCredentialRepository;
import com.ai.mall.identity.auth.AdminStatus;
import com.ai.mall.identity.persistence.AdminCredentialMapper;
import java.time.Instant;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminUserService {
    private final AdminCredentialRepository repository;
    private final AdminCredentialMapper mapper;
    private final PasswordEncoder encoder;
    private final AuthAuditService audit;

    public AdminUserService(AdminCredentialRepository repository, AdminCredentialMapper mapper,
                            PasswordEncoder encoder, AuthAuditService audit) {
        this.repository = repository; this.mapper = mapper; this.encoder = encoder; this.audit = audit;
    }

    @Transactional
    public AdminView create(long actor, String username, String rawPassword) {
        validatePassword(rawPassword);
        try {
            var saved = repository.save(new AdminCredential(0, username, encoder.encode(rawPassword),
                    AdminStatus.ENABLED, 1, 1, Instant.now(), Instant.now()));
            audit.record(actor, "ADMIN_CREATE", "ADMIN", Long.toString(saved.id()), "SUCCESS", null);
            return view(saved);
        } catch (DuplicateKeyException ex) {
            audit.record(actor, "ADMIN_CREATE", "ADMIN", "NEW", "FAILURE", "DUPLICATE_USERNAME");
            throw conflict("username already exists");
        }
    }

    public Page page(long page, int size) {
        if (page < 1 || size < 1 || size > 200) throw bad("invalid page");
        return new Page(mapper.count(), mapper.page((page - 1) * size, size));
    }

    @Transactional
    public void changeStatus(long actor, long id, AdminStatus status) {
        if (status == null) throw bad("status is required");
        var current = repository.findById(id).orElseThrow(() -> notFound("admin not found"));
        if (current.status() == status) return;
        if (status == AdminStatus.DISABLED && mapper.isSuperAdmin(id) > 0 && mapper.countEnabledSuperAdmins() <= 1) {
            audit.record(actor, "ADMIN_DISABLE", "ADMIN", Long.toString(id), "FAILURE", "LAST_SUPER_ADMIN");
            throw conflict("last enabled super administrator cannot be disabled");
        }
        if (mapper.updateStatus(id, status.name()) != 1) throw notFound("admin not found");
        audit.record(actor, "ADMIN_STATUS_CHANGE", "ADMIN", Long.toString(id), "SUCCESS", null);
    }

    @Transactional
    public void changePassword(long actor, long id, String rawPassword) {
        validatePassword(rawPassword);
        if (mapper.updatePassword(id, encoder.encode(rawPassword)) != 1) throw notFound("admin not found");
        audit.record(actor, "ADMIN_PASSWORD_CHANGE", "ADMIN", Long.toString(id), "SUCCESS", null);
    }

    private static AdminView view(AdminCredential admin) { return new AdminView(admin.id(), admin.username(), admin.status().name(), admin.authVersion(), admin.permissionVersion()); }
    private static void validatePassword(String value) {
        if (value == null || value.length() < 12 || !value.matches(".*[A-Z].*") || !value.matches(".*[a-z].*") || !value.matches(".*\\d.*"))
            throw bad("password must be at least 12 characters and include upper, lower and digit");
    }
    private static BusinessException bad(String m) { return new BusinessException(CommonErrorCode.PARAM_INVALID, HttpStatus.BAD_REQUEST, m); }
    private static BusinessException notFound(String m) { return new BusinessException(CommonErrorCode.BUSINESS_ERROR, HttpStatus.NOT_FOUND, m); }
    private static BusinessException conflict(String m) { return new BusinessException(CommonErrorCode.BUSINESS_ERROR, HttpStatus.CONFLICT, m); }
    public record AdminView(long id, String username, String status, long authVersion, long permissionVersion) {}
    public record Page(long total, List<AdminCredentialMapper.AdminSummary> items) {}
}

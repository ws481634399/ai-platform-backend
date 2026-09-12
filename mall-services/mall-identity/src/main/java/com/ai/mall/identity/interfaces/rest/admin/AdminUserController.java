package com.ai.mall.identity.interfaces.rest.admin;

import com.ai.mall.common.core.result.UnifyResult;
import com.ai.mall.common.security.SecurityContextFacade;
import com.ai.mall.identity.application.service.AdminUserApplicationService;
import com.ai.mall.identity.domain.model.admin.AdminUserStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/security/admins")
@Profile("!test")
public class AdminUserController {
    private final AdminUserApplicationService service;
    public AdminUserController(AdminUserApplicationService service) { this.service = service; }

    @PostMapping @PreAuthorize("@authorizationGuard.has('admin:create')")
    public UnifyResult<?> create(@Valid @RequestBody CreateRequest request) { return UnifyResult.ok(service.create(actor(), request.username(), request.password())); }
    @GetMapping @PreAuthorize("@authorizationGuard.has('admin:read')")
    public UnifyResult<?> page(@RequestParam(defaultValue="1") long page, @RequestParam(defaultValue="20") int size) { return UnifyResult.ok(service.page(page, size)); }
    @PatchMapping("/{id}/status") @PreAuthorize("@authorizationGuard.has('admin:update')")
    public UnifyResult<Void> status(@PathVariable long id, @RequestBody StatusRequest request) { service.changeStatus(actor(), id, request.status()); return UnifyResult.ok(); }
    @PutMapping("/{id}/password") @PreAuthorize("@authorizationGuard.has('admin:update')")
    public UnifyResult<Void> password(@PathVariable long id, @Valid @RequestBody PasswordRequest request) { service.changePassword(actor(), id, request.password()); return UnifyResult.ok(); }
    private static long actor() { return Long.parseLong(SecurityContextFacade.requireAdmin().subjectId()); }
    public record CreateRequest(@NotBlank String username, @NotBlank String password) {}
    public record StatusRequest(@jakarta.validation.constraints.NotNull AdminUserStatus status) {}
    public record PasswordRequest(@NotBlank String password) {}
}

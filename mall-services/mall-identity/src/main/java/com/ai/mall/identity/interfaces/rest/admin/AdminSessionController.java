package com.ai.mall.identity.interfaces.rest.admin;

import com.ai.mall.common.core.result.UnifyResult;
import com.ai.mall.common.security.SecurityContextFacade;
import com.ai.mall.identity.application.service.AdminSessionQueryService;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/session")
@Profile("!test")
public class AdminSessionController {
    private final AdminSessionQueryService sessions;
    public AdminSessionController(AdminSessionQueryService sessions) {
        this.sessions = sessions;
    }

    @GetMapping("/bootstrap")
    public UnifyResult<AdminSessionQueryService.Bootstrap> bootstrap() {
        var subject = SecurityContextFacade.requireAdmin();
        long id = Long.parseLong(subject.subjectId());
        return UnifyResult.ok(sessions.bootstrap(id));
    }

}

package com.ai.mall.member.interfaces.rest.mall;

import com.ai.mall.common.core.result.CommonErrorCode;
import com.ai.mall.common.core.result.UnifyResult;
import com.ai.mall.common.security.AuthenticatedSubject;
import com.ai.mall.common.security.SecurityContextFacade;
import com.ai.mall.common.web.exception.BusinessException;
import com.ai.mall.member.application.member.MemberProfileErrorCode;
import com.ai.mall.member.application.member.ProfileApplicationService;
import com.ai.mall.member.application.member.ProfileApplicationService.UpdateProfileCommand;
import com.ai.mall.member.domain.model.member.MemberProfile;
import com.ai.mall.member.interfaces.rest.mall.dto.MemberProfileDtos.AvatarUploadResponse;
import com.ai.mall.member.interfaces.rest.mall.dto.MemberProfileDtos.ProfileView;
import com.ai.mall.member.interfaces.rest.mall.dto.MemberProfileDtos.UpdateProfileRequest;
import jakarta.validation.Valid;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 会员资料接口（CHG-0016 STORY-003-01-02-01）。
 *
 * <p>路径 SSOT：{@code /api/mall/members/me}（requirement-design §4）。
 * memberId 只从 {@link SecurityContextFacade} 取，任何接口不接受外部 memberId；
 * 类级 {@code ROLE_MEMBER} 收口，ADMIN JWT 被双向隔离（403）。
 */
@RestController
@RequestMapping("/api/mall/members/me")
@PreAuthorize("hasRole('MEMBER')")
public class MemberProfileController {

    private final ProfileApplicationService profiles;

    public MemberProfileController(ProfileApplicationService profiles) {
        this.profiles = profiles;
    }

    @GetMapping
    public UnifyResult<ProfileView> getMe() {
        return UnifyResult.ok(toView(profiles.getProfile(currentMemberId())));
    }

    @PutMapping
    public UnifyResult<ProfileView> updateMe(@Valid @RequestBody UpdateProfileRequest request) {
        MemberProfile updated = profiles.updateProfile(currentMemberId(),
                new UpdateProfileCommand(request.nickname(), request.gender(), request.phone(), request.email()));
        return UnifyResult.ok(toView(updated));
    }

    @PostMapping("/avatar")
    public UnifyResult<AvatarUploadResponse> uploadAvatar(@RequestPart("file") MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(MemberProfileErrorCode.FILE_TYPE_INVALID,
                    HttpStatus.BAD_REQUEST, "头像文件不能为空");
        }
        String avatarUrl = profiles.updateAvatar(currentMemberId(), file.getBytes());
        return UnifyResult.ok(new AvatarUploadResponse(avatarUrl));
    }

    /** 当前会话会员 ID：仅取自安全上下文（sub=Long.toString(memberId)），无入参来源。 */
    private static long currentMemberId() {
        AuthenticatedSubject subject = SecurityContextFacade.currentSubject()
                .orElseThrow(() -> new BusinessException(CommonErrorCode.BUSINESS_ERROR,
                        HttpStatus.UNAUTHORIZED, "请先登录"));
        try {
            return Long.parseLong(subject.subjectId());
        } catch (NumberFormatException ex) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR,
                    HttpStatus.UNAUTHORIZED, "登录主体非法，请重新登录");
        }
    }

    private static ProfileView toView(MemberProfile p) {
        return new ProfileView(p.memberId(), p.username(), p.nickname(), p.avatarUrl(),
                p.gender().name(), p.phone(), p.email());
    }
}

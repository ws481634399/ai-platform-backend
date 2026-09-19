package com.ai.mall.member.application.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ai.mall.common.web.exception.BusinessException;
import com.ai.mall.member.application.member.ProfileProvisionService.ProvisionCommand;
import com.ai.mall.member.application.port.AvatarStorage;
import com.ai.mall.member.application.port.IdentityProfileSeedClient;
import com.ai.mall.member.application.port.IdentityProfileSeedClient.ProfileSeed;
import com.ai.mall.common.core.image.ImageFormat;
import com.ai.mall.member.domain.model.member.Gender;
import com.ai.mall.member.domain.model.member.MemberProfile;
import com.ai.mall.member.domain.repository.MemberProfileRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

/**
 * 资料应用服务单测（CHG-0016 STORY-003-01-02-01）：
 * 懒补偿幂等（TC-003）、部分更新合并（TC-002）、头像「先校验后传再写库」顺序（TC-004/005）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProfileApplicationService 资料/补偿/头像")
class ProfileApplicationServiceTest {

    private static final long MEMBER_ID = 72000001L;

    @Mock MemberProfileRepository profiles;
    @Mock IdentityProfileSeedClient seedClient;
    @Mock ProfileProvisionService provisionService;
    @Mock AvatarStorage avatarStorage;

    ProfileApplicationService service;

    @BeforeEach
    void setUp() {
        service = new ProfileApplicationService(profiles, seedClient, provisionService, avatarStorage);
    }

    private MemberProfile existingProfile() {
        return MemberProfile.reconstitute(MEMBER_ID, "Seed_Member", "会员000001",
                null, "UNKNOWN", "13800138000", "old@example.com", "evt-1",
                Instant.parse("2026-09-15T10:00:00Z"), Instant.parse("2026-09-15T10:00:00Z"));
    }

    @Test
    @DisplayName("TC-001 档案存在：直接返回，不触发 seed 调用")
    void getProfilePresentSkipsSeed() {
        MemberProfile profile = existingProfile();
        when(profiles.findByMemberId(MEMBER_ID)).thenReturn(Optional.of(profile));

        assertThat(service.getProfile(MEMBER_ID)).isSameAs(profile);
        verify(seedClient, never()).fetchSeed(anyLong());
        verify(provisionService, never()).provision(any());
    }

    @Test
    @DisplayName("TC-003 档案缺失：seed 拉取后以确定性事件 ID 幂等补建并重查")
    void missingProfileTriggersIdempotentCompensation() {
        MemberProfile rebuilt = MemberProfile.provision(MEMBER_ID, "Seed_Member",
                "会员000001", "evt-anything", Instant.now());
        when(profiles.findByMemberId(MEMBER_ID))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(rebuilt));
        when(seedClient.fetchSeed(MEMBER_ID))
                .thenReturn(new ProfileSeed(MEMBER_ID, "Seed_Member", "ENABLED"));

        MemberProfile result = service.getProfile(MEMBER_ID);

        assertThat(result).isSameAs(rebuilt);
        String deterministicEventId = UUID.nameUUIDFromBytes(
                ("member-profile-seed:" + MEMBER_ID).getBytes(StandardCharsets.UTF_8)).toString();
        ArgumentCaptor<ProvisionCommand> captor = ArgumentCaptor.forClass(ProvisionCommand.class);
        verify(provisionService).provision(captor.capture());
        assertThat(captor.getValue().eventId()).isEqualTo(deterministicEventId);
        assertThat(captor.getValue().memberId()).isEqualTo(MEMBER_ID);
        assertThat(captor.getValue().username()).isEqualTo("Seed_Member");
    }

    @Test
    @DisplayName("TC-003 补偿已建档后再次查询：seed 不再被调用（不重复建）")
    void compensationNotRepeatedAfterRebuilt() {
        MemberProfile rebuilt = existingProfile();
        when(profiles.findByMemberId(MEMBER_ID)).thenReturn(Optional.of(rebuilt));

        service.getProfile(MEMBER_ID);
        service.getProfile(MEMBER_ID);

        verify(seedClient, never()).fetchSeed(anyLong());
        verify(provisionService, never()).provision(any());
    }

    @Test
    @DisplayName("seed 404/401（PROFILE_INCONSISTENT）原样上抛 401，不建库")
    void seedInconsistentRaises401() {
        when(profiles.findByMemberId(MEMBER_ID)).thenReturn(Optional.empty());
        when(seedClient.fetchSeed(MEMBER_ID)).thenThrow(
                new BusinessException(MemberProfileErrorCode.PROFILE_INCONSISTENT, HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> service.getProfile(MEMBER_ID))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(ex.getErrorCode().getCode()).isEqualTo("B0101");
                });
        verify(provisionService, never()).provision(any());
    }

    @Test
    @DisplayName("TC-002 部分更新：nickname/gender 覆盖、null 保留；phone 空串清空、email 非空覆盖")
    void partialUpdateMergeSemantics() {
        MemberProfile profile = existingProfile();
        when(profiles.findByMemberId(MEMBER_ID)).thenReturn(Optional.of(profile));

        MemberProfile updated = service.updateProfile(MEMBER_ID,
                new ProfileApplicationService.UpdateProfileCommand("新昵称", "female", "  ", "new@example.com"));

        assertThat(updated).isSameAs(profile);
        assertThat(profile.nickname()).isEqualTo("新昵称");
        assertThat(profile.gender()).isEqualTo(Gender.FEMALE);
        assertThat(profile.phone()).isNull();
        assertThat(profile.email()).isEqualTo("new@example.com");
        verify(profiles).update(profile);
    }

    @Test
    @DisplayName("TC-002 聚合校验失败（坏手机号）不写库")
    void invalidUpdateDoesNotPersist() {
        MemberProfile profile = existingProfile();
        when(profiles.findByMemberId(MEMBER_ID)).thenReturn(Optional.of(profile));

        assertThatThrownBy(() -> service.updateProfile(MEMBER_ID,
                new ProfileApplicationService.UpdateProfileCommand("昵称", null, "12345", null)))
                .isInstanceOf(IllegalArgumentException.class);
        verify(profiles, never()).update(any());
    }

    @Test
    @DisplayName("gender 非法字符串 → 400，不写库")
    void invalidGenderRejected() {
        when(profiles.findByMemberId(MEMBER_ID)).thenReturn(Optional.of(existingProfile()));

        assertThatThrownBy(() -> service.updateProfile(MEMBER_ID,
                new ProfileApplicationService.UpdateProfileCommand("昵称", "UNKNOWN_X", null, null)))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(profiles, never()).update(any());
    }

    @Test
    @DisplayName("TC-005 空文件 / 2.1MB → FILE_TYPE_INVALID/FILE_TOO_LARGE 400，对象存储零调用")
    void avatarSizeAndEmptyRejectedBeforeStorage() {
        assertThatThrownBy(() -> service.updateAvatar(MEMBER_ID, new byte[0]))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getErrorCode()).isEqualTo(MemberProfileErrorCode.FILE_TYPE_INVALID);
                });

        byte[] oversized = new byte[2 * 1024 * 1024 + 1024];
        oversized[0] = (byte) 0x89;
        assertThatThrownBy(() -> service.updateAvatar(MEMBER_ID, oversized))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getErrorCode()).isEqualTo(MemberProfileErrorCode.FILE_TOO_LARGE);
                });
        verify(avatarStorage, never()).uploadAvatar(anyLong(), any(), any());
        verify(profiles, never()).update(any());
    }

    @Test
    @DisplayName("TC-005 伪装 gif 魔数 → FILE_TYPE_INVALID 400，对象存储零调用")
    void disguisedGifRejectedBeforeStorage() {
        byte[] gif = "GIF89a".getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> service.updateAvatar(MEMBER_ID, gif))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(MemberProfileErrorCode.FILE_TYPE_INVALID));
        verify(avatarStorage, never()).uploadAvatar(anyLong(), any(), any());
        verify(profiles, never()).update(any());
    }

    @Test
    @DisplayName("TC-004 合规图片：先传对象成功，再以返回 URL 更新库；返回 URL")
    void avatarUploadThenPersist() {
        MemberProfile profile = existingProfile();
        when(profiles.findByMemberId(MEMBER_ID)).thenReturn(Optional.of(profile));
        byte[] jpeg = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0};
        when(avatarStorage.uploadAvatar(MEMBER_ID, jpeg, ImageFormat.JPEG))
                .thenReturn("http://localhost:9000/mall-avatar/member-avatar/72000001/x.jpg");

        String url = service.updateAvatar(MEMBER_ID, jpeg);

        assertThat(url).isEqualTo("http://localhost:9000/mall-avatar/member-avatar/72000001/x.jpg");
        assertThat(profile.avatarUrl()).isEqualTo(url);
        verify(avatarStorage, times(1)).uploadAvatar(MEMBER_ID, jpeg, ImageFormat.JPEG);
        verify(profiles).update(profile);
    }

    @Test
    @DisplayName("存储故障 503：上抛 STORAGE_UNAVAILABLE，不写 avatar_url")
    void storageFailureSkipsDbUpdate() {
        when(profiles.findByMemberId(MEMBER_ID)).thenReturn(Optional.of(existingProfile()));
        byte[] png = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        when(avatarStorage.uploadAvatar(anyLong(), any(), any())).thenThrow(
                new BusinessException(MemberProfileErrorCode.STORAGE_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> service.updateAvatar(MEMBER_ID, png))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(ex.getErrorCode().getCode()).isEqualTo("S0102");
                });
        verify(profiles, never()).update(any());
    }
}

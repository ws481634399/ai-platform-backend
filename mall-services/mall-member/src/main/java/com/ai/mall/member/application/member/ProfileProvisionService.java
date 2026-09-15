package com.ai.mall.member.application.member;

import com.ai.mall.member.domain.exception.DuplicateResourceException;
import com.ai.mall.member.domain.model.member.MemberProfile;
import com.ai.mall.member.domain.repository.MemberProfileRepository;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 会员档案开通服务（CHG-0016）：消费 identity MemberRegistered 事件。
 *
 * <p>双幂等（at-least-once 投递下保证仅一份档案）：
 * <ol>
 *   <li>initialized_event_id 已存在（事件重放）→ provisioned=false，不建行；</li>
 *   <li>member_id 已存在（不同事件重复投递）→ provisioned=false；</li>
 *   <li>插入并发撞 uk_profile_event（或主键）→ 捕获 DuplicateResourceException 返回 false 兜底。</li>
 * </ol>
 * 默认昵称：事件携带昵称优先；为空时按 {@code "会员"+memberId 后 6 位} 本地兜底。
 */
@Service
public class ProfileProvisionService {

    private final MemberProfileRepository profiles;
    private final Clock clock = Clock.systemUTC();

    public ProfileProvisionService(MemberProfileRepository profiles) {
        this.profiles = profiles;
    }

    /**
     * 开通（幂等）。
     *
     * @return true 表示本次新建档案；false 表示档案已存在（事件重放/补偿重复）
     */
    @Transactional
    public boolean provision(ProvisionCommand command) {
        // 第一道幂等：事件 ID
        if (profiles.existsByInitializedEventId(command.eventId())) {
            return false;
        }
        // 第二道幂等：会员已有档案
        if (profiles.existsByMemberId(command.memberId())) {
            return false;
        }

        String nickname = (command.nickname() == null || command.nickname().isBlank())
                ? MemberNicknames.defaultNickname(command.memberId())
                : command.nickname();
        MemberProfile profile = MemberProfile.provision(
                command.memberId(), command.username(), nickname, command.eventId(), Instant.now(clock));
        try {
            profiles.add(profile);
            return true;
        } catch (DuplicateResourceException ex) {
            // 并发重放：唯一键冲突，视为已开通
            return false;
        }
    }

    /**
     * provision 命令（与 identity 事件契约一致）。
     *
     * @param eventId    MemberRegistered 事件 ID（幂等键）
     * @param memberId   会员 ID
     * @param username   注册用户名
     * @param nickname   默认昵称（可空，空则本地兜底计算）
     * @param occurredAt 事件发生时刻（保留字段；当前仅用于日志/审计，不参与建档判定）
     */
    public record ProvisionCommand(String eventId, long memberId, String username, String nickname,
                                   Instant occurredAt) {
    }
}

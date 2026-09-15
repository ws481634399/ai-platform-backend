package com.ai.mall.identity.application.member;

import com.ai.mall.identity.application.exception.UseCaseException;
import com.ai.mall.identity.application.port.PasswordHasher;
import com.ai.mall.identity.domain.exception.DuplicateResourceException;
import com.ai.mall.identity.domain.model.member.MemberAccount;
import com.ai.mall.identity.domain.model.member.MemberRegisteredEvent;
import com.ai.mall.identity.domain.model.member.MemberUsername;
import com.ai.mall.identity.domain.repository.MemberEventOutboxRepository;
import com.ai.mall.identity.domain.repository.MemberUserRepository;
import com.ai.mall.identity.domain.service.MemberPasswordPolicy;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 会员注册应用服务（CHG-0016 / STORY-003-01-01-01）。
 *
 * <p>单库事务编排（无跨库事务）：规则校验 → 归一查重 → BCrypt 哈希 → 建聚合 →
 * 同事务插入 member_user + member_event_outbox(PENDING) → 提交后 afterCommit 立即尝试投递。
 * outbox 插入失败随事务整体回滚，保证无 member_user 半成品残留，同名可重新注册。
 *
 * <p>安全：明文密码只存在于方法参数与哈希调用瞬间，不入事件、不入日志、不出参；
 * 注册响应仅含 memberId。
 */
@Service
public class MemberRegistrationService {

    /** 归一化唯一冲突统一文案，不回显已存在用户名的原始大小写。 */
    static final String USERNAME_EXISTS_MESSAGE = "用户名已存在";

    private final MemberUserRepository members;
    private final MemberEventOutboxRepository outbox;
    private final PasswordHasher passwordHasher;
    private final MemberProvisionRelay relay;
    private final MemberPasswordPolicy passwordPolicy = new MemberPasswordPolicy();
    private final Clock clock = Clock.systemUTC();

    public MemberRegistrationService(MemberUserRepository members, MemberEventOutboxRepository outbox,
                                     PasswordHasher passwordHasher, MemberProvisionRelay relay) {
        this.members = members;
        this.outbox = outbox;
        this.passwordHasher = passwordHasher;
        this.relay = relay;
    }

    /**
     * 注册会员。
     *
     * @return 新建会员 ID（调用方以字符串出参）
     * @throws IllegalArgumentException 用户名/密码规则违例（400 字段级提示）
     * @throws UseCaseException         CONFLICT 用户名已存在（409）
     */
    @Transactional
    public long register(String rawUsername, String rawPassword) {
        // 1. 领域规则：用户名结构 + 大小写归一；密码策略（错误为字段级 400）
        MemberUsername username = MemberUsername.of(rawUsername);
        passwordPolicy.ensureValid(rawPassword);
        Instant now = Instant.now(clock);

        // 2. 预查重（友好路径）；唯一索引为最终兜底，覆盖并发
        if (members.existsByUsernameNorm(username.norm())) {
            throw new UseCaseException(UseCaseException.Kind.CONFLICT, USERNAME_EXISTS_MESSAGE);
        }

        // 3. 建聚合并持久化（密码仅此处经 BCrypt 哈希，strength 与 ADMIN 编码器一致）
        MemberAccount account = MemberAccount.register(username, passwordHasher.hash(rawPassword), now);
        MemberAccount saved;
        try {
            saved = members.add(account);
        } catch (DuplicateResourceException ex) {
            throw new UseCaseException(UseCaseException.Kind.CONFLICT, USERNAME_EXISTS_MESSAGE);
        }

        // 4. 同事务写 outbox（失败整体回滚 → 无半成品账号）
        MemberRegisteredEvent event = MemberRegisteredEvent.create(
                saved.id(), saved.username().value(),
                MemberNicknames.defaultNickname(saved.id()), Instant.now(clock));
        outbox.append(event);

        // 5. 提交后立即投递一次；失败仅留 PENDING，不影响注册成功结果
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    relay.tryDispatchAfterCommit(event.eventId());
                }
            });
        }
        return saved.id();
    }
}

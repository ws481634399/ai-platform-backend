package com.ai.mall.identity.application.port;

import com.ai.mall.identity.domain.model.member.MemberRegisteredEvent;

/**
 * 会员档案开通投递端口（CHG-0016）：identity → mall-member provision 的抽象，
 * 由基础设施层 RestClient 实现（携带 X-Internal-Token）。
 *
 * <p>应用层只依赖端口，不感知 HTTP；任何投递失败（网络/非 2xx/对端业务失败）
 * 一律以异常表达，由 outbox relay 保留 PENDING 并安排重试。
 */
public interface MemberProvisioner {

    /**
     * 投递 MemberRegistered 事件至 mall-member。
     *
     * @throws RuntimeException 投递未成功（对端不可用、401、5xx、provision 业务失败等）
     */
    void provision(MemberRegisteredEvent event);
}

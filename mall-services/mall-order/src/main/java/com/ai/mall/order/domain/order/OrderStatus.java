package com.ai.mall.order.domain.order;

import java.util.Map;

/**
 * 订单状态（CHG-0019，全平台唯一命名）。
 *
 * <p>合法迁移集中在此枚举收口，Service/Controller/Mapper 不得自行解释状态：
 * <pre>
 * PENDING_PAYMENT --PAY--> PAID --SHIP--> SHIPPED --CONFIRM_RECEIPT--> COMPLETED
 * PENDING_PAYMENT --CANCEL--> CANCELLED
 * </pre>
 */
public enum OrderStatus {

    PENDING_PAYMENT,
    PAID,
    SHIPPED,
    COMPLETED,
    CANCELLED;

    /** 各操作唯一合法的来源态与目标态（CREATE 不经此表，创建即 PENDING_PAYMENT）。 */
    private static final Map<OrderOperation, Transition> TRANSITIONS = Map.of(
            OrderOperation.PAY, new Transition(PENDING_PAYMENT, PAID),
            OrderOperation.CANCEL, new Transition(PENDING_PAYMENT, CANCELLED),
            OrderOperation.SHIP, new Transition(PAID, SHIPPED),
            OrderOperation.CONFIRM_RECEIPT, new Transition(SHIPPED, COMPLETED));

    /** 判定当前状态执行某操作后的语义：可迁移 / 已是目标态（幂等重复）/ 非法。 */
    public TransitionOutcome evaluate(OrderOperation operation) {
        Transition transition = TRANSITIONS.get(operation);
        if (transition == null) {
            return TransitionOutcome.ILLEGAL;
        }
        if (this == transition.from()) {
            return TransitionOutcome.MUTATED;
        }
        if (this == transition.to()) {
            return TransitionOutcome.ALREADY_TARGET;
        }
        return TransitionOutcome.ILLEGAL;
    }

    public static OrderStatus targetOf(OrderOperation operation) {
        Transition transition = TRANSITIONS.get(operation);
        return transition == null ? null : transition.to();
    }

    public static OrderStatus requiredSourceOf(OrderOperation operation) {
        Transition transition = TRANSITIONS.get(operation);
        return transition == null ? null : transition.from();
    }

    private record Transition(OrderStatus from, OrderStatus to) {
    }

    /** 聚合状态迁移判定结果。 */
    public enum TransitionOutcome {
        /** 来源态匹配：本次请求应产生一次真实迁移与副作用。 */
        MUTATED,
        /** 已处于目标态：重复请求，幂等成功，不再迁移、不再产生副作用。 */
        ALREADY_TARGET,
        /** 其他状态：状态机拒绝。 */
        ILLEGAL
    }
}

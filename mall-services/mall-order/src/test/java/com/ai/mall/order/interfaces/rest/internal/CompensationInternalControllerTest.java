package com.ai.mall.order.interfaces.rest.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.ai.mall.common.core.result.UnifyResult;
import com.ai.mall.order.application.order.port.OrderCompensationPort;
import com.ai.mall.order.interfaces.rest.internal.CompensationInternalController.CompensationRequest;
import com.ai.mall.order.interfaces.rest.internal.CompensationInternalController.CompensationType;
import com.ai.mall.order.interfaces.rest.internal.CompensationInternalController.Line;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 补偿登记内部端点测试（TC-005）。
 */
@ExtendWith(MockitoExtension.class)
class CompensationInternalControllerTest {

    @Mock
    private OrderCompensationPort compensationPort;

    private CompensationRequest request(CompensationType type) {
        return new CompensationRequest("9500", "ON7001", type, "消费失败登记补偿",
                List.of(new Line(2001L, 2, "ON7001:2001")));
    }

    @Test
    void registerConfirmDispatchesToConfirmPort() {
        UnifyResult<String> result = new CompensationInternalController(compensationPort)
                .register(request(CompensationType.INVENTORY_CONFIRM_DEDUCT));

        assertThat(result.getData()).isEqualTo("accepted");
        verify(compensationPort, times(1))
                .enqueueInventoryConfirm(anyString(), anyList(), anyString());
    }

    @Test
    void registerReleaseDispatchesToReleasePort() {
        new CompensationInternalController(compensationPort)
                .register(request(CompensationType.INVENTORY_RELEASE));

        verify(compensationPort, times(1))
                .enqueueInventoryRelease(anyString(), anyList(), anyString());
    }
}

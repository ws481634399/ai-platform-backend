package com.ai.mall.order.interfaces.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ai.mall.common.web.exception.BusinessException;
import com.ai.mall.order.application.order.OrderCancelService;
import com.ai.mall.order.application.order.PaymentService;
import com.ai.mall.order.application.order.port.CartSelectionPort;
import com.ai.mall.order.application.order.port.InventoryPort;
import com.ai.mall.order.application.order.port.MemberAddressPort;
import com.ai.mall.order.application.order.port.ProductSkuPort;
import com.ai.mall.order.domain.order.OrderErrorCode;
import com.ai.mall.order.support.AbstractRedisIntegrationTest;
import com.ai.mall.order.support.ApiTestSecurityConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 订单交易闭环全链路集成测试（CHG-0019 Integration Gate 十场景）。
 *
 * <p>真实 H2（Flyway V1/V2）+ 真实 Redis（GETDEL 令牌）+ 真实订单/补偿仓储与服务，
 * 仅 mock 四个出站端口（product/inventory/member/cart）。覆盖：
 * 成功交易、库存不足、价格变化、篡改指纹、重复下单/支付/取消、支付取消竞争、越权 404、
 * 锁成功单失败（含补偿登记+人工重试）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@org.springframework.context.annotation.Import(ApiTestSecurityConfig.class)
class OrderApiTest extends AbstractRedisIntegrationTest {

    private static final String MEMBER_A = "5001";
    private static final String MEMBER_B = "5002";
    private static final String ADMIN = "1002";
    private static final long SKU1 = 6001L;
    private static final long SKU2 = 6002L;
    private static final long SKU_OFF = 6003L;
    private static final long ADDRESS = 8001L;

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtEncoder jwtEncoder;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private StringRedisTemplate redisTemplate;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PaymentService paymentService;
    @Autowired private OrderCancelService orderCancelService;

    @MockitoBean private ProductSkuPort productSkuPort;
    @MockitoBean private InventoryPort inventoryPort;
    @MockitoBean private MemberAddressPort memberAddressPort;
    @MockitoBean private CartSelectionPort cartSelectionPort;

    /** 可变桩状态：单价（分）。 */
    private final Map<Long, Long> priceMap = new HashMap<>();
    /** 可变桩状态：可售库存。 */
    private final Map<Long, Long> availabilityMap = new HashMap<>();
    /** 已锁定预留（reservationId 占位，模拟库存侧）。 */
    private final List<String> lockedReservations = java.util.Collections.synchronizedList(new ArrayList<>());
    private final List<String> releasedReservations = java.util.Collections.synchronizedList(new ArrayList<>());
    private final List<String> confirmedReservations = java.util.Collections.synchronizedList(new ArrayList<>());
    /** 配置：锁定即抛库存不足的 SKU。 */
    private final AtomicReference<Long> lockThrowInsufficientSku = new AtomicReference<>();
    /** 配置：release 调用即失败的 reservationId 前缀片段（sku 标识）。 */
    private final AtomicReference<Long> releaseThrowSku = new AtomicReference<>();

    @BeforeEach
    void reset() {
        jdbc.execute("DELETE FROM compensation_task");
        jdbc.execute("DELETE FROM order_status_history");
        jdbc.execute("DELETE FROM order_item");
        jdbc.execute("DELETE FROM orders");
        java.util.Set<String> tokenKeys = redisTemplate.keys("order:submit-token:*");
        if (tokenKeys != null && !tokenKeys.isEmpty()) {
            redisTemplate.delete(tokenKeys);
        }
        lockedReservations.clear();
        releasedReservations.clear();
        confirmedReservations.clear();
        lockThrowInsufficientSku.set(null);
        releaseThrowSku.set(null);
        priceMap.clear();
        availabilityMap.clear();
        priceMap.put(SKU1, 39900L);
        priceMap.put(SKU2, 19900L);
        availabilityMap.put(SKU1, 50L);
        availabilityMap.put(SKU2, 50L);

        org.mockito.Mockito.reset(productSkuPort, inventoryPort, memberAddressPort, cartSelectionPort);
        stubProduct();
        stubInventory();
        stubMember();
        stubCart();
    }

    // ---------- 桩配置 ----------

    private void stubProduct() {
        when(productSkuPort.batchSnapshots(anyList())).thenAnswer(invocation -> {
            List<Long> skuIds = invocation.getArgument(0);
            return skuIds.stream().map(skuId -> {
                if (skuId == SKU_OFF || !priceMap.containsKey(skuId)) {
                    return new ProductSkuPort.SkuSnapshot(null, null, "OFF_SHELF", skuId, null,
                            "DISABLED", null, null, Map.of(), false);
                }
                return new ProductSkuPort.SkuSnapshot(9000L + skuId, "商品" + skuId, "ON_SALE", skuId,
                        "SKU-" + skuId, "ENABLED", priceMap.get(skuId),
                        "https://cdn.example.com/" + skuId + ".png", Map.of("颜色", "黑"), true);
            }).toList();
        });
    }

    private void stubInventory() {
        when(inventoryPort.availability(anyList())).thenAnswer(invocation ->
                invocation.<List<Long>>getArgument(0).stream()
                        .map(skuId -> new InventoryPort.Availability(skuId,
                                availabilityMap.getOrDefault(skuId, 0L)))
                        .toList());
        org.mockito.Mockito.doAnswer(invocation -> {
            String reservationId = invocation.getArgument(0);
            Long skuId = invocation.getArgument(1);
            Long failSku = lockThrowInsufficientSku.get();
            if (failSku != null && failSku.equals(skuId)) {
                throw new BusinessException(OrderErrorCode.INSUFFICIENT_STOCK, HttpStatus.CONFLICT, "库存不足");
            }
            lockedReservations.add(reservationId);
            return null;
        }).when(inventoryPort).lock(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong());
        org.mockito.Mockito.doAnswer(invocation -> {
            String reservationId = invocation.getArgument(0);
            Long failSku = releaseThrowSku.get();
            if (failSku != null && reservationId.contains(":" + failSku)) {
                throw new BusinessException(OrderErrorCode.DEPENDENCY_UNAVAILABLE,
                        HttpStatus.SERVICE_UNAVAILABLE, "库存服务不可用");
            }
            releasedReservations.add(reservationId);
            return null;
        }).when(inventoryPort).release(org.mockito.ArgumentMatchers.anyString());
        org.mockito.Mockito.doAnswer(invocation -> {
            confirmedReservations.add(invocation.getArgument(0));
            return null;
        }).when(inventoryPort).confirm(org.mockito.ArgumentMatchers.anyString());
    }

    private void stubMember() {
        when(memberAddressPort.findOwnedAddress(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong())).thenAnswer(invocation -> {
            long memberId = invocation.getArgument(0);
            long addressId = invocation.getArgument(1);
            if (memberId == Long.parseLong(MEMBER_A) && addressId == ADDRESS) {
                return java.util.Optional.of(new MemberAddressPort.AddressSnapshot(ADDRESS,
                        Long.parseLong(MEMBER_A), "张三", "13800000000", "浙江省", "杭州市",
                        "西湖区", "文三路 100 号", "310000", true));
            }
            return java.util.Optional.empty();
        });
    }

    private void stubCart() {
        when(cartSelectionPort.selectedItems(Long.parseLong(MEMBER_A)))
                .thenReturn(List.of(new CartSelectionPort.SelectedItem(SKU1, 2)));
    }

    // ---------- REQ-M4-001 预览 ----------

    @Test
    @DisplayName("预览：BUY_NOW 现货可下单 → 签发 submitToken 且金额由服务端计算")
    void previewBuyNowOk() throws Exception {
        mockMvc.perform(post("/api/mall/orders/preview")
                        .header("Authorization", "Bearer " + memberToken(MEMBER_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"source":"BUY_NOW","addressId":"8001",
                                 "items":[{"skuId":"6001","quantity":2}]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.availableToSubmit").value(true))
                .andExpect(jsonPath("$.data.submitToken").isNotEmpty())
                .andExpect(jsonPath("$.data.goodsAmountFen").value(79800))
                .andExpect(jsonPath("$.data.payAmountFen").value(79800))
                .andExpect(jsonPath("$.data.items[0].stockStatus").value("OK"))
                .andExpect(jsonPath("$.data.items[0].issueCodes.length()").value(0));
    }

    @Test
    @DisplayName("预览：CART 来源以购物车勾选项为准，忽略请求体 items")
    void previewCartUsesCartSelection() throws Exception {
        mockMvc.perform(post("/api/mall/orders/preview")
                        .header("Authorization", "Bearer " + memberToken(MEMBER_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"source":"CART","addressId":"8001",
                                 "items":[{"skuId":"6002","quantity":9}]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].skuId").value("6001"))
                .andExpect(jsonPath("$.data.items[0].quantity").value(2))
                .andExpect(jsonPath("$.data.availableToSubmit").value(true));
    }

    @Test
    @DisplayName("预览：下架/无库存 → 不签发令牌且带 issueCodes")
    void previewBlockedWhenUnsableOrOutOfStock() throws Exception {
        availabilityMap.put(SKU1, 0L);
        mockMvc.perform(post("/api/mall/orders/preview")
                        .header("Authorization", "Bearer " + memberToken(MEMBER_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"source":"BUY_NOW","addressId":"8001",
                                 "items":[{"skuId":"6001","quantity":1},{"skuId":"6003","quantity":1}]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.availableToSubmit").value(false))
                .andExpect(jsonPath("$.data.submitToken").isEmpty())
                .andExpect(jsonPath("$.data.items[0].issueCodes[0]").value("OUT_OF_STOCK"))
                .andExpect(jsonPath("$.data.items[0].stockStatus").value("OUT_OF_STOCK"))
                .andExpect(jsonPath("$.data.items[1].issueCodes[0]").value("SKU_NOT_SALABLE"));
    }

    @Test
    @DisplayName("预览：地址不存在/不归属 → 不可下单（不泄露归属差异）")
    void previewAddressNotOwned() throws Exception {
        mockMvc.perform(post("/api/mall/orders/preview")
                        .header("Authorization", "Bearer " + memberToken(MEMBER_B))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"source":"BUY_NOW","addressId":"8001",
                                 "items":[{"skuId":"6001","quantity":1}]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.availableToSubmit").value(false))
                .andExpect(jsonPath("$.data.address").isEmpty());
    }

    // ---------- REQ-M4-001/002/003 成功交易闭环 ----------

    @Test
    @DisplayName("Gate-1 成功交易：预览→下单→重复支付→后台发货→确认收货，库存 lock/confirm 各一次")
    void fullHappyPath() throws Exception {
        String token = previewToken("BUY_NOW", """
                [{"skuId":"6001","quantity":2}]""", MEMBER_A);
        JsonNode order = createOrder(token, "BUY_NOW", """
                [{"skuId":"6001","quantity":2}]""", MEMBER_A);
        String orderNo = order.get("orderNo").asText();
        assertEquals("PENDING_PAYMENT", order.get("status").asText());
        assertEquals(79800, order.get("payAmountFen").asLong());
        assertTrue(lockedReservations.contains(orderNo + ":" + SKU1), "应按 orderNo:skuId 锁定库存");

        // 支付 + 重复支付（幂等，confirm 仅一次）
        payOrder(orderNo, MEMBER_A).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PAID"));
        payOrder(orderNo, MEMBER_A).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PAID"));
        assertEquals(1, confirmedReservations.size(), "重复支付不得重复扣减");

        // 会员不能发货
        mockMvc.perform(post("/api/admin/orders/" + orderNo + "/ship")
                        .header("Authorization", "Bearer " + memberToken(MEMBER_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"deliveryCompany":"SF","trackingNo":"SF123"}"""))
                .andExpect(status().isForbidden());

        // 无权限管理员 403
        mockMvc.perform(post("/api/admin/orders/" + orderNo + "/ship")
                        .header("Authorization", "Bearer " + adminToken(List.of()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"deliveryCompany":"SF","trackingNo":"SF123"}"""))
                .andExpect(status().isForbidden());

        // 有权限发货
        mockMvc.perform(post("/api/admin/orders/" + orderNo + "/ship")
                        .header("Authorization", "Bearer " + adminToken(List.of("order:ship")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"deliveryCompany":"SF","trackingNo":"SF123"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SHIPPED"))
                .andExpect(jsonPath("$.data.deliveryCompany").value("SF"))
                .andExpect(jsonPath("$.data.trackingNo").value("SF123"));

        // 确认收货
        mockMvc.perform(post("/api/mall/orders/" + orderNo + "/confirm-receipt")
                        .header("Authorization", "Bearer " + memberToken(MEMBER_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));

        // 详情含完整状态轨迹
        mockMvc.perform(get("/api/mall/orders/" + orderNo)
                        .header("Authorization", "Bearer " + memberToken(MEMBER_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.statusHistory.length()").value(4));
    }

    @Test
    @DisplayName("Gate-3 价格变化：预览后商品调价，下单以服务端二次核价为准")
    void priceChangedBetweenPreviewAndCreate() throws Exception {
        String token = previewToken("BUY_NOW", """
                [{"skuId":"6001","quantity":1}]""", MEMBER_A);
        priceMap.put(SKU1, 29900L);
        JsonNode order = createOrder(token, "BUY_NOW", """
                [{"skuId":"6001","quantity":1}]""", MEMBER_A);
        assertEquals(29900, order.get("payAmountFen").asLong());
    }

    @Test
    @DisplayName("Gate-4 篡改：下单行数量与令牌指纹不一致 → 400 B0406（前端改价无入口，金额一律服务端重算）")
    void tamperedFingerprintRejected() throws Exception {
        String token = previewToken("BUY_NOW", """
                [{"skuId":"6001","quantity":1}]""", MEMBER_A);
        mockMvc.perform(post("/api/mall/orders").header("Authorization", "Bearer " + memberToken(MEMBER_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(token, "BUY_NOW", """
                                [{"skuId":"6001","quantity":3}]""")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("B0406"));
        verify(inventoryPort, never()).lock(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    @DisplayName("Gate-5a 重复下单：同一 submitToken 第二次消费 → 400 B0406，不产生第二单")
    void duplicateSubmitRejected() throws Exception {
        String token = previewToken("BUY_NOW", """
                [{"skuId":"6001","quantity":1}]""", MEMBER_A);
        createOrder(token, "BUY_NOW", """
                [{"skuId":"6001","quantity":1}]""", MEMBER_A);
        mockMvc.perform(post("/api/mall/orders").header("Authorization", "Bearer " + memberToken(MEMBER_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(token, "BUY_NOW", """
                                [{"skuId":"6001","quantity":1}]""")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("B0406"));
        Integer orderCount = jdbc.queryForObject("SELECT COUNT(*) FROM orders", Integer.class);
        assertEquals(1, orderCount);
    }

    @Test
    @DisplayName("Gate-2 库存不足：二次核价发现库存为 0 → 409 B0404，不锁库存不建单")
    void insufficientStockOnCreate() throws Exception {
        String token = previewToken("BUY_NOW", """
                [{"skuId":"6001","quantity":1}]""", MEMBER_A);
        availabilityMap.put(SKU1, 0L);
        mockMvc.perform(post("/api/mall/orders").header("Authorization", "Bearer " + memberToken(MEMBER_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(token, "BUY_NOW", """
                                [{"skuId":"6001","quantity":1}]""")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("B0404"));
        verify(inventoryPort, never()).lock(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    @DisplayName("Gate-8 锁成功单失败：锁库存中途失败 → 已锁行立即释放；释放再失败登记补偿并可人工重试成功")
    void lockMidwayFailureReleasesAndCompensates() throws Exception {
        // 第二行锁库存抛库存不足
        lockThrowInsufficientSku.set(SKU2);
        // 第一行的释放也失败（模拟库存服务瞬时不可用）→ 必须落补偿任务
        releaseThrowSku.set(SKU1);
        String token = previewToken("BUY_NOW",
                """
                        [{"skuId":"6001","quantity":1},{"skuId":"6002","quantity":1}]""", MEMBER_A);
        mockMvc.perform(post("/api/mall/orders").header("Authorization", "Bearer " + memberToken(MEMBER_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(token, "BUY_NOW",
                                """
                                        [{"skuId":"6001","quantity":1},{"skuId":"6002","quantity":1}]""")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("B0404"));
        Integer orderCount = jdbc.queryForObject("SELECT COUNT(*) FROM orders", Integer.class);
        assertEquals(0, orderCount, "锁库存失败不得落单");

        // 补偿台可见 PENDING 任务（需要 order:compensation 权限）
        mockMvc.perform(get("/api/admin/compensations")
                        .header("Authorization", "Bearer " + adminToken(List.of())))
                .andExpect(status().isForbidden());
        MvcResult pageResult = mockMvc.perform(get("/api/admin/compensations")
                        .header("Authorization", "Bearer " + adminToken(List.of("order:compensation"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records.length()").value(1))
                .andExpect(jsonPath("$.data.records[0].status").value("PENDING"))
                .andReturn();
        long compensationId = objectMapper.readTree(pageResult.getResponse().getContentAsString())
                .path("data").path("records").get(0).get("id").asLong();

        // 库存恢复后人工重试 → SUCCESS，且最终完成释放
        releaseThrowSku.set(null);
        mockMvc.perform(post("/api/admin/compensations/" + compensationId + "/retry")
                        .header("Authorization", "Bearer " + adminToken(List.of("order:compensation"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCESS"));
        assertTrue(releasedReservations.stream().anyMatch(r -> r.endsWith(":" + SKU1)),
                "补偿重试应完成 SKU1 预留释放");
    }

    @Test
    @DisplayName("CART 来源下单成功后按令牌行清理购物车")
    void cartSourceClearsPurchasedItems() throws Exception {
        String token = previewToken("CART", "[]", MEMBER_A);
        JsonNode order = createOrder(token, "CART", "[]", MEMBER_A);
        assertEquals("CART", order.get("source").asText());
        verify(cartSelectionPort, times(1)).deleteItems(eq(Long.parseLong(MEMBER_A)),
                org.mockito.ArgumentMatchers.anyList());
    }

    // ---------- REQ-M4-002 取消/竞争/幂等 ----------

    @Test
    @DisplayName("Gate-6 取消：待支付订单取消成功并释放库存；重复取消幂等（release 仅一次）")
    void cancelAndIdempotent() throws Exception {
        String orderNo = createPaidOrPendingOrder("BUY_NOW", 1);
        mockMvc.perform(post("/api/mall/orders/" + orderNo + "/cancel")
                        .header("Authorization", "Bearer " + memberToken(MEMBER_A))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"reason":"不想买了"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.cancelReason").value("不想买了"));
        mockMvc.perform(post("/api/mall/orders/" + orderNo + "/cancel")
                        .header("Authorization", "Bearer " + memberToken(MEMBER_A))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
        assertEquals(1, releasedReservations.size(), "重复取消不得重复释放");
    }

    @Test
    @DisplayName("Gate-7 支付/取消状态冲突：已取消不能支付；已支付不能取消，均 409 B0407")
    void payCancelStateConflict() throws Exception {
        String cancelledOrder = createPaidOrPendingOrder("BUY_NOW", 1);
        mockMvc.perform(post("/api/mall/orders/" + cancelledOrder + "/cancel")
                        .header("Authorization", "Bearer " + memberToken(MEMBER_A)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/mall/orders/" + cancelledOrder + "/pay")
                        .header("Authorization", "Bearer " + memberToken(MEMBER_A)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("B0407"));

        String paidOrder = createPaidOrPendingOrder("BUY_NOW", 1);
        mockMvc.perform(post("/api/mall/orders/" + paidOrder + "/pay")
                        .header("Authorization", "Bearer " + memberToken(MEMBER_A)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/mall/orders/" + paidOrder + "/cancel")
                        .header("Authorization", "Bearer " + memberToken(MEMBER_A)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("B0407"));
    }

    @Test
    @DisplayName("Gate-7 支付/取消真并发：CAS 仲裁恰一方成功，库存副作用与终态一致")
    void payCancelConcurrentRace() throws Exception {
        String orderNo = createPaidOrPendingOrder("BUY_NOW", 1);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicReference<Throwable> payError = new AtomicReference<>();
        AtomicReference<Throwable> cancelError = new AtomicReference<>();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            pool.submit(() -> {
                ready.countDown();
                await(start);
                try {
                    paymentService.pay(Long.parseLong(MEMBER_A), orderNo);
                } catch (Throwable t) {
                    payError.set(t);
                }
            });
            pool.submit(() -> {
                ready.countDown();
                await(start);
                try {
                    orderCancelService.cancel(Long.parseLong(MEMBER_A), orderNo, null);
                } catch (Throwable t) {
                    cancelError.set(t);
                }
            });
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
        } finally {
            pool.shutdown();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        }
        boolean payWon = payError.get() == null;
        boolean cancelWon = cancelError.get() == null;
        assertTrue(payWon ^ cancelWon, "支付与取消必须恰有一方获胜");
        if (payWon) {
            assertTrue(cancelError.get() instanceof BusinessException);
            assertEquals(1, confirmedReservations.size());
            assertTrue(releasedReservations.isEmpty());
        } else {
            assertTrue(payError.get() instanceof BusinessException);
            assertEquals(1, releasedReservations.size());
            assertTrue(confirmedReservations.isEmpty());
        }
    }

    // ---------- REQ-M4-003 查询/越权/admin ----------

    @Test
    @DisplayName("Gate-9 越权：他人订单详情/支付/取消/收货统一 404（不泄露单号存在性）")
    void crossMemberAccess404() throws Exception {
        String orderNo = createPaidOrPendingOrder("BUY_NOW", 1);
        mockMvc.perform(get("/api/mall/orders/" + orderNo)
                        .header("Authorization", "Bearer " + memberToken(MEMBER_B)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("B0401"));
        mockMvc.perform(post("/api/mall/orders/" + orderNo + "/pay")
                        .header("Authorization", "Bearer " + memberToken(MEMBER_B)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/mall/orders/" + orderNo + "/cancel")
                        .header("Authorization", "Bearer " + memberToken(MEMBER_B)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("会员订单分页仅返回自己的订单；admin 可按会员/状态筛选")
    void orderListIsolationAndAdminFilter() throws Exception {
        createPaidOrPendingOrder("BUY_NOW", 1);
        mockMvc.perform(get("/api/mall/orders").header("Authorization", "Bearer " + memberToken(MEMBER_B)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));
        mockMvc.perform(get("/api/mall/orders").header("Authorization", "Bearer " + memberToken(MEMBER_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].items.length()").value(1));
        mockMvc.perform(get("/api/admin/orders").header("Authorization", "Bearer " + adminToken(List.of())))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/orders?memberId=5001&status=PENDING_PAYMENT")
                        .header("Authorization", "Bearer " + adminToken(List.of("order:list"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    @DisplayName("无 Token → 401；会员 JWT 访问 /api/admin → 403")
    void authBoundaries() throws Exception {
        mockMvc.perform(get("/api/mall/orders")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/orders")
                        .header("Authorization", "Bearer " + memberToken(MEMBER_A)))
                .andExpect(status().isForbidden());
    }

    // ---------- helpers ----------

    private String previewToken(String source, String itemsJson, String memberId) throws Exception {
        String body;
        if (itemsJson.isBlank() || "[]".equals(itemsJson)) {
            body = "{\"source\":\"" + source + "\",\"addressId\":\"8001\"}";
        } else {
            body = "{\"source\":\"" + source + "\",\"addressId\":\"8001\",\"items\":" + itemsJson + "}";
        }
        MvcResult result = mockMvc.perform(post("/api/mall/orders/preview")
                        .header("Authorization", "Bearer " + memberToken(memberId))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.availableToSubmit").value(true))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("submitToken").asText();
    }

    private JsonNode createOrder(String token, String source, String itemsJson, String memberId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/mall/orders")
                        .header("Authorization", "Bearer " + memberToken(memberId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(token, source, itemsJson)))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private String createBody(String token, String source, String itemsJson) {
        String items = (itemsJson == null || itemsJson.isBlank() || "[]".equals(itemsJson))
                ? "" : ",\"items\":" + itemsJson;
        return "{\"submitToken\":\"" + token + "\",\"addressId\":\"8001\",\"source\":\"" + source + "\"" + items + "}";
    }

    /** 预览+下单一笔待支付订单，返回 orderNo。 */
    private String createPaidOrPendingOrder(String source, int quantity) throws Exception {
        String items = "[{\"skuId\":\"6001\",\"quantity\":" + quantity + "}]";
        String token = previewToken(source, items, MEMBER_A);
        return createOrder(token, source, items, MEMBER_A).get("orderNo").asText();
    }

    private org.springframework.test.web.servlet.ResultActions payOrder(String orderNo, String memberId)
            throws Exception {
        return mockMvc.perform(post("/api/mall/orders/" + orderNo + "/pay")
                .header("Authorization", "Bearer " + memberToken(memberId)));
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }

    private String memberToken(String memberId) {
        return token(memberId, "MEMBER", List.of());
    }

    private String adminToken(List<String> permissions) {
        return token(ADMIN, "ADMIN", permissions);
    }

    private String token(String subject, String subjectType, List<String> permissions) {
        var builder = JwtClaimsSet.builder()
                .subject(subject).claim("username", "u" + subject)
                .claim("subject_type", subjectType).claim("auth_version", 1)
                .audience(List.of(ApiTestSecurityConfig.AUDIENCE)).issuer(ApiTestSecurityConfig.ISSUER)
                .issuedAt(Instant.now().minusSeconds(5))
                .expiresAt(Instant.now().plusSeconds(600));
        if (permissions != null && !permissions.isEmpty()) {
            builder.claim("permissions", permissions);
        }
        return jwtEncoder.encode(JwtEncoderParameters.from(builder.build())).getTokenValue();
    }
}

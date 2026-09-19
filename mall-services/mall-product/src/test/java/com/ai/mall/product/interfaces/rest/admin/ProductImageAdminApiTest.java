package com.ai.mall.product.interfaces.rest.admin;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ai.mall.common.core.image.ImageFormat;
import com.ai.mall.common.web.exception.BusinessException;
import com.ai.mall.product.application.image.ImageScene;
import com.ai.mall.product.application.port.ProductImageStorage;
import com.ai.mall.product.domain.shared.ProductErrorCode;
import com.ai.mall.product.support.ApiTestSecurityConfig;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 商品图片上传管理 API 集成测试（CHG-0023 STORY-007-01-01-02 / TC-003~TC-012）。
 *
 * <p>H2 + 真实安全链；{@link ProductImageStorage} 以 MockitoBean 替换（不触达 MinIO）。
 * MockMvc 不经 servlet 容器 multipart 大小拦截，超限位由应用内字节校验兜底（A2102）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ApiTestSecurityConfig.class)
@DisplayName("商品图片上传管理 API")
class ProductImageAdminApiTest {

    private static final String ENDPOINT = "/api/admin/product-images";

    /** PNG 魔数头 + 任意尾字节（detect 只校验头部）。 */
    private static final byte[] PNG = new byte[]{
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3};

    /** GIF89a 头：白名单不含 gif，伪装图片必须拒绝。 */
    private static final byte[] GIF = "GIF89a rest is irrelevant padding".getBytes();

    @Autowired MockMvc mockMvc;
    @Autowired JwtEncoder encoder;

    @MockitoBean ProductImageStorage imageStorage;

    // ---------- TC-003：鉴权 ----------

    @Test
    @DisplayName("TC-003a 无 token → 401 统一信封")
    void noTokenReturns401() throws Exception {
        mockMvc.perform(uploadRequest(null, "BRAND", pngFile("x.png", PNG)))
                .andExpect(status().isUnauthorized());
        verify(imageStorage, never()).upload(any(), any(), any());
    }

    @Test
    @DisplayName("TC-003b token 无图片相关权限码 → 403")
    void emptyAuthoritiesReturns403() throws Exception {
        mockMvc.perform(uploadRequest(token(List.of()), "BRAND", pngFile("x.png", PNG)))
                .andExpect(status().isForbidden());
        verify(imageStorage, never()).upload(any(), any(), any());
    }

    // ---------- TC-004：权限并集 ----------

    @Test
    @DisplayName("TC-004a 仅持 product:brand:update 可上传 BRAND 素材并返回 URL")
    void brandUpdateAuthorityAllowed() throws Exception {
        when(imageStorage.upload(eq(ImageScene.BRAND), any(), eq(ImageFormat.PNG)))
                .thenReturn("http://localhost:9000/mall-product/brand/u.png");

        mockMvc.perform(uploadRequest(token(List.of("product:brand:update")), "BRAND",
                        pngFile("x.png", PNG)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.url").value("http://localhost:9000/mall-product/brand/u.png"));

        verify(imageStorage).upload(eq(ImageScene.BRAND), any(), eq(ImageFormat.PNG));
    }

    @Test
    @DisplayName("TC-004b 仅持 product:product:update 可上传 PRODUCT 素材并返回 URL")
    void productUpdateAuthorityAllowed() throws Exception {
        when(imageStorage.upload(eq(ImageScene.PRODUCT), any(), eq(ImageFormat.PNG)))
                .thenReturn("http://localhost:9000/mall-product/product/v.png");

        mockMvc.perform(uploadRequest(token(List.of("product:product:update")), "PRODUCT",
                        pngFile("v.png", PNG)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.url").value("http://localhost:9000/mall-product/product/v.png"));

        verify(imageStorage).upload(eq(ImageScene.PRODUCT), any(), eq(ImageFormat.PNG));
    }

    // ---------- TC-005~TC-008：参数校验（存储端口不得被触达） ----------

    @Test
    @DisplayName("TC-005 空文件 → 400 A2101，不触达存储")
    void emptyFileRejected() throws Exception {
        MockMultipartFile empty = new MockMultipartFile("file", "x.png", "image/png", new byte[0]);

        mockMvc.perform(uploadRequest(token(List.of("product:brand:update")), "BRAND", empty))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("A2101"));
        verify(imageStorage, never()).upload(any(), any(), any());
    }

    @Test
    @DisplayName("TC-006 非法 scene（缺失/乱码）→ 400 A2103，不触达存储")
    void invalidSceneRejected() throws Exception {
        MockHttpServletRequestBuilder request = multipart(ENDPOINT)
                .file(pngFile("x.png", PNG))
                .header("Authorization", "Bearer " + token(List.of("product:brand:update")));
        // 不带 scene 参数

        mockMvc.perform(request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("A2103"));

        mockMvc.perform(uploadRequest(token(List.of("product:brand:update")), "AVATAR",
                        pngFile("x.png", PNG)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("A2103"));
        verify(imageStorage, never()).upload(any(), any(), any());
    }

    @Test
    @DisplayName("TC-007 伪装 gif（魔数非白名单）→ 400 A2101，不触达存储")
    void disguisedGifRejected() throws Exception {
        mockMvc.perform(uploadRequest(token(List.of("product:brand:update")), "BRAND",
                        pngFile("x.png", GIF)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("A2101"));
        verify(imageStorage, never()).upload(any(), any(), any());
    }

    @Test
    @DisplayName("TC-008 超过 2MB 上限 → 400 A2102，不触达存储")
    void oversizeRejected() throws Exception {
        byte[] oversized = new byte[2 * 1024 * 1024 + 1];
        oversized[0] = (byte) 0x89;
        oversized[1] = 0x50;

        mockMvc.perform(uploadRequest(token(List.of("product:brand:update")), "BRAND",
                        pngFile("big.png", oversized)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("A2102"));
        verify(imageStorage, never()).upload(any(), any(), any());
    }

    // ---------- TC-009：存储故障 ----------

    @Test
    @DisplayName("TC-009 存储端口故障 → 503 S2101 统一信封，不泄漏内部细节")
    void storageFailureReturns503() throws Exception {
        when(imageStorage.upload(any(), any(), any()))
                .thenThrow(new BusinessException(ProductErrorCode.STORAGE_UNAVAILABLE,
                        HttpStatus.SERVICE_UNAVAILABLE));

        mockMvc.perform(uploadRequest(token(List.of("product:brand:update")), "BRAND",
                        pngFile("x.png", PNG)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("S2101"))
                .andExpect(jsonPath("$.message").value("图片存储暂不可用，请稍后重试"));
    }

    // ---------- helpers ----------

    private MockMultipartFile pngFile(String filename, byte[] content) {
        return new MockMultipartFile("file", filename, "image/png", content);
    }

    private MockHttpServletRequestBuilder uploadRequest(String bearer, String scene,
                                                         MockMultipartFile file) {
        MockHttpServletRequestBuilder builder = multipart(ENDPOINT)
                .file(file)
                .param("scene", scene);
        if (bearer != null) {
            builder.header("Authorization", "Bearer " + bearer);
        }
        return builder;
    }

    /** 与 BrandAdminApiTest 同款 JWT 签发 helper。 */
    private String token(List<String> permissions) {
        var claims = JwtClaimsSet.builder()
                .subject("1002").claim("username", "image_admin")
                .claim("subject_type", "ADMIN").claim("auth_version", 1)
                .audience(List.of("mall-admin-api")).issuer("ai-platform")
                .issuedAt(Instant.now().minusSeconds(5))
                .expiresAt(Instant.now().plusSeconds(600));
        if (permissions != null && !permissions.isEmpty()) {
            claims.claim("permissions", permissions);
        }
        return encoder.encode(JwtEncoderParameters.from(claims.build())).getTokenValue();
    }
}

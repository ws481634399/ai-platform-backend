package com.ai.mall.product.domain.product;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

/**
 * 规格组合哈希：规格按 name 字典序排序后拼接 name=value，SHA-256 生成稳定哈希，
 * 保证同商品内规格组合唯一（与顺序无关）。
 */
public record SpecificationHash(String value) {

    public static SpecificationHash compute(List<Specification> specs) {
        if (specs == null || specs.isEmpty()) {
            throw new IllegalArgumentException("SKU 规格不能为空");
        }
        String raw = specs.stream()
                .sorted(Comparator.comparing(Specification::name))
                .map(s -> s.name() + "=" + s.value())
                .reduce((a, b) -> a + "&" + b)
                .orElse("");
        return new SpecificationHash(sha256Hex(raw));
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 算法不可用", e);
        }
    }
}

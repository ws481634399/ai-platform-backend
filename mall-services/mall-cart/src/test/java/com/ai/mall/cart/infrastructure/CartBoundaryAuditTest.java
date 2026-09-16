package com.ai.mall.cart.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 跨服务边界审计（CHG-0018 AC-021）：mall-cart 对 product/inventory 仅经内部契约 API
 * （RestClient）访问，禁止任何库表直查能力进入本模块——pom 不得引入持久化/JDBC 依赖，
 * 主干代码不得出现 JDBC/MyBatis/JPA/Spring JDBC import。
 */
class CartBoundaryAuditTest {

    private static final List<String> FORBIDDEN_POM_TOKENS = List.of(
            "mysql-connector", "mybatis", "flyway", "spring-boot-starter-jdbc",
            "spring-jdbc", "jakarta.persistence", "postgresql", "mariadb");

    private static final List<String> FORBIDDEN_IMPORTS = List.of(
            "import java.sql.",
            "import jakarta.persistence.",
            "import javax.sql.",
            "import org.mybatis",
            "import org.apache.ibatis",
            "import org.springframework.jdbc.",
            "import org.springframework.orm.");

    @Test
    @DisplayName("AC-021 pom 无 product/inventory 库直查依赖（无 JDBC/MyBatis/JPA/Flyway/驱动）")
    void pomHasNoPersistenceDependencies() throws IOException {
        Path moduleRoot = locateModuleRoot();
        String pom = Files.readString(moduleRoot.resolve("pom.xml"), StandardCharsets.UTF_8);
        assertThat(FORBIDDEN_POM_TOKENS).allSatisfy(token ->
                assertThat(pom).as("pom.xml 不应包含持久化直查依赖: %s", token).doesNotContain(token));
    }

    @Test
    @DisplayName("AC-021 主干代码无 JDBC/MyBatis/JPA/Spring JDBC import（跨服务仅 RestClient 契约）")
    void mainSourcesHaveNoDirectDataAccess() throws IOException {
        Path moduleRoot = locateModuleRoot();
        Path mainJava = moduleRoot.resolve("src/main/java");
        List<String> violations = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(mainJava)) {
            walk.filter(p -> p.toString().endsWith(".java")).forEach(javaFile -> {
                String content;
                try {
                    content = Files.readString(javaFile, StandardCharsets.UTF_8);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                for (String forbidden : FORBIDDEN_IMPORTS) {
                    if (content.contains(forbidden)) {
                        violations.add(mainJava.relativize(javaFile) + " -> " + forbidden);
                    }
                }
            });
        }
        assertThat(violations).as("发现库表直查入口 import").isEmpty();
    }

    /** surefire 工作目录即模块根；兼容在仓库根执行的场景向上查找含 mall-cart pom 的目录。 */
    private static Path locateModuleRoot() {
        Path current = Path.of("").toAbsolutePath();
        Path probe = current;
        for (int i = 0; i < 5 && probe != null; i++) {
            if (Files.exists(probe.resolve("src/main/java/com/ai/mall/cart"))
                    && Files.exists(probe.resolve("pom.xml"))) {
                return probe;
            }
            probe = probe.getParent();
        }
        // 兜底：当前目录（surefire 默认 user.dir=模块根）
        return current;
    }
}

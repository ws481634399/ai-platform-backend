package com.ai.mall.identity.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class LayerDependencyTest {
    private static final Path ROOT = Path.of("src/main/java/com/ai/mall/identity");

    @Test void domainIsFrameworkFreeAndDependsOnNoOuterLayer() throws IOException {
        assertSources("domain", List.of("org.springframework", "org.apache.ibatis", ".application.", ".infrastructure.", ".interfaces."));
    }

    @Test void applicationDoesNotDependOnInfrastructureOrInterfaces() throws IOException {
        assertSources("application", List.of("org.apache.ibatis", "org.springframework.http", ".infrastructure.", ".interfaces.", "RbacCommandMapper", "AdminUserMapper"));
    }

    @Test void interfacesDoNotBypassApplicationToInfrastructure() throws IOException {
        assertSources("interfaces", List.of(".infrastructure.", "org.apache.ibatis"));
    }

    @Test void canonicalFourLayerStructureIsPresent() {
        assertThat(ROOT.resolve("interfaces")).isDirectory();
        assertThat(ROOT.resolve("application")).isDirectory();
        assertThat(ROOT.resolve("domain")).isDirectory();
        assertThat(ROOT.resolve("infrastructure")).isDirectory();
    }

    private static void assertSources(String layer, List<String> forbidden) throws IOException {
        try (var files = Files.walk(ROOT.resolve(layer))) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                for (String dependency : forbidden) {
                    assertThat(source).as("%s must not contain %s", file, dependency).doesNotContain(dependency);
                }
            }
        }
    }
}

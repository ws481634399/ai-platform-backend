package com.ai.mall.identity.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ai.mall.identity.domain.model.rbac.Menu;
import com.ai.mall.identity.domain.repository.MenuRepository;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * CHG-0008 补充证据：BE-204 基于 H2 真实 SQL 的菜单树集成覆盖
 * （稳定排序、自环拒绝）。仓库 add() 不回填自增 id，
 * 依赖按唯一 path 回查得到持久化后的真实 id。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("M1 菜单树集成测试")
class M1MenuTreeIntegrationTest {

    @Autowired MenuRepository menus;
    @Autowired JdbcTemplate jdbc;

    @Test
    @DisplayName("BE-204/TC-001 菜单树按 sort_order,id 稳定排序持久化")
    void menuTreePersistsWithStableSortOrder() {
        Menu ops = addReload("运维", "DIRECTORY", "/ops", "ops", null, 40, null);
        Menu sec = addReload("安全中心", "DIRECTORY", "/security", "security", null, 50, null);
        Menu secUsers = addReload("用户管理", "PAGE", "/security/users", "security-users", "security:users:list", 10, sec.id());
        Menu secRoles = addReload("角色管理", "PAGE", "/security/roles", "security-roles", "security:roles:list", 5, sec.id());
        Menu coms = addReload("数据中心", "DIRECTORY", "/coms", "coms", null, 10, null);

        List<Menu> rows = menus.findAll();

        assertThat(rows).extracting(Menu::id).containsSubsequence(coms.id(), ops.id(), sec.id());
        int comsIdx = indexOf(rows, coms.id());
        int opsIdx = indexOf(rows, ops.id());
        int secIdx = indexOf(rows, sec.id());
        int usersIdx = indexOf(rows, secUsers.id());
        int rolesIdx = indexOf(rows, secRoles.id());
        assertThat(comsIdx).isLessThan(opsIdx);
        assertThat(opsIdx).isLessThan(secIdx);
        assertThat(usersIdx).isLessThan(comsIdx);
        assertThat(rolesIdx).isLessThan(usersIdx);

        Menu reloadedUsers = menus.findById(secUsers.id()).orElseThrow();
        assertThat(reloadedUsers.parentId()).isEqualTo(sec.id());
        assertThat(reloadedUsers.path()).isEqualTo("/security/users");
        assertThat(reloadedUsers.componentKey()).isEqualTo("security-users");
        assertThat(reloadedUsers.permissionCode()).isEqualTo("security:users:list");
        assertThat(reloadedUsers.sortOrder()).isEqualTo(10);
        assertThat(reloadedUsers.visible()).isTrue();

        List<Menu> again = menus.findAll();
        assertThat(again).extracting(Menu::id)
                .containsExactlyElementsOf(rows.stream().map(Menu::id).toList());
    }

    @Test
    @DisplayName("BE-204/TC-002 自环在领域层被拒绝，持久化不变量保持无环")
    void selfParentAndDescendantCycleRejected() {
        Menu root = addReload("根", "DIRECTORY", "/root", "root", null, 1, null);
        Menu child = addReload("子", "DIRECTORY", "/child", "child", null, 1, root.id());
        Menu grand = addReload("孙", "PAGE", "/grand", "grand", "grand:view", 1, child.id());

        Menu reloadedGrand = menus.findById(grand.id()).orElseThrow();
        assertThatThrownBy(() -> reloadedGrand.revise(grand.id(),
                "孙", "PAGE", "/grand", "grand", "grand:view", 1, true, "ENABLED"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("own parent");

        long selfParents = jdbc.queryForObject(
                "SELECT COUNT(*) FROM auth_menu WHERE id = parent_id", Long.class);
        assertThat(selfParents).isZero();

        Menu reloadedRoot = menus.findById(root.id()).orElseThrow();
        Menu reloadedChild = menus.findById(child.id()).orElseThrow();
        assertThat(reloadedRoot.parentId()).isNull();
        assertThat(reloadedChild.parentId()).isEqualTo(root.id());
        assertThat(reloadedGrand.parentId()).isEqualTo(child.id());
    }

    private Menu addReload(String name, String type, String path, String componentKey,
                           String permissionCode, int sortOrder, Long parentId) {
        menus.add(Menu.create(parentId, name, type, path, componentKey, permissionCode, sortOrder, true));
        return menus.findAll().stream()
                .filter(m -> Objects.equals(m.path(), path) && Objects.equals(m.name(), name))
                .findFirst().orElseThrow();
    }

    private static int indexOf(List<Menu> rows, long id) {
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).id() == id) return i;
        }
        return -1;
    }
}
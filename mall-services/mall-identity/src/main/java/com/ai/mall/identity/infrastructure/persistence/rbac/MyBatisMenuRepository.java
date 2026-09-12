package com.ai.mall.identity.infrastructure.persistence.rbac;
import com.ai.mall.identity.domain.model.rbac.Menu;import com.ai.mall.identity.domain.repository.MenuRepository;import java.util.*;import org.springframework.stereotype.Repository;
@Repository public class MyBatisMenuRepository implements MenuRepository {
 private final RbacCommandMapper mapper; public MyBatisMenuRepository(RbacCommandMapper mapper){this.mapper=mapper;}
 public Menu add(Menu m){mapper.createMenu(m.parentId(),m.name(),m.type(),m.path(),m.componentKey(),m.permissionCode(),m.sortOrder(),m.visible());return m;}
 public Optional<Menu> findById(long id){return Optional.ofNullable(mapper.findMenu(id)).map(this::toDomain);} public List<Menu> findAll(){return mapper.listMenus().stream().map(this::toDomain).toList();}
 public void save(Menu m){if(mapper.updateMenu(new RbacCommandMapper.MenuRow(m.id(),m.parentId(),m.name(),m.type(),m.path(),m.componentKey(),m.permissionCode(),m.sortOrder(),m.visible(),m.status()))!=1)throw new IllegalStateException("menu update rejected");}
 public boolean delete(Menu m){return mapper.deleteMenu(m.id())==1;} public boolean allEnabled(Collection<Long> ids){return ids.isEmpty()||mapper.countEnabledMenus(ids)==ids.size();} public boolean isDescendant(long id,long descendant){return mapper.isDescendant(id,descendant)>0;} public void bumpMembersVersion(long id){mapper.bumpMenuMembersPermissionVersion(id);}
 private Menu toDomain(RbacCommandMapper.MenuRow m){return Menu.reconstitute(m.id(),m.parentId(),m.name(),m.type(),m.path(),m.componentKey(),m.permissionCode(),m.sortOrder(),m.visible(),m.status());}
}

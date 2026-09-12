package com.ai.mall.identity.infrastructure.persistence.rbac;
import com.ai.mall.identity.domain.model.rbac.Role;import com.ai.mall.identity.domain.repository.RoleRepository;import java.util.*;import org.springframework.stereotype.Repository;
@Repository public class MyBatisRoleRepository implements RoleRepository {
 private final RbacCommandMapper mapper; public MyBatisRoleRepository(RbacCommandMapper mapper){this.mapper=mapper;}
 public Role add(Role r){try{mapper.createRole(r.code(),r.name(),r.description());return r;}catch(org.springframework.dao.DuplicateKeyException ex){throw new com.ai.mall.identity.domain.exception.DuplicateResourceException("role code already exists",ex);}}
 public Optional<Role> findById(long id){return Optional.ofNullable(mapper.findRole(id)).map(this::toDomain);}
 public List<Role> findAll(){return mapper.listRoles().stream().map(this::toDomain).toList();}
 public void save(Role r){if(mapper.updateRole(r.id(),r.name(),r.description(),r.status())!=1)throw new IllegalStateException("role update rejected");}
 public void saveAuthorizations(Role r){mapper.clearRolePermissions(r.id());mapper.clearRoleMenus(r.id());if(!r.permissionIds().isEmpty())mapper.addRolePermissions(r.id(),r.permissionIds());if(!r.menuIds().isEmpty())mapper.addRoleMenus(r.id(),r.menuIds());}
 public boolean delete(Role r){return mapper.deleteRole(r.id())==1;} public boolean allEnabled(Collection<Long> ids){return ids.isEmpty()||mapper.countEnabledRoles(ids)==ids.size();} public void bumpMembersVersion(long id){mapper.bumpRoleMembersPermissionVersion(id);}
 private Role toDomain(RbacCommandMapper.RoleRow r){return Role.reconstitute(r.id(),r.code(),r.name(),r.description(),r.status(),r.builtIn(),mapper.findRolePermissionIds(r.id()),mapper.findRoleMenuIds(r.id()));}
}

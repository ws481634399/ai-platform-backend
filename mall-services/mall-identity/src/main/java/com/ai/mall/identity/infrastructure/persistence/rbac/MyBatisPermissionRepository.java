package com.ai.mall.identity.infrastructure.persistence.rbac;
import com.ai.mall.identity.domain.model.rbac.Permission;import com.ai.mall.identity.domain.repository.PermissionRepository;import java.util.*;import org.springframework.stereotype.Repository;
@Repository public class MyBatisPermissionRepository implements PermissionRepository {
 private final RbacCommandMapper mapper; public MyBatisPermissionRepository(RbacCommandMapper mapper){this.mapper=mapper;}
 public Permission add(Permission p){try{mapper.createPermission(p.code(),p.name(),p.description(),p.type(),p.apiPattern(),p.httpMethod());return p;}catch(org.springframework.dao.DuplicateKeyException ex){throw new com.ai.mall.identity.domain.exception.DuplicateResourceException("permission code already exists",ex);}}
 public Optional<Permission> findById(long id){return Optional.ofNullable(mapper.findPermission(id)).map(this::toDomain);} public List<Permission> findAll(){return mapper.listPermissions().stream().map(this::toDomain).toList();}
 public void save(Permission p){if(mapper.updatePermission(p.id(),p.name(),p.description(),p.status(),p.apiPattern(),p.httpMethod())!=1)throw new IllegalStateException("permission update rejected");}
 public boolean delete(Permission p){return mapper.deletePermission(p.id())==1;} public boolean allEnabled(Collection<Long> ids){return ids.isEmpty()||mapper.countEnabledPermissions(ids)==ids.size();} public void bumpMembersVersion(long id){mapper.bumpPermissionMembersPermissionVersion(id);}
 private Permission toDomain(RbacCommandMapper.PermissionRow p){return Permission.reconstitute(p.id(),p.code(),p.name(),p.description(),p.type(),p.status(),p.apiPattern(),p.httpMethod());}
}

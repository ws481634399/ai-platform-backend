package com.ai.mall.identity.domain.repository;
import com.ai.mall.identity.domain.model.rbac.Permission;import java.util.Collection;import java.util.List;import java.util.Optional;
public interface PermissionRepository { Permission add(Permission permission); Optional<Permission> findById(long id); List<Permission> findAll(); void save(Permission permission); boolean delete(Permission permission); boolean allEnabled(Collection<Long> ids); void bumpMembersVersion(long permissionId); }

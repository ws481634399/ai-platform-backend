package com.ai.mall.identity.domain.repository;
import com.ai.mall.identity.domain.model.rbac.Role;import java.util.Collection;import java.util.List;import java.util.Optional;
public interface RoleRepository { Role add(Role role); Optional<Role> findById(long id); List<Role> findAll(); void save(Role role); void saveAuthorizations(Role role); boolean delete(Role role); boolean allEnabled(Collection<Long> ids); void bumpMembersVersion(long roleId); }

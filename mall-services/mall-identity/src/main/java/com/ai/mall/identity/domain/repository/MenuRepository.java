package com.ai.mall.identity.domain.repository;
import com.ai.mall.identity.domain.model.rbac.Menu;import java.util.Collection;import java.util.List;import java.util.Optional;
public interface MenuRepository { Menu add(Menu menu); Optional<Menu> findById(long id); List<Menu> findAll(); void save(Menu menu); boolean delete(Menu menu); boolean allEnabled(Collection<Long> ids); boolean isDescendant(long id,long possibleDescendant); void bumpMembersVersion(long menuId); }

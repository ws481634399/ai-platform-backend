package com.ai.mall.identity.domain.model.rbac;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import com.ai.mall.identity.domain.exception.DomainRuleViolation;

public final class Role {
    private final long id;
    private final RoleCode code;
    private String name;
    private String description;
    private RbacStatus status;
    private final boolean builtIn;
    private Set<Long> permissionIds;
    private Set<Long> menuIds;
    private Role(long id, String code, String name, String description, String status, boolean builtIn,
                 Collection<Long> permissionIds, Collection<Long> menuIds) {
        this.id=id; this.code=new RoleCode(code); this.name=required(name); this.description=trim(description);
        this.status=RbacStatus.valueOf(status); this.builtIn=builtIn;
        this.permissionIds=ids(permissionIds); this.menuIds=ids(menuIds);
    }
    public static Role create(String code, String name, String description) {
        return new Role(0, code, name, description, "ENABLED", false, Set.of(), Set.of());
    }
    public static Role reconstitute(long id, String code, String name, String description, String status, boolean builtIn,
                                    Collection<Long> permissions, Collection<Long> menus) {
        if(id<=0) throw new IllegalArgumentException("role id must be positive");
        return new Role(id,code,name,description,status,builtIn,permissions,menus);
    }
    public void revise(String name, String description, String status) {
        ensureMutable(); this.name=required(name); this.description=trim(description); this.status=RbacStatus.valueOf(status);
    }
    public void replaceAuthorizations(Collection<Long> permissions, Collection<Long> menus) {
        if (status != RbacStatus.ENABLED) throw new DomainRuleViolation("disabled role cannot receive authorizations");
        this.permissionIds=ids(permissions); this.menuIds=ids(menus);
        if ("SUPER_ADMIN".equals(code.value()) && permissionIds.isEmpty() && menuIds.isEmpty())
            throw new DomainRuleViolation("super administrator authorizations cannot be empty");
    }
    public void ensureDeletable() { if(builtIn) throw new DomainRuleViolation("built-in role is immutable"); }
    private void ensureMutable(){ ensureDeletable(); }
    private static Set<Long> ids(Collection<Long> values){var out=new LinkedHashSet<Long>(); if(values==null)return out; for(Long v:values)if(v==null||v<=0||!out.add(v))throw new IllegalArgumentException("ids must be unique and positive"); return out;}
    private static String required(String v){if(v==null||v.isBlank())throw new IllegalArgumentException("name is required"); return v.trim();}
    private static String trim(String v){return v==null?null:v.trim();}
    public long id(){return id;} public String code(){return code.value();} public String name(){return name;} public String description(){return description;}
    public String status(){return status.name();} public boolean builtIn(){return builtIn;} public Set<Long> permissionIds(){return Set.copyOf(permissionIds);} public Set<Long> menuIds(){return Set.copyOf(menuIds);}
}

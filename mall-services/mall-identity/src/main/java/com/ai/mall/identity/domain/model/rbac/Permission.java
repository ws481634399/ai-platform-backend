package com.ai.mall.identity.domain.model.rbac;

public final class Permission {
    private final long id; private final PermissionCode code; private String name; private String description;
    private final PermissionType type; private RbacStatus status; private String apiPattern; private String httpMethod;
    private Permission(long id,String code,String name,String description,String type,String status,String pattern,String method){this.id=id;this.code=new PermissionCode(code);this.type=PermissionType.valueOf(type); revise(name,description,status,pattern,method);}
    public static Permission create(String code,String name,String description,String type,String pattern,String method){return new Permission(0,code,name,description,type,"ENABLED",pattern,method);}
    public static Permission reconstitute(long id,String code,String name,String description,String type,String status,String pattern,String method){if(id<=0)throw new IllegalArgumentException("permission id must be positive");return new Permission(id,code,name,description,type,status,pattern,method);}
    public void revise(String name,String description,String status,String pattern,String method){if(name==null||name.isBlank())throw new IllegalArgumentException("name is required");this.name=name.trim();this.description=description==null?null:description.trim();this.status=RbacStatus.valueOf(status);this.apiPattern=pattern;this.httpMethod=method==null?null:method.toUpperCase();if(type==PermissionType.API&&(apiPattern==null||apiPattern.isBlank()||httpMethod==null||httpMethod.isBlank()))throw new IllegalArgumentException("API pattern and method are required");}
    public long id(){return id;}public String code(){return code.value();}public String name(){return name;}public String description(){return description;}public String type(){return type.name();}public String status(){return status.name();}public String apiPattern(){return apiPattern;}public String httpMethod(){return httpMethod;}
    public enum PermissionType { BUTTON, API }
}

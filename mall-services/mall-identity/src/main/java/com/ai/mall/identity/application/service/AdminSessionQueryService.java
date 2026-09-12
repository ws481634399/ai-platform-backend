package com.ai.mall.identity.application.service;
import com.ai.mall.identity.application.dto.MenuNode;import com.ai.mall.identity.application.exception.UseCaseException;import com.ai.mall.identity.domain.exception.DomainRuleViolation;import com.ai.mall.identity.domain.repository.AdminUserRepository;import java.util.*;import org.springframework.stereotype.Service;
@Service public class AdminSessionQueryService {
 private final AdminUserRepository admins;private final AuthorizationQueryService authorization;
 public AdminSessionQueryService(AdminUserRepository admins,AuthorizationQueryService authorization){this.admins=admins;this.authorization=authorization;}
 public Bootstrap bootstrap(long id){var admin=admins.findById(id).orElseThrow(()->new UseCaseException(UseCaseException.Kind.UNAUTHORIZED,"administrator unavailable"));try{admin.ensureCanSignIn();}catch(DomainRuleViolation ex){throw new UseCaseException(UseCaseException.Kind.UNAUTHORIZED,"administrator unavailable");}var snapshot=authorization.get(id,admin.permissionVersion());return new Bootstrap(new User(Long.toString(id),admin.account(),admin.account()),snapshot.menus(),snapshot.permissions(),snapshot.permissionVersion());}
 public record User(String id,String username,String displayName){} public record Bootstrap(User user,List<MenuNode> menus,Set<String> permissions,long permissionVersion){}
}

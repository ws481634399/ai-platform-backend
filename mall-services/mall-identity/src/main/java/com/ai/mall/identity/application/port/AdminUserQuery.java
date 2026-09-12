package com.ai.mall.identity.application.port;
import java.util.List;
public interface AdminUserQuery { long count(); List<Summary> page(long offset,int size); record Summary(long id,String username,String status,long authVersion,long permissionVersion){} }

package com.ai.mall.identity.infrastructure.persistence.admin;
import com.ai.mall.identity.application.port.AdminUserQuery;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisAdminUserQuery implements AdminUserQuery {
    private final AdminUserMapper mapper;
    public MyBatisAdminUserQuery(AdminUserMapper mapper){this.mapper = mapper;}

    @Override
    public long count(){return mapper.count();}

    @Override
    public List<Summary> page(long offset,int size){
        List<AdminUserMapper.SummaryPo> rows = mapper.page(offset,size);
        // CHG-0023：页内批量取角色关联后按 adminId 分组，无角色管理员给空列表（不出 null）
        Map<Long,List<Long>> roleIdsByAdmin = new LinkedHashMap<>();
        for (AdminUserMapper.SummaryPo row : rows) {
            roleIdsByAdmin.put(row.id(), new ArrayList<>());
        }
        if (!rows.isEmpty()) {
            for (AdminUserMapper.AdminRoleRef ref : mapper.findRoleRefs(roleIdsByAdmin.keySet())) {
                roleIdsByAdmin.computeIfAbsent(ref.adminId(), k -> new ArrayList<>()).add(ref.roleId());
            }
        }
        return rows.stream()
                .map(p -> new Summary(p.id(),p.username(),p.status(),p.authVersion(),p.permissionVersion(),
                        roleIdsByAdmin.getOrDefault(p.id(), List.of())))
                .toList();
    }
}

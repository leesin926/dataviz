package com.dataviz.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dataviz.common.core.exception.BizException;
import com.dataviz.user.dto.DeptCreateDTO;
import com.dataviz.user.entity.SysDept;
import com.dataviz.user.entity.SysUser;
import com.dataviz.user.mapper.DeptMapper;
import com.dataviz.user.mapper.UserMapper;
import com.dataviz.user.service.DeptService;
import com.dataviz.user.vo.DeptTreeVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 部门的读树与增删改。
 * <p>
 * <strong>这里的每一条写操作都以 {@code tenantId} 为准，而不是以路径上的 {@code id} 为准。</strong>
 * 之前 {@code update}/{@code delete}/{@code getById} 只按 id 取记录，任何拿到别人部门 id 的请求
 * 都能改、能删、能读（跨租户 IDOR）；现在三个入口统一过 {@link #requireDeptInTenant}。
 * {@code parentId} 同样要过归属校验——否则"把部门挂到别人的树上"本身就是一条跨租户的数据出口。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeptServiceImpl implements DeptService {

    private static final long ROOT_PARENT_ID = 0L;

    private final DeptMapper deptMapper;
    private final UserMapper userMapper;

    @Override
    public List<DeptTreeVO> getDeptTree(Long tenantId) {
        LambdaQueryWrapper<SysDept> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysDept::getTenantId, tenantId).orderByAsc(SysDept::getSortOrder);
        List<SysDept> allDepts = deptMapper.selectList(wrapper);

        List<DeptTreeVO> voList = allDepts.stream().map(d -> {
            DeptTreeVO vo = new DeptTreeVO();
            BeanUtils.copyProperties(d, vo);
            return vo;
        }).collect(Collectors.toList());

        // Build tree structure
        Map<Long, List<DeptTreeVO>> childrenMap = voList.stream()
                .filter(v -> v.getParentId() != null && v.getParentId() != 0)
                .collect(Collectors.groupingBy(DeptTreeVO::getParentId));

        voList.forEach(v -> v.setChildren(childrenMap.getOrDefault(v.getId(), new ArrayList<>())));

        return voList.stream().filter(v -> v.getParentId() == null || v.getParentId() == 0).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createDept(DeptCreateDTO dto, Long tenantId) {
        Long parentId = normalizeParentId(dto.getParentId());
        requireParentUsable(parentId, tenantId, null);

        SysDept dept = new SysDept();
        dept.setTenantId(tenantId);
        dept.setParentId(parentId);
        dept.setDeptName(dto.getDeptName().trim());
        dept.setSortOrder(dto.getSortOrder() == null ? 0 : dto.getSortOrder());
        dept.setLeader(dto.getLeader());
        dept.setPhone(dto.getPhone());
        dept.setStatus(1);
        deptMapper.insert(dept);
        return dept.getId();
    }

    /**
     * 逐字段赋值而不是 {@code BeanUtils.copyProperties}：DTO 里没有的列（tenantId/status）会被
     * copyProperties 保留，但 DTO 里有的列<strong>传 null 就会把原值抹掉</strong>——
     * 其中 {@code parentId=null} 最致命：这个节点既不再是根（根判据是 0）也挂不到任何父节点下，
     * 于是从树里消失，数据还在库里但界面上再也点不到。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateDept(Long id, DeptCreateDTO dto, Long tenantId) {
        SysDept dept = requireDeptInTenant(id, tenantId);
        Long parentId = normalizeParentId(dto.getParentId());
        requireParentUsable(parentId, tenantId, id);

        dept.setParentId(parentId);
        dept.setDeptName(dto.getDeptName().trim());
        if (dto.getSortOrder() != null) {
            dept.setSortOrder(dto.getSortOrder());
        }
        dept.setLeader(dto.getLeader());
        dept.setPhone(dto.getPhone());
        deptMapper.updateById(dept);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDept(Long id, Long tenantId) {
        requireDeptInTenant(id, tenantId);

        // 子部门判据不带租户：跨租户的孤儿子节点同样会跟着一起变成点不到的数据，拦下来比放行安全
        LambdaQueryWrapper<SysDept> childWrapper = new LambdaQueryWrapper<>();
        childWrapper.eq(SysDept::getParentId, id);
        if (deptMapper.selectCount(childWrapper) > 0) {
            throw new BizException("Cannot delete department with children");
        }
        // 部门下还有人就直接删：删完之后那些用户的 deptId 指向一条已不存在的记录，
        // 管理端用户页的部门选择器会显示成空白，而这是只有出事当天才会被发现的那种坏数据
        LambdaQueryWrapper<SysUser> userWrapper = new LambdaQueryWrapper<>();
        userWrapper.eq(SysUser::getDeptId, id);
        if (userMapper.selectCount(userWrapper) > 0) {
            throw new BizException("Cannot delete department with users");
        }
        deptMapper.deleteById(id);
    }

    @Override
    public DeptTreeVO getDeptById(Long id, Long tenantId) {
        SysDept dept = requireDeptInTenant(id, tenantId);
        DeptTreeVO vo = new DeptTreeVO();
        BeanUtils.copyProperties(dept, vo);
        return vo;
    }

    /** 顶级统一落成 0，不再让 null 和 0 两种写法并存（读侧两种都认，写侧只出一种）。 */
    private static Long normalizeParentId(Long parentId) {
        return parentId == null ? ROOT_PARENT_ID : parentId;
    }

    /**
     * 不存在与"存在但不属于本租户"必须是同一句话 —— 分开报等于把"这个 id 在别的租户里存在"
     * 变成了一个可以试探的查询接口。
     */
    private SysDept requireDeptInTenant(Long id, Long tenantId) {
        SysDept dept = deptMapper.selectById(id);
        if (dept == null || !tenantId.equals(dept.getTenantId())) {
            throw new BizException("Department not found");
        }
        return dept;
    }

    /**
     * 校验新父级可用：同租户、不是自己、且不在自己的子树里。
     * <p>
     * 成环不是理论问题：环上的节点既不是根（parentId≠0）也不会被任何可见父节点收进 children，
     * {@code getDeptTree} 就把整条环<strong>静默丢掉</strong>——数据在库里，界面上凭空消失，
     * 而且因为看不见也就删不掉。这里顺着父级往上走一圈，走到自己即成环；
     * 顺带走过的每一层都做租户归属校验（跨租户的链条同样不许挂）。
     * </p>
     *
     * @param selfId 被改动的部门 id；新建时传 null
     */
    private void requireParentUsable(Long parentId, Long tenantId, Long selfId) {
        if (parentId == null || parentId == ROOT_PARENT_ID) {
            return;
        }
        Set<Long> walked = new HashSet<Long>();
        Long cursor = parentId;
        while (cursor != null && cursor != ROOT_PARENT_ID) {
            if (cursor.equals(selfId) || !walked.add(cursor)) {
                throw new BizException("Cannot move a department under itself or its sub-departments");
            }
            SysDept ancestor = deptMapper.selectById(cursor);
            if (ancestor == null || !tenantId.equals(ancestor.getTenantId())) {
                throw new BizException("Parent department not found");
            }
            cursor = ancestor.getParentId();
        }
    }
}

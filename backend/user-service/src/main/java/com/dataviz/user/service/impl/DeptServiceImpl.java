package com.dataviz.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dataviz.common.core.exception.BizException;
import com.dataviz.user.dto.DeptCreateDTO;
import com.dataviz.user.entity.SysDept;
import com.dataviz.user.mapper.DeptMapper;
import com.dataviz.user.service.DeptService;
import com.dataviz.user.vo.DeptTreeVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeptServiceImpl implements DeptService {

    private final DeptMapper deptMapper;

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
        SysDept dept = new SysDept();
        BeanUtils.copyProperties(dto, dept);
        dept.setTenantId(tenantId);
        dept.setStatus(1);
        deptMapper.insert(dept);
        return dept.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateDept(Long id, DeptCreateDTO dto) {
        SysDept dept = deptMapper.selectById(id);
        if (dept == null) {
            throw new BizException("Department not found");
        }
        BeanUtils.copyProperties(dto, dept);
        deptMapper.updateById(dept);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDept(Long id) {
        // Check for child departments
        LambdaQueryWrapper<SysDept> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysDept::getParentId, id);
        if (deptMapper.selectCount(wrapper) > 0) {
            throw new BizException("Cannot delete department with children");
        }
        deptMapper.deleteById(id);
    }

    @Override
    public DeptTreeVO getDeptById(Long id) {
        SysDept dept = deptMapper.selectById(id);
        if (dept == null) {
            throw new BizException("Department not found");
        }
        DeptTreeVO vo = new DeptTreeVO();
        BeanUtils.copyProperties(dept, vo);
        return vo;
    }
}

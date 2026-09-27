package com.dataviz.user.service;

import com.dataviz.user.dto.DeptCreateDTO;
import com.dataviz.user.vo.DeptTreeVO;
import java.util.List;

public interface DeptService {
    List<DeptTreeVO> getDeptTree(Long tenantId);
    Long createDept(DeptCreateDTO dto, Long tenantId);
    void updateDept(Long id, DeptCreateDTO dto, Long tenantId);
    void deleteDept(Long id, Long tenantId);
    DeptTreeVO getDeptById(Long id, Long tenantId);
}

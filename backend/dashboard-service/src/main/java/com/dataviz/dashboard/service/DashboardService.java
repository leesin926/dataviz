package com.dataviz.dashboard.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dataviz.dashboard.dto.DashboardCreateDTO;
import com.dataviz.dashboard.dto.DashboardUpdateDTO;
import com.dataviz.dashboard.vo.DashboardListVO;
import com.dataviz.dashboard.vo.DashboardVO;

import java.util.List;

public interface DashboardService {

    Long create(DashboardCreateDTO dto);

    void update(DashboardUpdateDTO dto);

    DashboardVO getById(Long id);

    void delete(Long id);

    Page<DashboardListVO> page(Integer pageNum, Integer pageSize, String keyword, Integer status);

    void publish(Long id);

    void unpublish(Long id);

    Long copy(Long id);

    List<DashboardListVO> listTemplates();
}

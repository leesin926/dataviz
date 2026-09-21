package com.dataviz.screen.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dataviz.common.core.exception.BizException;
import com.dataviz.common.core.result.ErrorCode;
import com.dataviz.screen.entity.ScreenComponent;
import com.dataviz.screen.mapper.ScreenComponentMapper;
import com.dataviz.screen.vo.ScreenComponentVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/screen/component")
@RequiredArgsConstructor
public class ScreenComponentController {

    private final ScreenComponentMapper screenComponentMapper;

    @PostMapping
    public Long create(@RequestBody ScreenComponent component) {
        screenComponentMapper.insert(component);
        log.info("Created screen component: id={}", component.getId());
        return component.getId();
    }

    @PutMapping
    public void update(@RequestBody ScreenComponent component) {
        screenComponentMapper.updateById(component);
        log.info("Updated screen component: id={}", component.getId());
    }

    @GetMapping("/{id}")
    public ScreenComponentVO getById(@PathVariable Long id) {
        ScreenComponent component = screenComponentMapper.selectById(id);
        if (component == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Component not found: " + id);
        }
        ScreenComponentVO vo = new ScreenComponentVO();
        BeanUtils.copyProperties(component, vo);
        return vo;
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        screenComponentMapper.deleteById(id);
        log.info("Deleted screen component: id={}", id);
    }

    @GetMapping("/list/{screenId}")
    public List<ScreenComponentVO> listByScreenId(@PathVariable Long screenId) {
        LambdaQueryWrapper<ScreenComponent> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ScreenComponent::getScreenId, screenId);
        List<ScreenComponent> list = screenComponentMapper.selectList(wrapper);
        return list.stream().map(c -> {
            ScreenComponentVO vo = new ScreenComponentVO();
            BeanUtils.copyProperties(c, vo);
            return vo;
        }).collect(Collectors.toList());
    }
}

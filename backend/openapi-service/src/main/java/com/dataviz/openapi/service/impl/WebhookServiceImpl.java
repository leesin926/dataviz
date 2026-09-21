package com.dataviz.openapi.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dataviz.openapi.dto.WebhookCreateDTO;
import com.dataviz.openapi.entity.OpenApiWebhook;
import com.dataviz.openapi.mapper.WebhookMapper;
import com.dataviz.openapi.service.WebhookService;
import com.dataviz.openapi.vo.WebhookVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookServiceImpl implements WebhookService {

    private final WebhookMapper webhookMapper;

    @Override
    @Transactional
    public Long create(WebhookCreateDTO dto) {
        OpenApiWebhook webhook = new OpenApiWebhook();
        BeanUtils.copyProperties(dto, webhook);
        webhook.setStatus(1);
        webhookMapper.insert(webhook);
        log.info("Created webhook: id={}, eventType={}", webhook.getId(), webhook.getEventType());
        return webhook.getId();
    }

    @Override
    @Transactional
    public void update(WebhookCreateDTO dto) {
        OpenApiWebhook webhook = webhookMapper.selectById(dto.getAppId());
        if (webhook == null) {
            throw new RuntimeException("Webhook not found");
        }
        BeanUtils.copyProperties(dto, webhook);
        webhookMapper.updateById(webhook);
        log.info("Updated webhook: id={}", webhook.getId());
    }

    @Override
    @Transactional
    public void delete(Long id) {
        webhookMapper.deleteById(id);
        log.info("Deleted webhook: id={}", id);
    }

    @Override
    public WebhookVO getById(Long id) {
        OpenApiWebhook webhook = webhookMapper.selectById(id);
        if (webhook == null) {
            throw new RuntimeException("Webhook not found: " + id);
        }
        WebhookVO vo = new WebhookVO();
        BeanUtils.copyProperties(webhook, vo);
        return vo;
    }

    @Override
    public List<WebhookVO> listByApp(Long appId) {
        LambdaQueryWrapper<OpenApiWebhook> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OpenApiWebhook::getAppId, appId);
        List<OpenApiWebhook> list = webhookMapper.selectList(wrapper);
        return list.stream().map(w -> {
            WebhookVO vo = new WebhookVO();
            BeanUtils.copyProperties(w, vo);
            return vo;
        }).collect(Collectors.toList());
    }
}

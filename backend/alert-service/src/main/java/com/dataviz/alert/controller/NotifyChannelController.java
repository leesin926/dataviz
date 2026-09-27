package com.dataviz.alert.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dataviz.alert.entity.NotifyChannel;
import com.dataviz.alert.mapper.NotifyChannelMapper;
import com.dataviz.common.security.annotation.RequiresPermission;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/alert/channel")
@RequiredArgsConstructor
// 类级 = 本控制器全部端点至少要 alert:read（PermissionInterceptor 会回落到类上的注解）；渠道会存下服务端将来 POST 的 URL，写配置属 SSRF 相关面，抬到 alert:write。
@RequiresPermission("alert:read")
public class NotifyChannelController {

    private final NotifyChannelMapper notifyChannelMapper;

    @PostMapping
    @RequiresPermission("alert:write")
    public Long create(@RequestBody NotifyChannel channel) {
        notifyChannelMapper.insert(channel);
        log.info("Created notify channel: id={}, type={}", channel.getId(), channel.getType());
        return channel.getId();
    }

    @PutMapping
    @RequiresPermission("alert:write")
    public void update(@RequestBody NotifyChannel channel) {
        notifyChannelMapper.updateById(channel);
        log.info("Updated notify channel: id={}", channel.getId());
    }

    @GetMapping("/{id}")
    public NotifyChannel getById(@PathVariable Long id) {
        NotifyChannel channel = notifyChannelMapper.selectById(id);
        if (channel == null) {
            throw new RuntimeException("Notify channel not found: " + id);
        }
        return channel;
    }

    @DeleteMapping("/{id}")
    @RequiresPermission("alert:write")
    public void delete(@PathVariable Long id) {
        notifyChannelMapper.deleteById(id);
        log.info("Deleted notify channel: id={}", id);
    }

    @GetMapping("/list")
    public List<NotifyChannel> list(@RequestParam(required = false) String type) {
        LambdaQueryWrapper<NotifyChannel> wrapper = new LambdaQueryWrapper<>();
        if (type != null) {
            wrapper.eq(NotifyChannel::getType, type);
        }
        return notifyChannelMapper.selectList(wrapper);
    }
}

package com.dataviz.alert.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dataviz.alert.entity.NotifyChannel;
import com.dataviz.alert.mapper.NotifyChannelMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/alert/channel")
@RequiredArgsConstructor
public class NotifyChannelController {

    private final NotifyChannelMapper notifyChannelMapper;

    @PostMapping
    public Long create(@RequestBody NotifyChannel channel) {
        notifyChannelMapper.insert(channel);
        log.info("Created notify channel: id={}, type={}", channel.getId(), channel.getType());
        return channel.getId();
    }

    @PutMapping
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

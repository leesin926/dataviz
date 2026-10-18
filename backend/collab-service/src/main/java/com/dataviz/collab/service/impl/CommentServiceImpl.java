package com.dataviz.collab.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dataviz.collab.dto.CommentCreateDTO;
import com.dataviz.collab.entity.CollabComment;
import com.dataviz.collab.mapper.CommentMapper;
import com.dataviz.collab.service.CommentService;
import com.dataviz.collab.vo.CommentVO;
import com.dataviz.common.security.context.SecurityContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;
import com.dataviz.common.core.exception.BizException;
import com.dataviz.common.core.result.ErrorCode;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommentServiceImpl implements CommentService {

    private final CommentMapper commentMapper;

    @Override
    @Transactional
    public Long create(CommentCreateDTO dto) {
        CollabComment comment = new CollabComment();
        BeanUtils.copyProperties(dto, comment);
        // 作者从会话取，不接受入参：DTO 里没有作者字段，而公共填充器是按 String 填 createBy 的
        // （见 MybatisPlusConfig.insertFill），本表 create_by 是 BIGINT ⇒ 类型对不上、填充被跳过 ⇒
        // 不写这一句的话每条新评论的作者都是 NULL，"编辑/删除他人评论"这种校验根本无从加起。
        comment.setCreateBy(SecurityContextHolder.getUserId());
        commentMapper.insert(comment);
        log.info("Created comment: id={}, targetType={}, targetId={}", 
                comment.getId(), dto.getTargetType(), dto.getTargetId());
        return comment.getId();
    }

    @Override
    @Transactional
    public void update(Long id, String content) {
        CollabComment comment = commentMapper.selectById(id);
        if (comment == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Comment not found: " + id);
        }
        comment.setContent(content);
        commentMapper.updateById(comment);
        log.info("Updated comment: id={}", id);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        commentMapper.deleteById(id);
        log.info("Deleted comment: id={}", id);
    }

    @Override
    public CommentVO getById(Long id) {
        CollabComment comment = commentMapper.selectById(id);
        if (comment == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Comment not found: " + id);
        }
        CommentVO vo = new CommentVO();
        BeanUtils.copyProperties(comment, vo);
        return vo;
    }

    @Override
    public List<CommentVO> listByTarget(String targetType, Long targetId) {
        LambdaQueryWrapper<CollabComment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CollabComment::getTargetType, targetType)
               .eq(CollabComment::getTargetId, targetId)
               // 顶层判定必须同时认 NULL 和 0：演示数据写的是 NULL，而 DDL 的 parent_id 带
               // DEFAULT 0 —— 入参 parentId 为空时 MyBatis-Plus 默认按 NOT_NULL 策略把这一列整条
               // 跳过，落库拿到的是 0。只查 IS NULL 的后果是"演示评论看得见、自己发的顶层评论
               // 刷新后消失"。
               .and(w -> w.isNull(CollabComment::getParentId).or().eq(CollabComment::getParentId, 0L))
               .orderByDesc(CollabComment::getCreateTime);
        List<CollabComment> list = commentMapper.selectList(wrapper);
        return list.stream().map(c -> {
            CommentVO vo = new CommentVO();
            BeanUtils.copyProperties(c, vo);
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    public List<CommentVO> listReplies(Long parentId) {
        LambdaQueryWrapper<CollabComment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CollabComment::getParentId, parentId)
               .orderByAsc(CollabComment::getCreateTime);
        List<CollabComment> list = commentMapper.selectList(wrapper);
        return list.stream().map(c -> {
            CommentVO vo = new CommentVO();
            BeanUtils.copyProperties(c, vo);
            return vo;
        }).collect(Collectors.toList());
    }
}

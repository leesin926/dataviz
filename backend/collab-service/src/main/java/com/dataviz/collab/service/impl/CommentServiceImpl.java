package com.dataviz.collab.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dataviz.collab.dto.CommentCreateDTO;
import com.dataviz.collab.entity.CollabComment;
import com.dataviz.collab.mapper.CommentMapper;
import com.dataviz.collab.service.CommentService;
import com.dataviz.collab.vo.CommentVO;
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
public class CommentServiceImpl implements CommentService {

    private final CommentMapper commentMapper;

    @Override
    @Transactional
    public Long create(CommentCreateDTO dto) {
        CollabComment comment = new CollabComment();
        BeanUtils.copyProperties(dto, comment);
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
            throw new RuntimeException("Comment not found: " + id);
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
            throw new RuntimeException("Comment not found: " + id);
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
               .isNull(CollabComment::getParentId)
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

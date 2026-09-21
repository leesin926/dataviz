package com.dataviz.collab.service;

import com.dataviz.collab.dto.CommentCreateDTO;
import com.dataviz.collab.vo.CommentVO;

import java.util.List;

public interface CommentService {

    Long create(CommentCreateDTO dto);

    void update(Long id, String content);

    void delete(Long id);

    CommentVO getById(Long id);

    List<CommentVO> listByTarget(String targetType, Long targetId);

    List<CommentVO> listReplies(Long parentId);
}

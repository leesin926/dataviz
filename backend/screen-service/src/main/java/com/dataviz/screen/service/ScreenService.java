package com.dataviz.screen.service;

import com.dataviz.screen.dto.ScreenUpsertDTO;
import com.dataviz.screen.vo.ScreenListVO;
import com.dataviz.screen.vo.ScreenVO;

import java.util.Map;

public interface ScreenService {

    ScreenVO create(ScreenUpsertDTO dto);

    void update(Long id, ScreenUpsertDTO dto);

    /** platform 为 mobile/tablet 时返回该端变体展平后的单份配置（缺省回退 pc 顶层配置） */
    ScreenVO getById(Long id, String platform);

    void delete(Long id);

    ScreenListVO list(String keyword, String status, int pageNum, int pageSize);

    void publish(Long id);

    void unpublish(Long id);

    ScreenVO clone(Long id, String newName);

    String share(Long id);

    /** 分享链接读取：仅返回已发布大屏的配置，供免登录展示端使用 */
    ScreenVO getByShareToken(String shareToken, String platform);

    /** 按端保存变体，platform ∈ pc|mobile|tablet */
    void saveVariant(Long id, String platform, Map<String, Object> variant);
}

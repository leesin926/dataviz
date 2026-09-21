package com.dataviz.screen.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dataviz.common.core.exception.BizException;
import com.dataviz.common.core.result.ErrorCode;
import com.dataviz.screen.dto.ScreenUpsertDTO;
import com.dataviz.screen.entity.Screen;
import com.dataviz.screen.mapper.ScreenMapper;
import com.dataviz.screen.service.ScreenService;
import com.dataviz.screen.vo.ScreenListVO;
import com.dataviz.screen.vo.ScreenVO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScreenServiceImpl implements ScreenService {

    private static final int DEFAULT_WIDTH = 1920;
    private static final int DEFAULT_HEIGHT = 1080;
    private static final List<String> VALID_PLATFORMS = Arrays.asList("pc", "mobile", "tablet");

    private final ScreenMapper screenMapper;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public ScreenVO create(ScreenUpsertDTO dto) {
        if (!StringUtils.hasText(dto.getName())) {
            throw new BizException(ErrorCode.BAD_REQUEST, "大屏名称不能为空");
        }
        Screen screen = new Screen();
        screen.setName(dto.getName());
        screen.setDescription(dto.getDescription());
        screen.setCoverUrl(dto.getCover());
        screen.setConfigJson(writeJson(buildConfigJson(null, dto)));
        screen.setComponentsJson(writeJson(dto.getComponents() != null ? dto.getComponents() : new ArrayList<Object>()));
        screen.setVariantsJson(dto.getVariants() == null ? null : writeJson(dto.getVariants()));
        screen.setAdaptMode(normalizeAdaptMode(dto.getAdaptMode(), "scale"));
        screen.setStatus(0);
        screen.setViewCount(0L);
        screenMapper.insert(screen);
        log.info("Created screen: id={}, name={}", screen.getId(), screen.getName());
        return toVO(selectOrThrow(screen.getId()));
    }

    @Override
    @Transactional
    public void update(Long id, ScreenUpsertDTO dto) {
        Screen screen = selectOrThrow(id);
        if (StringUtils.hasText(dto.getName())) {
            screen.setName(dto.getName());
        }
        if (dto.getDescription() != null) {
            screen.setDescription(dto.getDescription());
        }
        if (dto.getCover() != null) {
            screen.setCoverUrl(dto.getCover());
        }
        if (dto.getAdaptMode() != null) {
            screen.setAdaptMode(normalizeAdaptMode(dto.getAdaptMode(), screen.getAdaptMode()));
        }
        if (dto.getConfig() != null || dto.getComponents() != null || dto.getLayers() != null
                || dto.getWidth() != null || dto.getHeight() != null) {
            screen.setConfigJson(writeJson(buildConfigJson(screen.getConfigJson(), dto)));
        }
        if (dto.getComponents() != null) {
            screen.setComponentsJson(writeJson(dto.getComponents()));
        }
        if (dto.getVariants() != null) {
            screen.setVariantsJson(writeJson(dto.getVariants()));
        }
        screenMapper.updateById(screen);
        log.info("Updated screen: id={}", id);
    }

    @Override
    public ScreenVO getById(Long id, String platform) {
        Screen screen = selectOrThrow(id);
        screenMapper.incrementViewCount(id);
        ScreenVO vo = toVO(screen);
        if (StringUtils.hasText(platform) && !"pc".equalsIgnoreCase(platform)) {
            applyVariant(vo, screen.getVariantsJson(), platform);
        }
        return vo;
    }

    @Override
    @Transactional
    public void delete(Long id) {
        screenMapper.deleteById(id);
        log.info("Deleted screen: id={}", id);
    }

    @Override
    public ScreenListVO list(String keyword, String status, int pageNum, int pageSize) {
        LambdaQueryWrapper<Screen> wrapper = new LambdaQueryWrapper<Screen>();
        if (StringUtils.hasText(keyword)) {
            wrapper.like(Screen::getName, keyword);
        }
        Integer statusInt = parseStatus(status);
        if (statusInt != null) {
            wrapper.eq(Screen::getStatus, statusInt);
        }
        wrapper.orderByDesc(Screen::getCreateTime);
        List<Screen> all = screenMapper.selectList(wrapper);
        int total = all.size();
        int safePageNum = Math.max(pageNum, 1);
        int safePageSize = pageSize <= 0 ? 20 : Math.min(pageSize, 100);
        int from = Math.min((safePageNum - 1) * safePageSize, total);
        int to = Math.min(from + safePageSize, total);
        List<ScreenVO> vos = new ArrayList<ScreenVO>();
        for (Screen s : all.subList(from, to)) {
            ScreenVO vo = toVO(s);
            // 列表不携带大字段，编辑器打开详情时再取全量
            vo.setComponents(null);
            vo.setVariants(null);
            vos.add(vo);
        }
        int pages = (int) Math.ceil((double) total / safePageSize);
        return new ScreenListVO(vos, total, safePageNum, safePageSize, pages);
    }

    @Override
    @Transactional
    public void publish(Long id) {
        Screen screen = selectOrThrow(id);
        screen.setStatus(1);
        if (!StringUtils.hasText(screen.getShareToken())) {
            screen.setShareToken(newShareToken());
        }
        screenMapper.updateById(screen);
        log.info("Published screen: id={}, shareToken={}", id, screen.getShareToken());
    }

    @Override
    @Transactional
    public void unpublish(Long id) {
        Screen screen = selectOrThrow(id);
        screen.setStatus(0);
        screenMapper.updateById(screen);
        log.info("Unpublished screen: id={}", id);
    }

    @Override
    @Transactional
    public ScreenVO clone(Long id, String newName) {
        Screen source = selectOrThrow(id);
        Screen copy = new Screen();
        copy.setName(StringUtils.hasText(newName) ? newName : source.getName() + " 副本");
        copy.setDescription(source.getDescription());
        copy.setConfigJson(source.getConfigJson());
        copy.setComponentsJson(source.getComponentsJson());
        copy.setVariantsJson(source.getVariantsJson());
        copy.setCoverUrl(source.getCoverUrl());
        copy.setAdaptMode(source.getAdaptMode());
        copy.setStatus(0);
        copy.setViewCount(0L);
        screenMapper.insert(copy);
        log.info("Cloned screen: from={}, to={}", id, copy.getId());
        return toVO(selectOrThrow(copy.getId()));
    }

    @Override
    @Transactional
    public String share(Long id) {
        Screen screen = selectOrThrow(id);
        if (!StringUtils.hasText(screen.getShareToken())) {
            screen.setShareToken(newShareToken());
            screenMapper.updateById(screen);
            log.info("Shared screen: id={}, token={}", id, screen.getShareToken());
        }
        return screen.getShareToken();
    }

    @Override
    public ScreenVO getByShareToken(String shareToken, String platform) {
        if (!StringUtils.hasText(shareToken)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "分享标识不能为空");
        }
        LambdaQueryWrapper<Screen> wrapper = new LambdaQueryWrapper<Screen>();
        wrapper.eq(Screen::getShareToken, shareToken);
        Screen screen = screenMapper.selectOne(wrapper);
        if (screen == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "大屏不存在或链接无效");
        }
        if (screen.getStatus() == null || screen.getStatus() != 1) {
            throw new BizException(ErrorCode.NOT_FOUND, "大屏尚未发布，分享链接不可用");
        }
        screenMapper.incrementViewCount(screen.getId());
        ScreenVO vo = toVO(screen);
        if (StringUtils.hasText(platform) && !"pc".equalsIgnoreCase(platform)) {
            applyVariant(vo, screen.getVariantsJson(), platform);
        }
        return vo;
    }

    private String newShareToken() {
        return java.util.UUID.randomUUID().toString().replace("-", "");
    }

    @Override
    @Transactional
    public void saveVariant(Long id, String platform, Map<String, Object> variant) {
        String key = platform == null ? "" : platform.toLowerCase();
        if (!VALID_PLATFORMS.contains(key)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "非法的端类型: " + platform + "，仅支持 pc/mobile/tablet");
        }
        Screen screen = selectOrThrow(id);
        Map<String, Object> variants = readVariants(screen.getVariantsJson());
        variants.put(key, variant);
        screen.setVariantsJson(writeJson(variants));
        screenMapper.updateById(screen);
        log.info("Saved variant: screenId={}, platform={}", id, key);
    }

    // ---------- 序列化 / 契约转换 ----------

    private Screen selectOrThrow(Long id) {
        Screen screen = screenMapper.selectById(id);
        if (screen == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Screen not found: " + id);
        }
        return screen;
    }

    /** width/height/layers 随画布配置一并存入 config_json（表无独立列，保持零迁移） */
    private Map<String, Object> buildConfigJson(String existingJson, ScreenUpsertDTO dto) {
        Map<String, Object> config = new LinkedHashMap<String, Object>();
        if (StringUtils.hasText(existingJson)) {
            config.putAll(readMap(existingJson));
        }
        if (dto.getConfig() != null) {
            for (Map.Entry<String, Object> entry : dto.getConfig().entrySet()) {
                if (!"width".equals(entry.getKey()) && !"height".equals(entry.getKey()) && !"layers".equals(entry.getKey())) {
                    config.put(entry.getKey(), entry.getValue());
                }
            }
        }
        if (dto.getWidth() != null) {
            config.put("width", dto.getWidth());
        }
        if (dto.getHeight() != null) {
            config.put("height", dto.getHeight());
        }
        if (dto.getLayers() != null) {
            config.put("layers", dto.getLayers());
        }
        if (!config.containsKey("width")) {
            config.put("width", DEFAULT_WIDTH);
        }
        if (!config.containsKey("height")) {
            config.put("height", DEFAULT_HEIGHT);
        }
        return config;
    }

    private ScreenVO toVO(Screen screen) {
        ScreenVO vo = new ScreenVO();
        vo.setId(screen.getId());
        vo.setTenantId(screen.getTenantId());
        vo.setName(screen.getName());
        vo.setDescription(screen.getDescription());
        vo.setCover(screen.getCoverUrl());
        vo.setStatus(statusToName(screen.getStatus()));
        vo.setAdaptMode(normalizeAdaptMode(screen.getAdaptMode(), "scale"));
        vo.setViewCount(screen.getViewCount());
        vo.setShareToken(screen.getShareToken());
        vo.setCreatedBy(screen.getCreateBy());
        vo.setCreatedAt(screen.getCreateTime());
        vo.setUpdatedAt(screen.getUpdateTime());

        Map<String, Object> configRaw = StringUtils.hasText(screen.getConfigJson())
                ? readMap(screen.getConfigJson()) : new LinkedHashMap<String, Object>();
        vo.setWidth(numberOr(configRaw.remove("width"), DEFAULT_WIDTH));
        vo.setHeight(numberOr(configRaw.remove("height"), DEFAULT_HEIGHT));
        Object layers = configRaw.remove("layers");
        if (layers instanceof List) {
            vo.setLayers((List<Object>) layers);
        }
        vo.setConfig(configRaw);
        vo.setComponents(StringUtils.hasText(screen.getComponentsJson())
                ? readList(screen.getComponentsJson()) : new ArrayList<Object>());
        if (StringUtils.hasText(screen.getVariantsJson())) {
            vo.setVariants(readVariants(screen.getVariantsJson()));
        }
        return vo;
    }

    /** 将指定端变体展平覆盖到 VO；该端缺省时保持 pc 顶层配置（同源回退） */
    private void applyVariant(ScreenVO vo, String variantsJson, String platform) {
        vo.setVariants(null);
        if (!StringUtils.hasText(variantsJson)) {
            return;
        }
        Object variantObj = readVariants(variantsJson).get(platform.toLowerCase());
        if (!(variantObj instanceof Map)) {
            return;
        }
        Map<String, Object> variant = new LinkedHashMap<String, Object>((Map<String, Object>) variantObj);
        vo.setWidth(numberOr(variant.remove("width"), vo.getWidth()));
        vo.setHeight(numberOr(variant.remove("height"), vo.getHeight()));
        Object config = variant.remove("config");
        if (config instanceof Map) {
            vo.setConfig((Map<String, Object>) config);
        }
        Object components = variant.remove("components");
        if (components instanceof List) {
            vo.setComponents((List<Object>) components);
        }
        Object layers = variant.remove("layers");
        if (layers instanceof List) {
            vo.setLayers((List<Object>) layers);
        }
    }

    private Map<String, Object> readVariants(String json) {
        return StringUtils.hasText(json) ? readMap(json) : new LinkedHashMap<String, Object>();
    }

    private Map<String, Object> readMap(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() { });
        } catch (Exception e) {
            throw new RuntimeException("大屏配置 JSON 解析失败: " + e.getMessage(), e);
        }
    }

    private List<Object> readList(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<Object>>() { });
        } catch (Exception e) {
            throw new RuntimeException("大屏组件 JSON 解析失败: " + e.getMessage(), e);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new RuntimeException("大屏配置 JSON 序列化失败: " + e.getMessage(), e);
        }
    }

    private static Integer numberOr(Object value, Integer fallback) {
        return value instanceof Number ? ((Number) value).intValue() : fallback;
    }

    private static String statusToName(Integer status) {
        if (status == null) {
            return "draft";
        }
        switch (status) {
            case 1:
                return "published";
            case 2:
                return "archived";
            default:
                return "draft";
        }
    }

    /** 兼容前端/旧数据传入的 draft/published/archived 与 0/1/2 两种形式 */
    private static Integer parseStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }
        if ("draft".equalsIgnoreCase(status)) {
            return 0;
        }
        if ("published".equalsIgnoreCase(status)) {
            return 1;
        }
        if ("archived".equalsIgnoreCase(status)) {
            return 2;
        }
        try {
            return Integer.valueOf(status);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String normalizeAdaptMode(String adaptMode, String fallback) {
        if (!StringUtils.hasText(adaptMode)) {
            return fallback == null ? "scale" : fallback.toLowerCase();
        }
        String lower = adaptMode.toLowerCase();
        if ("fixed-width".equals(lower) || "fixedwidth".equals(lower)) {
            return "fixed-width";
        }
        if ("responsive".equals(lower)) {
            return "responsive";
        }
        return "scale";
    }
}

package com.feiji.cloud.plot;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.feiji.cloud.auth.LoginUser;
import com.feiji.cloud.common.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class PlotService {

    private static final Logger log = LoggerFactory.getLogger(PlotService.class);

    private final PlotMapper plotMapper;
    private final ObjectMapper objectMapper;

    public PlotService(PlotMapper plotMapper, ObjectMapper objectMapper) {
        this.plotMapper = plotMapper;
        this.objectMapper = objectMapper;
    }

    public PlotEntity create(PlotEntity input, LoginUser user) {
        input.setId(null);
        input.setAreaMu(computeAreaMu(input.getCoordinates()));
        input.setWorkspaceId(user.workspaceId());
        input.setCreator(user.username());
        input.setCreatedAt(LocalDateTime.now());
        input.setUpdatedAt(LocalDateTime.now());
        plotMapper.insert(input);
        return input;
    }

    public PlotEntity update(Long id, PlotEntity input) {
        PlotEntity existing = requireById(id);
        existing.setName(input.getName());
        existing.setCode(input.getCode());
        existing.setCategory(input.getCategory());
        existing.setCrop(input.getCrop());
        existing.setDescription(input.getDescription());
        existing.setColor(input.getColor());
        if (input.getCoordinates() != null) {
            existing.setCoordinates(input.getCoordinates());
            existing.setAreaMu(computeAreaMu(input.getCoordinates()));
        }
        existing.setUpdatedAt(LocalDateTime.now());
        plotMapper.updateById(existing);
        return existing;
    }

    public void delete(Long id) {
        plotMapper.deleteById(id);
    }

    public PlotEntity requireById(Long id) {
        PlotEntity entity = plotMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException("地块不存在: id=" + id);
        }
        return entity;
    }

    public List<PlotEntity> list(Long workspaceId) {
        return plotMapper.selectList(new QueryWrapper<PlotEntity>()
                .eq("workspace_id", workspaceId)
                .orderByDesc("id"));
    }

    /** 多边形面积（亩）：等距圆柱近似，农用地块精度足够 */
    static double computeAreaMu(String coordinatesJson) {
        try {
            List<?> coords = new ObjectMapper().readValue(coordinatesJson, List.class);
            if (coords == null || coords.size() < 3) {
                return 0;
            }
            double lat0 = ((Number) ((List<?>) coords.get(0)).get(1)).doubleValue();
            double kx = 111320.0 * Math.cos(Math.toRadians(lat0));
            double ky = 110540.0;
            double area = 0;
            for (int i = 0; i < coords.size(); i++) {
                List<?> a = (List<?>) coords.get(i);
                List<?> b = (List<?>) coords.get((i + 1) % coords.size());
                double x1 = ((Number) a.get(0)).doubleValue() * kx;
                double y1 = ((Number) a.get(1)).doubleValue() * ky;
                double x2 = ((Number) b.get(0)).doubleValue() * kx;
                double y2 = ((Number) b.get(1)).doubleValue() * ky;
                area += x1 * y2 - x2 * y1;
            }
            return Math.round(Math.abs(area) / 2 / 666.6667 * 100) / 100.0;
        } catch (Exception e) {
            log.warn("地块面积计算失败: {}", e.getMessage());
            return 0;
        }
    }
}

package com.feiji.cloud.map;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.feiji.cloud.auth.Audited;
import com.feiji.cloud.auth.CurrentUser;
import com.feiji.cloud.auth.LoginUser;
import com.feiji.cloud.auth.RequireRole;
import com.feiji.cloud.auth.Role;
import com.feiji.cloud.common.api.HttpResult;
import com.feiji.cloud.common.exception.BusinessException;
import com.feiji.cloud.ws.WsPushService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 地图标绘元素（Web 端点位/折线/多边形）。二期对接 Pilot 2 的地图元素同步协议时，
 * 在此基础上按 element_group 结构包装推送即可。
 */
@Validated
@RestController
@RequestMapping("/api/v1/map-elements")
public class MapElementController {

    private final MapElementMapper mapper;
    private final WsPushService wsPushService;

    public MapElementController(MapElementMapper mapper, WsPushService wsPushService) {
        this.mapper = mapper;
        this.wsPushService = wsPushService;
    }

    public record CreateRequest(
            @NotBlank(message = "名称不能为空") String name,
            @NotBlank(message = "类型不能为空")
            @Pattern(regexp = "POINT|LINE|POLYGON", message = "类型须为 POINT/LINE/POLYGON") String elementType,
            @NotEmpty(message = "坐标不能为空") List<List<Double>> coordinates,
            String color) {
    }

    @GetMapping
    public HttpResult<List<MapElementEntity>> list(@CurrentUser LoginUser user) {
        return HttpResult.ok(mapper.selectList(new QueryWrapper<MapElementEntity>()
                .eq("workspace_id", user.workspaceId())
                .orderByDesc("id")));
    }

    @PostMapping
    @RequireRole(Role.OPERATOR)
    @Audited("新增地图标注")
    public HttpResult<MapElementEntity> create(@Valid @RequestBody CreateRequest request,
                                               @CurrentUser LoginUser user) {
        MapElementEntity entity = new MapElementEntity();
        entity.setName(request.name());
        entity.setElementType(request.elementType());
        try {
            entity.setCoordinates(new com.fasterxml.jackson.databind.ObjectMapper()
                    .writeValueAsString(request.coordinates()));
        } catch (Exception e) {
            throw new BusinessException("坐标序列化失败");
        }
        entity.setColor(request.color() == null ? "#f59e0b" : request.color());
        entity.setWorkspaceId(user.workspaceId());
        entity.setCreator(user.username());
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        mapper.insert(entity);
        wsPushService.push("map_element_update", Map.of("action", "create", "id", entity.getId()));
        return HttpResult.ok(entity);
    }

    @DeleteMapping("/{id}")
    @RequireRole(Role.OPERATOR)
    @Audited("删除地图标注")
    public HttpResult<Void> delete(@PathVariable Long id) {
        mapper.deleteById(id);
        wsPushService.push("map_element_update", Map.of("action", "delete", "id", id));
        return HttpResult.ok();
    }
}

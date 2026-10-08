package com.feiji.cloud.plot;

import com.feiji.cloud.auth.Audited;
import com.feiji.cloud.auth.CurrentUser;
import com.feiji.cloud.auth.LoginUser;
import com.feiji.cloud.auth.RequireRole;
import com.feiji.cloud.auth.Role;
import com.feiji.cloud.common.api.HttpResult;
import com.feiji.cloud.ws.WsPushService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/v1/plots")
public class PlotController {

    private final PlotService plotService;
    private final WsPushService wsPushService;

    public PlotController(PlotService plotService, WsPushService wsPushService) {
        this.plotService = plotService;
        this.wsPushService = wsPushService;
    }

    public record SaveRequest(
            @NotBlank(message = "地块名称不能为空") String name,
            String code,
            String category,
            String crop,
            String description,
            String color,
            @NotEmpty(message = "地块坐标不能为空") List<List<Double>> coordinates) {
    }

    @GetMapping
    public HttpResult<List<PlotEntity>> list(@CurrentUser LoginUser user) {
        return HttpResult.ok(plotService.list(user.workspaceId()));
    }

    @GetMapping("/{id}")
    public HttpResult<PlotEntity> detail(@PathVariable Long id) {
        return HttpResult.ok(plotService.requireById(id));
    }

    @PostMapping
    @RequireRole(Role.OPERATOR)
    @Audited("新增地块")
    public HttpResult<PlotEntity> create(@Valid @RequestBody SaveRequest request,
                                         @CurrentUser LoginUser user) {
        PlotEntity entity = plotService.create(toEntity(request), user);
        wsPushService.push("plot_update", java.util.Map.of("action", "create", "id", entity.getId()));
        return HttpResult.ok(entity);
    }

    @PatchMapping("/{id}")
    @RequireRole(Role.OPERATOR)
    @Audited("修改地块")
    public HttpResult<PlotEntity> update(@PathVariable Long id, @Valid @RequestBody SaveRequest request) {
        PlotEntity entity = plotService.update(id, toEntity(request));
        wsPushService.push("plot_update", java.util.Map.of("action", "update", "id", id));
        return HttpResult.ok(entity);
    }

    @DeleteMapping("/{id}")
    @RequireRole(Role.OPERATOR)
    @Audited("删除地块")
    public HttpResult<Void> delete(@PathVariable Long id) {
        plotService.delete(id);
        wsPushService.push("plot_update", java.util.Map.of("action", "delete", "id", id));
        return HttpResult.ok();
    }

    private PlotEntity toEntity(SaveRequest request) {
        PlotEntity entity = new PlotEntity();
        entity.setName(request.name());
        entity.setCode(request.code());
        entity.setCategory(request.category());
        entity.setCrop(request.crop());
        entity.setDescription(request.description());
        entity.setColor(request.color() == null ? "#22c55e" : request.color());
        try {
            entity.setCoordinates(new com.fasterxml.jackson.databind.ObjectMapper()
                    .writeValueAsString(request.coordinates()));
        } catch (Exception e) {
            throw new com.feiji.cloud.common.exception.BusinessException("地块坐标序列化失败");
        }
        return entity;
    }
}

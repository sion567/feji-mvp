package com.feiji.cloud.flight;

import com.feiji.cloud.auth.CurrentUser;
import com.feiji.cloud.auth.LoginUser;
import com.feiji.cloud.common.api.HttpResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/flights")
public class FlightController {

    private final FlightRecordService flightRecordService;

    public FlightController(FlightRecordService flightRecordService) {
        this.flightRecordService = flightRecordService;
    }

    @GetMapping
    public HttpResult<List<FlightRecordEntity>> list(@CurrentUser LoginUser user,
                                                     @RequestParam(defaultValue = "200") int limit) {
        return HttpResult.ok(flightRecordService.list(user.workspaceId(), limit));
    }

    @GetMapping("/{id}")
    public HttpResult<FlightRecordEntity> detail(@PathVariable Long id) {
        return HttpResult.ok(flightRecordService.requireById(id));
    }

    @GetMapping("/{id}/telemetry")
    public HttpResult<List<FlightTelemetryEntity>> telemetry(@PathVariable Long id,
                                                             @RequestParam(defaultValue = "20000") int limit) {
        return HttpResult.ok(flightRecordService.telemetry(id, limit));
    }
}

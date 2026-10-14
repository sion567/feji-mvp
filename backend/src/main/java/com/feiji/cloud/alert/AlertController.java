package com.feiji.cloud.alert;

import com.feiji.cloud.auth.CurrentUser;
import com.feiji.cloud.auth.LoginUser;
import com.feiji.cloud.common.api.HttpResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/alerts")
public class AlertController {

    private final AlertService alertService;

    public AlertController(AlertService alertService) {
        this.alertService = alertService;
    }

    @GetMapping
    public HttpResult<List<DeviceAlertEntity>> list(@CurrentUser LoginUser user,
                                                    @RequestParam(required = false) String sn,
                                                    @RequestParam(required = false) String level,
                                                    @RequestParam(defaultValue = "100") int limit) {
        return HttpResult.ok(alertService.list(sn, level, limit));
    }
}

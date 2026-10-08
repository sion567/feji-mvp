package com.feiji.cloud.vendor.spi;

import com.feiji.cloud.common.Vendors;
import com.feiji.cloud.common.exception.BusinessException;
import com.feiji.cloud.device.DeviceArchiveService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 指令路由：按设备的厂商把指令转发到对应 VendorAdapter。
 * 设备无档案（仅实时态）或档案缺 vendor 时回退 DJI（当前接入管线的默认厂商）。
 */
@Service
public class CommandRouter {

    private final List<VendorAdapter> adapters;
    private final DeviceArchiveService archive;

    public CommandRouter(List<VendorAdapter> adapters, DeviceArchiveService archive) {
        this.adapters = adapters;
        this.archive = archive;
    }

    public VendorAdapter route(String sn) {
        String vendor = vendorOf(sn);
        return adapters.stream()
                .filter(adapter -> adapter.supports(vendor))
                .findFirst()
                .orElseThrow(() -> new BusinessException("没有适配器支持设备厂商: " + vendor + " (sn=" + sn + ")"));
    }

    public String vendorOf(String sn) {
        return archive.vendorOf(sn);
    }
}

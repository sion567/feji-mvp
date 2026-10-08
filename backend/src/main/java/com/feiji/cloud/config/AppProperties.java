package com.feiji.cloud.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

/**
 * 平台自有配置（app.*）。安全与 MQTT 的默认值仅供本地开发，生产环境必须显式覆盖。
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @DefaultValue Security security,
        @DefaultValue Mqtt mqtt,
        @DefaultValue Osd osd,
        @DefaultValue Live live,
        @DefaultValue Wayline wayline) {

    public record Security(
            @DefaultValue("admin") String adminUsername,
            @DefaultValue("admin123") String adminPassword,
            @DefaultValue("feiji-cloud-dev-jwt-secret-change-in-production-32bytes!") String jwtSecret,
            @DefaultValue("24") long jwtExpireHours) {
    }

    public record Mqtt(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("tcp://localhost:1883") String host,
            @DefaultValue("feiji-cloud-server") String clientId,
            @DefaultValue("") String username,
            @DefaultValue("") String password,
            @DefaultValue("30") int keepAlive,
            @DefaultValue({
                    "thing/product/+/osd",
                    "thing/product/+/state",
                    "thing/product/+/events",
                    "thing/product/+/services_reply",
                    "sys/product/+/status",
                    "thing/product/+/drc/up"
            }) List<String> subscribeTopics) {
    }

    public record Osd(
            @DefaultValue("1000") long pushIntervalMs,
            @DefaultValue("168") long osdTtlHours) {
    }

    /**
     * 直播配置：rtmpHost 为设备可达的推流服务器地址（host:port），playBaseUrl 为网页拉流基址。
     * 本机开发默认指向 docker-compose 里的 ZLMediaKit；zlmSecret 留空则跳过 ZLM API 调用。
     */
    public record Live(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("localhost:1935") String rtmpHost,
            @DefaultValue("live") String app,
            @DefaultValue("http://localhost:8080") String playBaseUrl,
            @DefaultValue("http://localhost:8080") String zlmUrl,
            @DefaultValue("") String zlmSecret,
            @DefaultValue("15000") long replyTimeoutMs,
            @DefaultValue("zlm") String mediaServerType) {
    }

    /**
     * 航线配置：storageDir 为 KMZ 本地存储目录（二期可切 MinIO）；
     * downloadBaseUrl 为设备拉取 KMZ 的地址前缀（真机场景须为设备可达的 IP/域名）。
     */
    public record Wayline(
            @DefaultValue("./data/waylines") String storageDir,
            @DefaultValue("http://localhost:6789") String downloadBaseUrl) {
    }
}

package com.feiji.cloud.cloudapi.wayline;

/**
 * 航线文件解析失败（KMZ 结构缺失、XML 非法等）。
 */
public class WaylineParseException extends RuntimeException {

    public WaylineParseException(String message) {
        super(message);
    }

    public WaylineParseException(String message, Throwable cause) {
        super(message, cause);
    }
}

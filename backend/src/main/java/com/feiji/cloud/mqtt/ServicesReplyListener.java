package com.feiji.cloud.mqtt;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 设备 services_reply 监听器。ServicesReplyHandler 收到指令应答后逐一通知，
 * 请求-响应配对靠 tid/bid 由各业务自行维护。
 */
public interface ServicesReplyListener {

    void onServicesReply(String sn, String method, String bid, JsonNode data);
}

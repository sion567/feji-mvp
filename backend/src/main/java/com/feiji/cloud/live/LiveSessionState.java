package com.feiji.cloud.live;

/**
 * 直播会话状态机：
 * STARTING --(services_reply result=0)--> LIVE --(stop 指令)--> STOPPING --> 会话移除
 * STARTING/STOPPING 超时未收到应答 --定时巡检--> FAILED / 强制移除
 */
public enum LiveSessionState {
    STARTING,
    LIVE,
    STOPPING,
    FAILED
}

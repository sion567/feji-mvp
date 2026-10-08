package com.feiji.cloud.wayline;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 航线任务。task_type: IMMEDIATE(立即) / TIMED(定时单次) / RECURRING(每日巡检)；
 * status: PENDING(待派发) → DISPATCHING(指令已下发) → RUNNING(执行中) → SUCCESS/FAILED/CANCELED。
 */
@TableName("wayline_job")
public class WaylineJobEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String flightId;
    private Long waylineFileId;
    private String droneSn;
    private Long workspaceId;
    private String taskType;
    private Long executeTime;
    private String status;
    private Integer progress;
    private String message;
    private String bid;
    private String reportStatus;
    private String reportNo;
    private String creator;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getFlightId() { return flightId; }
    public void setFlightId(String flightId) { this.flightId = flightId; }
    public Long getWaylineFileId() { return waylineFileId; }
    public void setWaylineFileId(Long waylineFileId) { this.waylineFileId = waylineFileId; }
    public String getDroneSn() { return droneSn; }
    public void setDroneSn(String droneSn) { this.droneSn = droneSn; }
    public Long getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(Long workspaceId) { this.workspaceId = workspaceId; }
    public String getTaskType() { return taskType; }
    public void setTaskType(String taskType) { this.taskType = taskType; }
    public Long getExecuteTime() { return executeTime; }
    public void setExecuteTime(Long executeTime) { this.executeTime = executeTime; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getProgress() { return progress; }
    public void setProgress(Integer progress) { this.progress = progress; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getBid() { return bid; }
    public void setBid(String bid) { this.bid = bid; }
    public String getReportStatus() { return reportStatus; }
    public void setReportStatus(String reportStatus) { this.reportStatus = reportStatus; }
    public String getReportNo() { return reportNo; }
    public void setReportNo(String reportNo) { this.reportNo = reportNo; }
    public String getCreator() { return creator; }
    public void setCreator(String creator) { this.creator = creator; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}

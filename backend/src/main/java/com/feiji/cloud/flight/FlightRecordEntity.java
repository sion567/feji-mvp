package com.feiji.cloud.flight;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 架次记录：OSD 飞行状态跳变自动开闭（status: FLYING/CLOSED）。
 * report_status: NONE(未报备)/FILED(已报备)/APPROVED(已批复)，报备责任在运营单位，平台仅留痕。
 */
@TableName("flight_record")
public class FlightRecordEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String droneSn;
    private Long jobId;
    private String jobName;
    private String status;
    private Long takeoffTime;
    private Long landingTime;
    private Double takeoffLat;
    private Double takeoffLng;
    private Double landingLat;
    private Double landingLng;
    private Double maxHeight;
    private Double distanceM;
    private String reportStatus;
    private String reportNo;
    private Long workspaceId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getDroneSn() { return droneSn; }
    public void setDroneSn(String droneSn) { this.droneSn = droneSn; }
    public Long getJobId() { return jobId; }
    public void setJobId(Long jobId) { this.jobId = jobId; }
    public String getJobName() { return jobName; }
    public void setJobName(String jobName) { this.jobName = jobName; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getTakeoffTime() { return takeoffTime; }
    public void setTakeoffTime(Long takeoffTime) { this.takeoffTime = takeoffTime; }
    public Long getLandingTime() { return landingTime; }
    public void setLandingTime(Long landingTime) { this.landingTime = landingTime; }
    public Double getTakeoffLat() { return takeoffLat; }
    public void setTakeoffLat(Double takeoffLat) { this.takeoffLat = takeoffLat; }
    public Double getTakeoffLng() { return takeoffLng; }
    public void setTakeoffLng(Double takeoffLng) { this.takeoffLng = takeoffLng; }
    public Double getLandingLat() { return landingLat; }
    public void setLandingLat(Double landingLat) { this.landingLat = landingLat; }
    public Double getLandingLng() { return landingLng; }
    public void setLandingLng(Double landingLng) { this.landingLng = landingLng; }
    public Double getMaxHeight() { return maxHeight; }
    public void setMaxHeight(Double maxHeight) { this.maxHeight = maxHeight; }
    public Double getDistanceM() { return distanceM; }
    public void setDistanceM(Double distanceM) { this.distanceM = distanceM; }
    public String getReportStatus() { return reportStatus; }
    public void setReportStatus(String reportStatus) { this.reportStatus = reportStatus; }
    public String getReportNo() { return reportNo; }
    public void setReportNo(String reportNo) { this.reportNo = reportNo; }
    public Long getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(Long workspaceId) { this.workspaceId = workspaceId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}

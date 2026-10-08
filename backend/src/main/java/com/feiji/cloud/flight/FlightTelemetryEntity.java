package com.feiji.cloud.flight;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("flight_telemetry")
public class FlightTelemetryEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long flightId;
    private Long ts;
    private Double lat;
    private Double lng;
    private Double alt;
    private Double elevation;
    private Double speed;
    private Double heading;
    private Integer battery;
    private String mode;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getFlightId() { return flightId; }
    public void setFlightId(Long flightId) { this.flightId = flightId; }
    public Long getTs() { return ts; }
    public void setTs(Long ts) { this.ts = ts; }
    public Double getLat() { return lat; }
    public void setLat(Double lat) { this.lat = lat; }
    public Double getLng() { return lng; }
    public void setLng(Double lng) { this.lng = lng; }
    public Double getAlt() { return alt; }
    public void setAlt(Double alt) { this.alt = alt; }
    public Double getElevation() { return elevation; }
    public void setElevation(Double elevation) { this.elevation = elevation; }
    public Double getSpeed() { return speed; }
    public void setSpeed(Double speed) { this.speed = speed; }
    public Double getHeading() { return heading; }
    public void setHeading(Double heading) { this.heading = heading; }
    public Integer getBattery() { return battery; }
    public void setBattery(Integer battery) { this.battery = battery; }
    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
}

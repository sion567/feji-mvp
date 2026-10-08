<script setup lang="ts">
import { computed } from 'vue'
import type { DeviceEntry } from '../api/deviceApi'
import { droneModeLabel, dockModeLabel, coverLabel } from '../lib/modeCode'
import { kindLabel, fmtNum } from '../lib/device'

const props = defineProps<{
  device: DeviceEntry | null
  osd: any
  showTrack?: boolean
  drcActive?: boolean
  flytoMode?: boolean
  canOp?: boolean
}>()

const emit = defineEmits<{
  (e: 'toggle-track'): void
  (e: 'drc-toggle'): void
  (e: 'takeoff'): void
  (e: 'flyto-toggle'): void
  (e: 'control', x: number, y: number, z: number): void
}>()

const rows = computed(() => {
  if (!props.device) return []
  const osd = props.osd || {}
  const list: { label: string; value: string }[] = [
    { label: 'SN', value: props.device.sn },
    { label: '型号', value: props.device.modelName || props.device.model || '-' },
    { label: '类型', value: kindLabel(props.device.kind) },
    { label: '状态', value: props.device.online ? '在线' : '离线' },
    { label: '经纬度', value: `${fmtNum(osd.latitude)}, ${fmtNum(osd.longitude)}` },
    { label: '高度(椭球)', value: fmtNum(osd.height, ' m') }
  ]
  if (props.device.kind === 'dock') {
    list.push(
      { label: '机场模式', value: dockModeLabel(osd.mode_code) },
      { label: '舱盖', value: coverLabel(osd.cover_state) },
      { label: '机内无人机', value: osd.drone_in_dock == null ? '-' : osd.drone_in_dock ? '在舱' : '不在舱' },
      { label: '环境温度', value: fmtNum(osd.temperature, ' ℃') },
      { label: '湿度', value: osd.humidity != null ? `${osd.humidity} %` : '-' },
      { label: '风速', value: fmtNum(osd.wind_speed, ' m/s') },
      { label: '累计架次', value: osd.job_number != null ? String(osd.job_number) : '-' }
    )
  } else {
    list.push(
      { label: '相对起飞点高度', value: fmtNum(osd.elevation, ' m') },
      { label: '飞行模式', value: droneModeLabel(osd.mode_code) },
      { label: '电量', value: osd.battery?.capacity_percent != null ? `${osd.battery.capacity_percent} %` : '-' },
      { label: '水平速度', value: fmtNum(osd.horizontal_speed, ' m/s') },
      { label: '垂直速度', value: fmtNum(osd.vertical_speed, ' m/s') },
      { label: '航向', value: fmtNum(osd.attitude_head, ' °') },
      { label: '返航点距离', value: fmtNum(osd.home_distance, ' m') },
      { label: '风速', value: fmtNum(osd.wind_speed, ' m/s') }
    )
  }
  return list
})
</script>

<template>
  <div class="detail-panel">
    <div class="panel-title detail-header">
      <span>设备详情</span>
      <button v-if="device" class="track-btn" @click="emit('toggle-track')">
        {{ showTrack ? '隐藏轨迹' : '回放轨迹' }}
      </button>
    </div>
    <p v-if="!device" class="empty">点击地图上的设备或左侧列表查看详情。</p>
    <template v-else>
      <p v-if="!osd" class="empty">等待 OSD 数据…（设备上报后自动刷新）</p>
      <div v-else class="rows">
        <div v-for="row in rows" :key="row.label" class="row">
          <span class="label">{{ row.label }}</span>
          <span class="value">{{ row.value }}</span>
        </div>
      </div>
    </template>
    <template v-if="device && canOp">
      <div class="drc-block">
        <div class="drc-row">
          <button class="drc-btn" @click="emit('takeoff')">一键起飞</button>
          <button class="drc-btn" :class="{ on: flytoMode }" @click="emit('flyto-toggle')">
            指点飞行
          </button>
          <button class="drc-btn" :class="{ on: drcActive }" @click="emit('drc-toggle')">
            {{ drcActive ? '退出DRC' : '进入DRC' }}
          </button>
        </div>
        <div v-if="drcActive" class="drc-row pad">
          <span></span>
          <button class="pad-btn" @click="emit('control', 0, 512, 0)">↑</button>
          <span></span>
          <button class="pad-btn" @click="emit('control', -512, 0, 0)">←</button>
          <button class="pad-btn stop" @click="emit('control', 0, 0, 0)">■</button>
          <button class="pad-btn" @click="emit('control', 512, 0, 0)">→</button>
          <span></span>
          <button class="pad-btn" @click="emit('control', 0, -512, 0)">↓</button>
          <span></span>
        </div>
        <p v-if="flytoMode" class="drc-hint">在地图上点击目标点飞行</p>
        <p v-if="drcActive" class="drc-hint">DRC 模式已激活，可使用方向控制</p>
      </div>
    </template>
  </div>
</template>

<style scoped>
.detail-panel {
  padding: 12px;
}
.detail-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.drc-block {
  margin-top: 10px;
  border-top: 1px solid var(--border);
  padding-top: 8px;
}
.drc-row {
  display: flex;
  gap: 6px;
  align-items: center;
  margin-bottom: 6px;
}
.drc-row.pad {
  justify-content: center;
  display: grid;
  grid-template-columns: repeat(3, 34px);
  gap: 4px;
}
.drc-btn {
  flex: 1;
  background: var(--panel);
  border: 1px solid var(--border);
  border-radius: 4px;
  color: var(--text);
  cursor: pointer;
  font-size: 11px;
  padding: 5px 2px;
}
.drc-btn.on {
  border-color: var(--accent);
  color: var(--accent);
}
.pad-btn {
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: 4px;
  color: var(--text);
  cursor: pointer;
  padding: 5px 0;
}
.pad-btn.stop {
  color: #f87171;
}
.drc-hint {
  color: var(--muted);
  font-size: 11px;
  margin: 2px 0 0;
}
.track-btn {
  background: none;
  border: 1px solid var(--border);
  border-radius: 4px;
  color: var(--accent);
  cursor: pointer;
  font-size: 11px;
  padding: 2px 8px;
}
.rows {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.row {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  border-bottom: 1px dashed rgba(255, 255, 255, 0.06);
  padding-bottom: 4px;
}
.label {
  color: var(--muted);
  flex-shrink: 0;
}
.value {
  text-align: right;
  word-break: break-all;
}
</style>

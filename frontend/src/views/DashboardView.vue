<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { listDevices, getTrack, type DeviceEntry, type TrackPoint } from '../api/deviceApi'
import { listLives, type LiveSessionView } from '../api/liveApi'
import {
  listMapElements, createMapElement, deleteMapElement, type MapElementView
} from '../api/mapApi'
import { listPlots, type PlotView } from '../api/plotApi'
import { drcEnter, drcExit, drcFlyTo, drcTakeoff, drcControl, drcState } from '../api/drcApi'
import { canOperate } from '../api/auth'
import { useWs } from '../ws/wsClient'
import { kindLabel } from '../lib/device'
import AppHeader from '../components/AppHeader.vue'
import DeviceMap from '../components/DeviceMap.vue'
import DeviceDetail from '../components/DeviceDetail.vue'
import LivePanel from '../components/LivePanel.vue'
import AlertFeed from '../components/AlertFeed.vue'

const devices = ref<DeviceEntry[]>([])
const osdBySn = ref<Record<string, any>>({})
const liveSessions = ref<LiveSessionView[]>([])
const mapElements = ref<MapElementView[]>([])
const plotList = ref<PlotView[]>([])
const selectedSn = ref<string | null>(null)
const showTrack = ref(false)
const trackPoints = ref<TrackPoint[]>([])
const drawMode = ref<'POINT' | 'LINE' | 'POLYGON' | null>(null)
const flytoMode = ref(false)
const drcActiveMap = ref<Record<string, boolean>>({})
const mapComp = ref<InstanceType<typeof DeviceMap>>()
const { subscribe } = useWs()
const router = useRouter()

const selected = computed(() => devices.value.find((d) => d.sn === selectedSn.value) ?? null)
const selectedOsd = computed(() => (selectedSn.value ? osdBySn.value[selectedSn.value] : null))
const onlineCount = computed(() => devices.value.filter((d) => d.online).length)
const selectedDrcActive = computed(() =>
  selected.value ? !!drcActiveMap.value[selected.value.parentSn || selected.value.sn] : false
)

let unsub: (() => void) | undefined

onMounted(async () => {
  await Promise.all([refresh(), refreshLives(), refreshElements(), refreshPlots(), refreshDrcState()])
  unsub = subscribe((type, data) => {
    if (type === 'osd_update') {
      osdBySn.value = { ...osdBySn.value, [data.sn]: data.data }
    } else if (type === 'device_status_update') {
      refresh()
    } else if (type === 'live_update' || type === 'live_capacity_update') {
      refreshLives()
    } else if (type === 'map_element_update') {
      refreshElements()
    } else if (type === 'plot_update') {
      refreshPlots()
    } else if (type === 'drc_update') {
      drcActiveMap.value = { ...drcActiveMap.value, [data.sn]: data.active }
    }
  })
})

onUnmounted(() => unsub?.())

async function refresh() {
  try {
    devices.value = await listDevices()
  } catch {
    // 401 由 http 层统一处理
  }
}

async function refreshLives() {
  try {
    liveSessions.value = await listLives()
  } catch {
    // 直播模块不可用时忽略
  }
}

async function refreshElements() {
  try {
    mapElements.value = (await listMapElements()).map((el) => ({
      ...el,
      coordinates: typeof el.coordinates === 'string' ? JSON.parse(el.coordinates) : el.coordinates
    }))
  } catch {
    // 元素模块不可用时忽略
  }
}

async function refreshPlots() {
  try {
    plotList.value = (await listPlots()).map((p) => ({
      ...p,
      coordinates: typeof p.coordinates === 'string' ? JSON.parse(p.coordinates) : p.coordinates
    }))
  } catch {
    // 地块模块不可用时忽略
  }
}

async function refreshDrcState() {
  try {
    const state: Record<string, boolean> = {}
    for (const sn of await drcState()) state[sn] = true
    drcActiveMap.value = state
  } catch {
    // 忽略
  }
}

function select(sn: string) {
  selectedSn.value = sn
  showTrack.value = false
  trackPoints.value = []
  flytoMode.value = false
}

async function toggleTrack() {
  showTrack.value = !showTrack.value
  if (showTrack.value && selectedSn.value) {
    try {
      trackPoints.value = await getTrack(selectedSn.value)
    } catch {
      trackPoints.value = []
    }
  } else {
    trackPoints.value = []
  }
}

function open3D() {
  if (selectedSn.value) router.push({ path: '/track3d', query: { sn: selectedSn.value } })
}

// ---- 地图标绘 ----

function setDrawMode(mode: 'POINT' | 'LINE' | 'POLYGON' | null) {
  drawMode.value = drawMode.value === mode ? null : mode
  flytoMode.value = false
}

async function onDrawFinish(coordinates: [number, number][]) {
  const name = prompt('标注名称：')
  if (!name) return
  try {
    await createMapElement({ name, elementType: drawMode.value!, coordinates })
    drawMode.value = null
    await refreshElements()
  } catch (e) {
    alert(e instanceof Error ? e.message : '保存标注失败')
  }
}

async function onDeleteElement(el: MapElementView) {
  if (!confirm(`删除标注「${el.name}」？`)) return
  try {
    await deleteMapElement(el.id)
    await refreshElements()
  } catch (e) {
    alert(e instanceof Error ? e.message : '删除失败')
  }
}

// ---- DRC 指令飞行 ----

function toggleFlyto() {
  flytoMode.value = !flytoMode.value
  drawMode.value = null
}

async function onMapClick(lat: number, lng: number) {
  const sn = selected.value ? selected.value.parentSn || selected.value.sn : null
  if (!sn) return
  try {
    await drcFlyTo(sn, lat, lng, 60)
    flytoMode.value = false
  } catch (e) {
    alert(e instanceof Error ? e.message : '指点飞行失败')
  }
}

async function onDrcToggle() {
  const sn = selected.value ? selected.value.parentSn || selected.value.sn : null
  if (!sn) return
  try {
    if (selectedDrcActive.value) {
      await drcExit(sn)
    } else {
      await drcEnter(sn)
    }
  } catch (e) {
    alert(e instanceof Error ? e.message : 'DRC 操作失败')
  }
}

async function onTakeoff() {
  const sn = selected.value ? selected.value.parentSn || selected.value.sn : null
  if (!sn) return
  const height = prompt('一键起飞高度（米）：', '30')
  if (height == null) return
  try {
    await drcTakeoff(sn, Number(height))
  } catch (e) {
    alert(e instanceof Error ? e.message : '一键起飞失败')
  }
}

async function onControl(x: number, y: number, z: number) {
  const sn = selected.value ? selected.value.parentSn || selected.value.sn : null
  if (!sn) return
  try {
    await drcControl(sn, x, y, z)
  } catch (e) {
    alert(e instanceof Error ? e.message : '控制指令失败')
  }
}
</script>

<template>
  <div class="page dashboard">
    <AppHeader title="无人机自控平台 · 态势大屏" />
    <div class="draw-bar">
      <span class="hint">标绘：</span>
      <button :class="{ on: drawMode === 'POINT' }" @click="setDrawMode('POINT')">点位</button>
      <button :class="{ on: drawMode === 'LINE' }" @click="setDrawMode('LINE')">折线</button>
      <button :class="{ on: drawMode === 'POLYGON' }" @click="setDrawMode('POLYGON')">多边形</button>
      <button v-if="drawMode === 'LINE' || drawMode === 'POLYGON'" @click="mapComp?.finishDraw()">完成</button>
      <button v-if="drawMode" @click="mapComp?.cancelDraw(); drawMode = null">取消</button>
      <span class="hint" v-if="drawMode">
        {{ drawMode === 'POINT' ? '点击地图放置点位' : '点击地图采点，完成后保存' }}
      </span>
    </div>
    <div class="layout">
      <aside class="sidebar panel">
        <div class="panel-title">设备（{{ onlineCount }}/{{ devices.length }} 在线）</div>
        <ul class="device-list">
          <li
            v-for="d in devices"
            :key="d.sn"
            :class="{ selected: d.sn === selectedSn }"
            @click="select(d.sn)"
          >
            <span class="dot" :class="d.online ? 'on' : 'off'"></span>
            <div class="info">
              <div class="name">{{ d.deviceName || d.modelName || d.sn }}</div>
              <div class="sub">{{ kindLabel(d.kind) }} · {{ d.sn }}</div>
            </div>
            <span
              v-if="osdBySn[d.sn]?.battery?.capacity_percent != null"
              class="battery"
            >{{ osdBySn[d.sn].battery.capacity_percent }}%</span>
          </li>
        </ul>
        <p v-if="devices.length === 0" class="empty">
          暂无设备，设备上电接入后自动出现在这里。
        </p>
        <div class="panel-title element-title">地图标注（{{ mapElements.length }}）</div>
        <ul class="element-list">
          <li v-for="el in mapElements" :key="el.id">
            <span class="el-dot" :style="{ background: el.color || '#f59e0b' }"></span>
            <span class="el-name">{{ el.name }}</span>
            <button v-if="canOperate()" class="el-del" @click="onDeleteElement(el)">×</button>
          </li>
        </ul>
      </aside>
      <main class="map-area panel">
        <DeviceMap
          ref="mapComp"
          :devices="devices"
          :osd-by-sn="osdBySn"
          :selected-sn="selectedSn"
          :track-points="trackPoints"
          :elements="mapElements"
          :plots="plotList"
          :draw-mode="drawMode"
          :flyto-mode="flytoMode"
          @select="select"
          @draw-finish="onDrawFinish"
          @map-click="onMapClick"
        />
        <AlertFeed />
      </main>
      <aside class="detail panel">
        <LivePanel :device="selected" :sessions="liveSessions" />
        <DeviceDetail
          :device="selected"
          :osd="selectedOsd"
          :show-track="showTrack"
          :drc-active="selectedDrcActive"
          :flyto-mode="flytoMode"
          :can-op="canOperate()"
          @toggle-track="toggleTrack"
          @open-3d="open3D"
          @drc-toggle="onDrcToggle"
          @takeoff="onTakeoff"
          @flyto-toggle="toggleFlyto"
          @control="onControl"
        />
      </aside>
    </div>
  </div>
</template>

<style scoped>
.draw-bar {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 10px;
  border-bottom: 1px solid var(--border);
  background: var(--panel);
}
.draw-bar button {
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: 4px;
  color: var(--text);
  cursor: pointer;
  font-size: 12px;
  padding: 3px 10px;
}
.draw-bar button.on {
  border-color: var(--accent);
  color: var(--accent);
}
.draw-bar .hint {
  color: var(--muted);
  font-size: 12px;
}
.layout {
  display: grid;
  grid-template-columns: 280px 1fr 320px;
  gap: 10px;
  padding: 10px;
  height: calc(100% - 84px);
}
.device-list {
  list-style: none;
  margin: 0;
  padding: 8px;
  overflow-y: auto;
  max-height: 46%;
}
.device-list li {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px;
  border-radius: 6px;
  cursor: pointer;
}
.device-list li:hover {
  background: rgba(255, 255, 255, 0.04);
}
.device-list li.selected {
  background: rgba(59, 130, 246, 0.15);
}
.info {
  flex: 1;
  min-width: 0;
}
.name {
  font-size: 13px;
}
.sub {
  color: var(--muted);
  font-size: 11px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.battery {
  color: var(--green);
  font-size: 12px;
}
.element-title {
  border-top: 1px solid var(--border);
  margin-top: 6px;
}
.element-list {
  list-style: none;
  margin: 0;
  padding: 0 8px 8px;
  overflow-y: auto;
}
.element-list li {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 6px;
  font-size: 12px;
}
.el-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}
.el-name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.el-del {
  background: none;
  border: none;
  color: var(--muted);
  cursor: pointer;
  font-size: 14px;
}
.el-del:hover {
  color: #f87171;
}
.map-area {
  padding: 0;
  overflow: hidden;
  position: relative;
}
</style>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import L from 'leaflet'
import { tdtBaseLayers } from '../lib/tdt'
import {
  listFlights, flightTelemetry,
  type FlightRecordView, type FlightTelemetryPoint
} from '../api/flightApi'
import { useWs } from '../ws/wsClient'
import AppHeader from '../components/AppHeader.vue'

const flights = ref<FlightRecordView[]>([])
const selected = ref<FlightRecordView | null>(null)
const points = ref<FlightTelemetryPoint[]>([])
const loadingTrack = ref(false)
const error = ref('')
const { subscribe } = useWs()

const mapEl = ref<HTMLElement>()
let map: L.Map | null = null
let trackLayer: L.LayerGroup | null = null

const statusLabel: Record<string, string> = { FLYING: '飞行中', CLOSED: '已结束' }
const reportLabel: Record<string, string> = { NONE: '未报备', FILED: '已报备', APPROVED: '已批复' }
const router = useRouter()

function go3D() {
  if (selected.value) router.push({ path: '/track3d', query: { flight: String(selected.value.id) } })
}

async function refresh() {
  try {
    flights.value = await listFlights()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载失败'
  }
}

function durationOf(f: FlightRecordView) {
  if (!f.takeoffTime || !f.landingTime) return '-'
  const sec = Math.round((f.landingTime - f.takeoffTime) / 1000)
  if (sec < 60) return `${sec} 秒`
  return `${Math.floor(sec / 60)} 分 ${sec % 60} 秒`
}

function fmtTime(ms: number | null) {
  return ms ? new Date(ms).toLocaleString() : '-'
}

async function onSelect(f: FlightRecordView) {
  selected.value = f
  points.value = []
  if (f.status !== 'CLOSED' && f.takeoffTime == null) return
  loadingTrack.value = true
  try {
    points.value = (await flightTelemetry(f.id)).filter((p) => p.lat != null && p.lng != null)
    drawTrack()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '轨迹加载失败'
  } finally {
    loadingTrack.value = false
  }
}

function drawTrack() {
  if (!map || !trackLayer) return
  trackLayer.clearLayers()
  const f = selected.value
  if (!f) return
  if (points.value.length > 1) {
    L.polyline(points.value.map((p) => [p.lat!, p.lng!] as [number, number]), {
      color: '#38bdf8', weight: 3, opacity: 0.9
    }).addTo(trackLayer)
  }
  if (f.takeoffLat != null && f.takeoffLng != null) {
    L.circleMarker([f.takeoffLat, f.takeoffLng], { radius: 7, color: '#22c55e', fillOpacity: 1 })
      .bindTooltip('起飞点').addTo(trackLayer)
  }
  if (f.landingLat != null && f.landingLng != null) {
    L.circleMarker([f.landingLat, f.landingLng], { radius: 7, color: '#f87171', fillOpacity: 1 })
      .bindTooltip('降落点').addTo(trackLayer)
  }
  const bounds: L.LatLngExpression[] = []
  if (f.takeoffLat != null) bounds.push([f.takeoffLat, f.takeoffLng!])
  if (f.landingLat != null) bounds.push([f.landingLat, f.landingLng!])
  points.value.slice(0, 2000).forEach((p) => bounds.push([p.lat!, p.lng!]))
  if (bounds.length > 1) {
    map.fitBounds(L.latLngBounds(bounds), { padding: [30, 30] })
  }
}

watch(selected, drawTrack)

onMounted(() => {
  map = L.map(mapEl.value!, { center: [31.02, 121.44], zoom: 14 })
  const layers = tdtBaseLayers(import.meta.env.VITE_TDT_TK)
  layers['影像'].addTo(map)
  L.control.layers({ 影像: layers['影像'], 矢量: layers['矢量'] }, undefined, { position: 'topright' }).addTo(map)
  trackLayer = L.layerGroup().addTo(map)
  refresh()
  subscribe((type) => {
    if (type === 'flight_update') refresh()
  })
})

onBeforeUnmount(() => {
  map?.remove()
  map = null
})

const summary = computed(() => {
  const closed = flights.value.filter((f) => f.status === 'CLOSED')
  const totalM = closed.reduce((n, f) => n + (f.distanceM || 0), 0)
  return `架次 ${flights.value.length}（进行中 ${flights.value.length - closed.length}）· 累计里程 ${(totalM / 1000).toFixed(2)} km`
})
</script>

<template>
  <div class="page">
    <AppHeader title="飞行记录" />
    <main class="layout">
      <aside class="list-panel panel">
        <div class="panel-title">{{ summary }}</div>
        <p v-if="error" class="error">{{ error }}</p>
        <div class="table-wrap">
          <table class="table">
            <thead>
              <tr><th>架次</th><th>设备</th><th>起飞时间</th><th>时长</th><th>里程</th><th>最大高度</th><th>状态</th><th>报备</th></tr>
            </thead>
            <tbody>
              <tr
                v-for="f in flights"
                :key="f.id"
                :class="{ selected: selected?.id === f.id }"
                @click="onSelect(f)"
              >
                <td>#{{ f.id }}</td>
                <td class="sn">{{ f.droneSn }}</td>
                <td>{{ fmtTime(f.takeoffTime) }}</td>
                <td>{{ durationOf(f) }}</td>
                <td>{{ f.distanceM != null ? (f.distanceM / 1000).toFixed(2) + ' km' : '-' }}</td>
                <td>{{ f.maxHeight != null ? f.maxHeight + ' m' : '-' }}</td>
                <td :style="{ color: f.status === 'FLYING' ? 'var(--accent)' : 'var(--muted)' }">
                  {{ statusLabel[f.status] || f.status }}
                </td>
                <td>{{ reportLabel[f.reportStatus || 'NONE'] || '-' }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <p v-if="flights.length === 0" class="empty">暂无架次：设备起飞后自动生成飞行记录。</p>
      </aside>
      <main class="map-panel panel">
        <div class="map-title">
          <template v-if="selected">
            架次 #{{ selected.id }} · {{ selected.droneSn }}
            <template v-if="selected.jobName"> · 任务「{{ selected.jobName }}」</template>
            <template v-if="loadingTrack"> · 轨迹加载中…</template>
            <button v-if="points.length > 1" class="btn-3d" @click="go3D">3D 回放</button>
          </template>
          <template v-else>点击左侧架次查看飞行轨迹</template>
        </div>
        <div class="map-wrap">
          <div ref="mapEl" class="flight-map"></div>
          <div v-if="selected" class="stat-bar">
            <span>起飞 {{ fmtTime(selected.takeoffTime) }}</span>
            <span>降落 {{ fmtTime(selected.landingTime) }}</span>
            <span>时长 {{ durationOf(selected) }}</span>
            <span>里程 {{ selected.distanceM != null ? (selected.distanceM / 1000).toFixed(2) + ' km' : '-' }}</span>
            <span>最大高度 {{ selected.maxHeight != null ? selected.maxHeight + ' m' : '-' }}</span>
            <span>采样点 {{ points.length }}</span>
          </div>
        </div>
      </main>
    </main>
  </div>
</template>

<style scoped>
.layout {
  display: grid;
  grid-template-columns: 1.4fr 1fr;
  gap: 10px;
  padding: 10px;
  height: calc(100% - 48px);
}
.list-panel {
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.table-wrap {
  flex: 1;
  overflow: auto;
  padding: 0 8px 8px;
}
.table {
  width: 100%;
  border-collapse: collapse;
}
.table th, .table td {
  padding: 8px 8px;
  border-bottom: 1px solid var(--border);
  font-size: 12px;
  text-align: left;
  white-space: nowrap;
}
.table th {
  color: var(--muted);
  font-weight: normal;
}
.table tbody tr {
  cursor: pointer;
}
.table tbody tr:hover {
  background: rgba(255, 255, 255, 0.04);
}
.table tbody tr.selected {
  background: rgba(59, 130, 246, 0.15);
}
.sn {
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
}
.map-panel {
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.map-title {
  padding: 10px 12px;
  color: var(--muted);
  font-size: 12px;
}
.btn-3d {
  margin-left: 10px;
  background: none;
  border: 1px solid var(--border);
  border-radius: 4px;
  color: var(--accent);
  cursor: pointer;
  font-size: 11px;
  padding: 2px 8px;
}
.map-wrap {
  flex: 1;
  position: relative;
}
.flight-map {
  height: 100%;
  width: 100%;
}
.stat-bar {
  position: absolute;
  bottom: 12px;
  left: 12px;
  right: 12px;
  display: flex;
  gap: 14px;
  flex-wrap: wrap;
  background: rgba(17, 26, 46, 0.92);
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 8px 12px;
  font-size: 12px;
  color: var(--muted);
}
.empty {
  color: var(--muted);
  font-size: 12px;
  padding: 8px 12px;
}
.error {
  color: #f87171;
  font-size: 12px;
  padding: 4px 12px;
}
</style>

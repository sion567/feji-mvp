<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { Map as MlMap, Marker } from 'maplibre-gl'
import type { GeoJSONSource } from 'maplibre-gl'
import 'maplibre-gl/dist/maplibre-gl.css'
import { tdtStyle } from '../lib/tdtMaplibre'
import { getTrack } from '../api/deviceApi'
import { flightDetail, flightTelemetry } from '../api/flightApi'
import AppHeader from '../components/AppHeader.vue'

/** 归一化回放点：alt 为相对起飞点高度（米） */
interface ReplayPoint {
  lng: number
  lat: number
  alt: number
  ts: number
  speed: number | null
}

const route = useRoute()
const title = ref('3D 轨迹回放')
const loading = ref(true)
const error = ref('')
const notEnough = ref(false)

const playing = ref(false)
const follow = ref(false)
const speedMul = ref(5)
const progress = ref(0) // 0..1000
const elapsedLabel = ref('00:00')
const totalLabel = ref('00:00')
const altLabel = ref('-')
const speedLabel = ref('-')

const mapEl = ref<HTMLElement>()
let map: MlMap | null = null
let droneMarker: Marker | null = null
let raf = 0
let lastFrame = 0

let pts: ReplayPoint[] = []
let coords: [number, number][] = []
let tStart = 0
let total = 0
let maxAlt = 0
let layersReady = false
let dataReady = false
let posDirty = false
let tCur = 0

const EMPTY_FC = { type: 'FeatureCollection', features: [] } as GeoJSON.FeatureCollection

async function load() {
  try {
    const flightId = route.query.flight ? Number(route.query.flight) : null
    const sn = typeof route.query.sn === 'string' ? route.query.sn : null
    let raw: ReplayPoint[] = []
    if (flightId != null && !Number.isNaN(flightId)) {
      const detail = await flightDetail(flightId)
      title.value = `架次 #${flightId} · ${detail.droneSn} · 3D 轨迹回放`
      const telemetry = await flightTelemetry(flightId)
      raw = telemetry
        .filter((p) => p.lat != null && p.lng != null)
        .map((p) => ({ lng: p.lng!, lat: p.lat!, ts: p.ts, alt: p.elevation ?? p.alt ?? 0, speed: p.speed }))
    } else if (sn) {
      title.value = `${sn} · 3D 轨迹回放`
      const track = await getTrack(sn)
      // 设备 Redis 轨迹的 alt 是椭球高，减首点归一为相对高度
      const base = track.length ? track[0].alt ?? 0 : 0
      raw = track.map((p) => ({ lng: p.lng, lat: p.lat, ts: p.ts, alt: (p.alt ?? 0) - base, speed: null }))
    }
    if (raw.length && raw[0].alt !== 0) {
      const base = raw[0].alt
      raw = raw.map((p) => ({ ...p, alt: p.alt - base }))
    }
    pts = raw.sort((a, b) => a.ts - b.ts)
    if (pts.length < 2) {
      notEnough.value = true
      return
    }
    coords = pts.map((p) => [p.lng, p.lat] as [number, number])
    tStart = pts[0].ts
    total = pts[pts.length - 1].ts - tStart
    if (total <= 0) {
      notEnough.value = true
      return
    }
    maxAlt = Math.max(...pts.map((p) => p.alt))
    totalLabel.value = fmtDur(total)
    dataReady = true
    tryRebuild()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '轨迹加载失败'
  } finally {
    loading.value = false
  }
}

function initMap() {
  map = new MlMap({
    container: mapEl.value!,
    style: tdtStyle(import.meta.env.VITE_TDT_TK),
    center: [121.44, 31.02],
    zoom: 13,
    pitch: 60,
    bearing: -20,
    customAttribution: '© 天地图'
  })
  map.on('load', addLayers)
  map.on('dragstart', () => {
    if (follow.value) follow.value = false
  })
}

function addLayers() {
  if (!map) return
  map.addSource('ground-line', { type: 'geojson', data: EMPTY_FC })
  map.addSource('flown-line', { type: 'geojson', data: EMPTY_FC })
  map.addSource('curtain', { type: 'geojson', data: EMPTY_FC })
  map.addSource('cur-column', { type: 'geojson', data: EMPTY_FC })
  map.addLayer({
    id: 'ground-line',
    type: 'line',
    source: 'ground-line',
    paint: { 'line-color': '#38bdf8', 'line-width': 2, 'line-opacity': 0.35, 'line-dasharray': [2, 2] }
  } as any)
  map.addLayer({
    id: 'curtain',
    type: 'fill-extrusion',
    source: 'curtain',
    paint: {
      'fill-extrusion-height': ['get', 'h'],
      'fill-extrusion-base': 0,
      'fill-extrusion-color': altColorExpr(),
      'fill-extrusion-opacity': 0.55
    }
  } as any)
  map.addLayer({
    id: 'flown-line',
    type: 'line',
    source: 'flown-line',
    paint: { 'line-color': '#22d3ee', 'line-width': 4, 'line-opacity': 0.95 }
  } as any)
  map.addLayer({
    id: 'cur-column',
    type: 'fill-extrusion',
    source: 'cur-column',
    paint: {
      'fill-extrusion-height': ['get', 'h'],
      'fill-extrusion-base': 0,
      'fill-extrusion-color': '#f43f5e',
      'fill-extrusion-opacity': 0.9
    }
  } as any)
  layersReady = true
  tryRebuild()
}

function altColorExpr() {
  const m = Math.max(maxAlt, 10)
  return [
    'interpolate',
    ['linear'],
    ['get', 'h'],
    0,
    '#0ea5e9',
    Number((m * 0.35).toFixed(1)),
    '#22d3ee',
    Number((m * 0.7).toFixed(1)),
    '#a3e635',
    m,
    '#f59e0b'
  ]
}

function tryRebuild() {
  if (!map || !layersReady || !dataReady) return
  const src = (id: string) => map!.getSource(id) as GeoJSONSource

  // 地面投影线（虚线暗色）
  src('ground-line').setData({
    type: 'Feature',
    properties: {},
    geometry: { type: 'LineString', coordinates: coords }
  })

  // 高度帘幕：每段一个零面积多边形竖直拉伸成墙，高度 = 段内最大相对高度
  const step = Math.max(1, Math.ceil((pts.length - 1) / 3000))
  const segs: GeoJSON.Feature[] = []
  for (let i = 0; i < pts.length - 1; i += step) {
    const j = Math.min(i + step, pts.length - 1)
    if (coords[i][0] === coords[j][0] && coords[i][1] === coords[j][1]) continue
    segs.push({
      type: 'Feature',
      properties: { h: Math.max(pts[i].alt, pts[j].alt) },
      geometry: { type: 'Polygon', coordinates: [[coords[i], coords[j], coords[j], coords[i], coords[i]]] }
    })
  }
  src('curtain').setData({ type: 'FeatureCollection', features: segs })
  map.setPaintProperty('curtain', 'fill-extrusion-color', altColorExpr())

  // 起飞/降落标记（起绿落红，同 2D 版约定）
  mkEndMarker(coords[0], '#22c55e', '起飞')
  const last = coords[coords.length - 1]
  if (haversine(coords[0], last) > 5) mkEndMarker(last, '#f87171', '降落')

  // 无人机图标
  droneMarker = new Marker({ element: mkDroneEl() }).setLngLat(coords[0]).addTo(map)

  fitAll()
  seek(0)
}

function mkEndMarker(pos: [number, number], color: string, label: string) {
  const el = document.createElement('div')
  el.className = 'end-marker'
  el.innerHTML = `<span class="end-dot" style="background:${color}"></span>${label}`
  new Marker({ element: el, anchor: 'center' }).setLngLat(pos).addTo(map!)
}

function mkDroneEl() {
  const el = document.createElement('div')
  el.className = 'drone-marker'
  el.innerHTML =
    '<svg width="26" height="26" viewBox="0 0 26 26"><circle cx="13" cy="13" r="4" fill="#f43f5e"/><circle cx="13" cy="13" r="10" fill="none" stroke="#f43f5e" stroke-opacity="0.55" stroke-width="2"/></svg>'
  return el
}

function fitAll() {
  if (!map || !coords.length) return
  let minLon = Infinity, minLat = Infinity, maxLon = -Infinity, maxLat = -Infinity
  for (const [lon, lat] of coords) {
    minLon = Math.min(minLon, lon); maxLon = Math.max(maxLon, lon)
    minLat = Math.min(minLat, lat); maxLat = Math.max(maxLat, lat)
  }
  map.fitBounds(
    [[minLon, minLat], [maxLon, maxLat]],
    { padding: 90, duration: 0, pitch: 60, bearing: -20 }
  )
}

function seek(pct: number) {
  tCur = (pct / 1000) * total
  posDirty = true
}

function onSlider() {
  seek(progress.value)
}

function togglePlay() {
  if (pts.length < 2) return
  if (!playing.value && tCur >= total) tCur = 0
  playing.value = !playing.value
}

function tick(now: number) {
  if (playing.value) {
    const dt = Math.min(now - lastFrame, 200)
    tCur = Math.min(tCur + dt * speedMul.value, total)
    if (tCur >= total) playing.value = false
  }
  lastFrame = now
  if (playing.value || posDirty) updatePosition()
  raf = requestAnimationFrame(tick)
}

/** 二分查找 ts <= tStart + t 的最后一个点下标 */
function idxAt(t: number) {
  const target = tStart + t
  let lo = 0
  let hi = pts.length - 1
  while (lo < hi) {
    const mid = (lo + hi + 1) >> 1
    if (pts[mid].ts <= target) lo = mid
    else hi = mid - 1
  }
  return lo
}

function updatePosition() {
  if (!map || pts.length < 2) return
  const i = idxAt(tCur)
  const p0 = pts[i]
  const p1 = pts[Math.min(i + 1, pts.length - 1)]
  const span = p1.ts - p0.ts
  const f = span > 0 ? Math.min(1, Math.max(0, (tStart + tCur - p0.ts) / span)) : 0
  const lng = p0.lng + (p1.lng - p0.lng) * f
  const lat = p0.lat + (p1.lat - p0.lat) * f
  const alt = p0.alt + (p1.alt - p0.alt) * f
  const speed =
    p0.speed != null && p1.speed != null ? p0.speed + (p1.speed - p0.speed) * f : p0.speed ?? p1.speed

  droneMarker?.setLngLat([lng, lat])

  // 当前高度柱（红色，从地面立到当前高度）
  const d = 0.00004
  ;(map.getSource('cur-column') as GeoJSONSource).setData({
    type: 'FeatureCollection',
    features: [
      {
        type: 'Feature',
        properties: { h: Math.max(alt, 0.5) },
        geometry: {
          type: 'Polygon',
          coordinates: [[[lng - d, lat - d], [lng + d, lat - d], [lng + d, lat + d], [lng - d, lat + d], [lng - d, lat - d]]]
        }
      }
    ]
  })

  // 已飞轨迹高亮
  const flown = coords.slice(0, i + 1)
  flown.push([lng, lat])
  ;(map.getSource('flown-line') as GeoJSONSource).setData({
    type: 'Feature',
    properties: {},
    geometry: { type: 'LineString', coordinates: flown }
  })

  elapsedLabel.value = fmtDur(tCur)
  progress.value = Math.round((tCur / total) * 1000)
  altLabel.value = `${alt.toFixed(1)} m`
  speedLabel.value = speed != null ? `${speed.toFixed(1)} m/s` : '-'
  if (follow.value) map.setCenter([lng, lat])
  posDirty = false
}

function haversine(a: [number, number], b: [number, number]) {
  const rad = Math.PI / 180
  const dLat = (b[1] - a[1]) * rad
  const dLon = (b[0] - a[0]) * rad
  const s =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(a[1] * rad) * Math.cos(b[1] * rad) * Math.sin(dLon / 2) ** 2
  return 6371000 * 2 * Math.asin(Math.sqrt(s))
}

function fmtDur(ms: number) {
  const sec = Math.max(0, Math.round(ms / 1000))
  return `${String(Math.floor(sec / 60)).padStart(2, '0')}:${String(sec % 60).padStart(2, '0')}`
}

onMounted(() => {
  initMap()
  load()
  raf = requestAnimationFrame(tick)
})

onBeforeUnmount(() => {
  cancelAnimationFrame(raf)
  playing.value = false
  map?.remove()
  map = null
})
</script>

<template>
  <div class="page">
    <AppHeader :title="title" />
    <main class="stage">
      <div ref="mapEl" class="map3d"></div>
      <div v-if="error" class="overlay">{{ error }}</div>
      <div v-else-if="loading" class="overlay">轨迹加载中…</div>
      <div v-else-if="notEnough" class="overlay">轨迹点不足，无法回放</div>
      <div v-else class="ctrl panel">
        <button class="play-btn" @click="togglePlay">{{ playing ? '⏸' : '▶' }}</button>
        <input class="seek" type="range" min="0" max="1000" v-model.number="progress" @input="onSlider" />
        <span class="time">{{ elapsedLabel }} / {{ totalLabel }}</span>
        <select v-model.number="speedMul" class="speed">
          <option :value="1">1x</option>
          <option :value="2">2x</option>
          <option :value="5">5x</option>
          <option :value="10">10x</option>
          <option :value="30">30x</option>
          <option :value="60">60x</option>
        </select>
        <span class="metric">高度 <b>{{ altLabel }}</b></span>
        <span class="metric">速度 <b>{{ speedLabel }}</b></span>
        <label class="follow"><input v-model="follow" type="checkbox" />跟随</label>
        <button class="reset" @click="fitAll">重置视角</button>
      </div>
    </main>
  </div>
</template>

<style scoped>
.stage {
  flex: 1;
  position: relative;
  overflow: hidden;
}
.map3d {
  height: 100%;
  width: 100%;
}
.maplibregl-canvas {
  outline: none;
}
.overlay {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--muted);
  background: rgba(11, 18, 32, 0.6);
  pointer-events: none;
}
.ctrl {
  position: absolute;
  bottom: 14px;
  left: 50%;
  transform: translateX(-50%);
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 14px;
  background: rgba(17, 26, 46, 0.92);
  max-width: calc(100% - 28px);
}
.play-btn {
  background: none;
  border: 1px solid var(--border);
  border-radius: 4px;
  color: var(--accent);
  cursor: pointer;
  font-size: 13px;
  width: 30px;
  height: 26px;
  flex-shrink: 0;
}
.seek {
  width: 260px;
  accent-color: var(--accent);
}
.time,
.metric {
  color: var(--muted);
  font-size: 12px;
  white-space: nowrap;
}
.metric b {
  color: var(--text);
  font-weight: 600;
}
.speed {
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: 4px;
  color: var(--text);
  font-size: 12px;
  padding: 2px 4px;
}
.follow {
  color: var(--muted);
  font-size: 12px;
  display: flex;
  align-items: center;
  gap: 4px;
  white-space: nowrap;
  cursor: pointer;
}
.reset {
  background: none;
  border: 1px solid var(--border);
  border-radius: 4px;
  color: var(--text);
  cursor: pointer;
  font-size: 12px;
  padding: 3px 8px;
  white-space: nowrap;
}
:deep(.end-marker) {
  display: flex;
  align-items: center;
  gap: 4px;
  color: #e5ecf5;
  font-size: 11px;
  white-space: nowrap;
  text-shadow: 0 0 4px rgba(0, 0, 0, 0.9);
}
:deep(.end-dot) {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}
:deep(.drone-marker) {
  filter: drop-shadow(0 0 4px rgba(244, 63, 94, 0.8));
}
</style>

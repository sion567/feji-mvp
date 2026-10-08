<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import L from 'leaflet'
import { tdtBaseLayers } from '../lib/tdt'
import type { DeviceEntry, TrackPoint } from '../api/deviceApi'
import type { MapElementView } from '../api/mapApi'
import type { PlotView } from '../api/plotApi'

const props = defineProps<{
  devices: DeviceEntry[]
  osdBySn: Record<string, any>
  selectedSn: string | null
  trackPoints?: TrackPoint[]
  elements?: MapElementView[]
  plots?: PlotView[]
  drawMode?: 'POINT' | 'LINE' | 'POLYGON' | null
  flytoMode?: boolean
}>()

const emit = defineEmits<{
  (e: 'select', sn: string): void
  (e: 'draw-finish', coordinates: [number, number][]): void
  (e: 'map-click', lat: number, lng: number): void
}>()

const mapEl = ref<HTMLElement>()
let map: L.Map | null = null
let didFit = false
const markers = new Map<string, L.CircleMarker>()
let elementLayer: L.LayerGroup | null = null
let plotLayer: L.LayerGroup | null = null
let draftLayer: L.LayerGroup | null = null
let draftPoints: [number, number][] = []

onMounted(() => {
  map = L.map(mapEl.value!, { center: [31.02, 121.44], zoom: 13 }).addControl(
    L.control.attribution({ prefix: false })
  )
  const layers = tdtBaseLayers(import.meta.env.VITE_TDT_TK)
  layers['影像'].addTo(map)
  L.control.layers({ 影像: layers['影像'], 矢量: layers['矢量'] }, undefined, { position: 'topright' }).addTo(map)
  elementLayer = L.layerGroup().addTo(map)
  plotLayer = L.layerGroup().addTo(map)
  draftLayer = L.layerGroup().addTo(map)
  map.on('click', onMapClick)
  syncMarkers()
  drawElements()
  drawPlots()
})

function fillColor(d: DeviceEntry) {
  if (d.kind === 'dock') return '#3b82f6'
  if (d.kind === 'rc') return '#a78bfa'
  return '#f59e0b'
}

function onMapClick(event: L.LeafletMouseEvent) {
  const { lat, lng } = event.latlng
  if (props.drawMode) {
    if (props.drawMode === 'POINT') {
      emit('draw-finish', [[lng, lat]])
      return
    }
    draftPoints.push([lng, lat])
    draftLayer?.clearLayers()
    if (draftPoints.length > 1) {
      L.polyline(draftPoints, { color: '#fbbf24', dashArray: '6 4' }).addTo(draftLayer!)
    }
    L.circleMarker([lat, lng], { radius: 4, color: '#fbbf24', fillOpacity: 1 }).addTo(draftLayer!)
    return
  }
  if (props.flytoMode) {
    emit('map-click', lat, lng)
  }
}

/** 由父组件在折线/多边形采点完成后调用 */
function finishDraw() {
  const coords = [...draftPoints]
  draftPoints = []
  draftLayer?.clearLayers()
  if (coords.length) {
    emit('draw-finish', coords)
  }
}

function cancelDraw() {
  draftPoints = []
  draftLayer?.clearLayers()
}

defineExpose({ finishDraw, cancelDraw })

function syncMarkers() {
  if (!map) return
  for (const d of props.devices) {
    const osd = props.osdBySn[d.sn]
    const lat = osd?.latitude
    const lng = osd?.longitude
    if (typeof lat !== 'number' || typeof lng !== 'number') continue
    let marker = markers.get(d.sn)
    if (!marker) {
      marker = L.circleMarker([lat, lng], { radius: 8, weight: 2 })
      marker.on('click', () => emit('select', d.sn))
      marker.bindTooltip(`${d.deviceName || d.modelName || d.sn}（${d.sn}）`)
      marker.addTo(map)
      markers.set(d.sn, marker)
      if (!didFit) {
        didFit = true
        map.setView([lat, lng], 14)
      }
    } else {
      marker.setLatLng([lat, lng])
    }
    marker.setStyle({
      color: d.online ? '#22c55e' : '#6b7280',
      fillColor: fillColor(d),
      fillOpacity: props.selectedSn === d.sn ? 1 : 0.75
    })
    if (props.selectedSn === d.sn) marker.bringToFront()
  }
  for (const [sn, marker] of markers) {
    if (!props.devices.some((d) => d.sn === sn)) {
      marker.remove()
      markers.delete(sn)
    }
  }
}

// 轨迹回放
let trackLine: L.Polyline | null = null

watch(
  () => props.trackPoints,
  (points) => {
    if (!map) return
    if (trackLine) {
      trackLine.remove()
      trackLine = null
    }
    if (points && points.length > 1) {
      trackLine = L.polyline(
        points.map((p) => [p.lat, p.lng] as [number, number]),
        { color: '#38bdf8', weight: 3, opacity: 0.85 }
      ).addTo(map)
      map.fitBounds(trackLine.getBounds(), { padding: [30, 30] })
    }
  }
)

// 地图标绘元素
function drawElements() {
  if (!elementLayer) return
  elementLayer.clearLayers()
  for (const el of props.elements || []) {
    const coords = (el.coordinates || [])
      .filter((p) => Array.isArray(p) && p.length >= 2)
      .map((p) => [p[1], p[0]] as [number, number])
    if (!coords.length) continue
    const style = { color: el.color || '#f59e0b', weight: 2 }
    if (el.elementType === 'POINT') {
      L.circleMarker(coords[0], { ...style, radius: 7, fillOpacity: 0.9 })
        .bindTooltip(`标注：${el.name}`)
        .addTo(elementLayer)
    } else if (el.elementType === 'LINE' && coords.length > 1) {
      L.polyline(coords, style).bindTooltip(`标注：${el.name}`).addTo(elementLayer)
    } else if (el.elementType === 'POLYGON' && coords.length > 2) {
      L.polygon(coords, { ...style, fillOpacity: 0.15 })
        .bindTooltip(`标注：${el.name}`)
        .addTo(elementLayer)
    }
  }
}

watch(
  () => [props.devices, props.osdBySn, props.selectedSn],
  () => syncMarkers(),
  { deep: true }
)

watch(() => props.elements, () => drawElements(), { deep: true })

// 地块（多边形）
function drawPlots() {
  if (!plotLayer) return
  plotLayer.clearLayers()
  for (const plot of props.plots || []) {
    const coords = (plot.coordinates || [])
      .filter((p) => Array.isArray(p) && p.length >= 2)
      .map((p) => [p[1], p[0]] as [number, number])
    if (coords.length < 3) continue
    L.polygon(coords, { color: plot.color || '#22c55e', weight: 2, fillOpacity: 0.12 })
      .bindTooltip(
        `地块：${plot.name}` +
        (plot.code ? `（${plot.code}）` : '') +
        (plot.areaMu ? `<br/>面积 ${plot.areaMu} 亩` : '')
      )
      .addTo(plotLayer)
  }
}

watch(() => props.plots, () => drawPlots(), { deep: true })

watch(
  () => props.drawMode,
  (mode) => {
    if (map) {
      map.getContainer().style.cursor = mode ? 'crosshair' : ''
    }
    if (!mode) cancelDraw()
  }
)

watch(
  () => props.flytoMode,
  (mode) => {
    if (map) {
      map.getContainer().style.cursor = mode ? 'crosshair' : ''
    }
  }
)

onBeforeUnmount(() => {
  map?.remove()
  map = null
})
</script>

<template>
  <div ref="mapEl" class="device-map"></div>
</template>

<style scoped>
.device-map {
  height: 100%;
  width: 100%;
}
</style>

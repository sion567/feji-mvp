<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import L from 'leaflet'
import { tdtBaseLayers } from '../lib/tdt'
import type { PlotView } from '../api/plotApi'

const props = defineProps<{
  plots: PlotView[]
  selectedId?: number | null
  drawing?: boolean
}>()

const emit = defineEmits<{
  (e: 'draw-finish', coordinates: [number, number][]): void
  (e: 'select', id: number): void
}>()

const mapEl = ref<HTMLElement>()
let map: L.Map | null = null
let plotLayer: L.LayerGroup | null = null
let draftLayer: L.LayerGroup | null = null
let draftPoints: [number, number][] = []

onMounted(() => {
  map = L.map(mapEl.value!, { center: [31.02, 121.44], zoom: 14 })
  const layers = tdtBaseLayers(import.meta.env.VITE_TDT_TK)
  layers['影像'].addTo(map)
  L.control.layers({ 影像: layers['影像'], 矢量: layers['矢量'] }, undefined, { position: 'topright' }).addTo(map)
  plotLayer = L.layerGroup().addTo(map)
  draftLayer = L.layerGroup().addTo(map)
  map.on('click', onMapClick)
  drawPlots()
})

function onMapClick(event: L.LeafletMouseEvent) {
  if (!props.drawing) return
  const { lat, lng } = event.latlng
  draftPoints.push([lng, lat])
  draftLayer?.clearLayers()
  if (draftPoints.length > 1) {
    L.polyline(draftPoints.map((p) => [p[1], p[0]] as [number, number]), {
      color: '#fbbf24', dashArray: '6 4'
    }).addTo(draftLayer!)
  }
  L.circleMarker([lat, lng], { radius: 4, color: '#fbbf24', fillOpacity: 1 }).addTo(draftLayer!)
}

/** 结束绘制：返回采到的边界点（少于 3 点返回 null） */
function finishDraw(): [number, number][] | null {
  const coords = [...draftPoints]
  draftPoints = []
  draftLayer?.clearLayers()
  return coords.length >= 3 ? coords : null
}

function cancelDraw() {
  draftPoints = []
  draftLayer?.clearLayers()
}

defineExpose({ finishDraw, cancelDraw })

function drawPlots() {
  if (!plotLayer) return
  plotLayer.clearLayers()
  for (const plot of props.plots || []) {
    const coords = (plot.coordinates || [])
      .filter((p) => Array.isArray(p) && p.length >= 2)
      .map((p) => [p[1], p[0]] as [number, number])
    if (coords.length < 3) continue
    const color = plot.color || '#22c55e'
    const polygon = L.polygon(coords, { color, weight: 2, fillOpacity: props.selectedId === plot.id ? 0.35 : 0.15 })
    polygon.bindTooltip(
      `地块：${plot.name}` +
      (plot.code ? `（${plot.code}）` : '') +
      (plot.areaMu ? `<br/>面积 ${plot.areaMu} 亩` : '') +
      (plot.crop ? `<br/>${plot.crop}` : '')
    )
    polygon.on('click', () => emit('select', plot.id))
    polygon.addTo(plotLayer)
  }
}

watch(() => [props.plots, props.selectedId], () => drawPlots(), { deep: true })

watch(
  () => props.drawing,
  (drawing) => {
    if (map) {
      map.getContainer().style.cursor = drawing ? 'crosshair' : ''
    }
    if (!drawing) cancelDraw()
  }
)

onBeforeUnmount(() => {
  map?.remove()
  map = null
})
</script>

<template>
  <div ref="mapEl" class="plot-map"></div>
</template>

<style scoped>
.plot-map {
  height: 100%;
  width: 100%;
}
</style>

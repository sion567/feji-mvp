<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import L from 'leaflet'
import { tdtBaseLayers } from '../lib/tdt'
import type { WaylineDetail } from '../api/waylineApi'

const props = defineProps<{ wayline: WaylineDetail | null }>()

const mapEl = ref<HTMLElement>()
let map: L.Map | null = null
let layerGroup: L.LayerGroup | null = null

const WAYLINE_COLORS = ['#38bdf8', '#f59e0b', '#a78bfa', '#22c55e']

onMounted(() => {
  map = L.map(mapEl.value!, { center: [31.02, 121.44], zoom: 14 })
  const layers = tdtBaseLayers(import.meta.env.VITE_TDT_TK)
  layers['影像'].addTo(map)
  L.control.layers({ 影像: layers['影像'], 矢量: layers['矢量'] }, undefined, { position: 'topright' }).addTo(map)
  layerGroup = L.layerGroup().addTo(map)
  draw()
})

function draw() {
  if (!map || !layerGroup) return
  layerGroup.clearLayers()
  const wayline = props.wayline
  if (!wayline?.waylines?.length) return

  const bounds: L.LatLngExpression[] = []
  wayline.waylines.forEach((line, lineIdx) => {
    const color = WAYLINE_COLORS[lineIdx % WAYLINE_COLORS.length]
    const points: [number, number][] = []
    for (const wp of line.waypoints || []) {
      if (typeof wp.latitude !== 'number' || typeof wp.longitude !== 'number') continue
      points.push([wp.latitude, wp.longitude])
      bounds.push([wp.latitude, wp.longitude])
      const actions = (wp.actionGroups || []).reduce((n, g) => n + (g.actions?.length || 0), 0)
      L.circleMarker([wp.latitude, wp.longitude], { radius: 6, color, fillColor: color, fillOpacity: 0.9, weight: 2 })
        .bindTooltip(
          `航线${line.waylineId ?? lineIdx} · 航点${wp.index ?? ''}` +
          (actions ? `（${actions} 个动作）` : '') +
          (wp.executeHeight != null ? `<br/>执行高度 ${wp.executeHeight}m` : '')
        )
        .addTo(layerGroup!)
    }
    if (points.length > 1) {
      L.polyline(points, { color, weight: 3, opacity: 0.85 }).addTo(layerGroup!)
    }
  })
  if (bounds.length) {
    map.fitBounds(L.latLngBounds(bounds), { padding: [30, 30] })
  }
}

watch(() => props.wayline, () => draw(), { deep: true })

onBeforeUnmount(() => {
  map?.remove()
  map = null
})
</script>

<template>
  <div ref="mapEl" class="wayline-map"></div>
</template>

<style scoped>
.wayline-map {
  height: 100%;
  width: 100%;
}
</style>

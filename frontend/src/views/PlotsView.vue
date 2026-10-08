<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import {
  listPlots, createPlot, updatePlot, deletePlot,
  type PlotView, type PlotSaveRequest
} from '../api/plotApi'
import { canOperate } from '../api/auth'
import { useWs } from '../ws/wsClient'
import AppHeader from '../components/AppHeader.vue'
import PlotMap from '../components/PlotMap.vue'

const plots = ref<PlotView[]>([])
const selectedId = ref<number | null>(null)
const drawing = ref(false)
const editing = ref(false) // true=修改已有地块信息
const error = ref('')
const mapComp = ref<InstanceType<typeof PlotMap>>()
const { subscribe } = useWs()

const form = ref({
  name: '',
  code: '',
  category: '农田',
  crop: '',
  description: '',
  color: '#22c55e'
})

const categories = ['农田', '园区', '作业区', '禁飞区', '其他']

const selected = computed(() => plots.value.find((p) => p.id === selectedId.value) ?? null)
const draftCoordinates = ref<[number, number][] | null>(null)

async function refresh() {
  try {
    plots.value = (await listPlots()).map((p) => ({
      ...p,
      coordinates: typeof p.coordinates === 'string' ? JSON.parse(p.coordinates) : p.coordinates
    }))
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载失败'
  }
}

function startCreate() {
  editing.value = false
  drawing.value = true
  selectedId.value = null
  draftCoordinates.value = null
  form.value = { name: '', code: '', category: '农田', crop: '', description: '', color: '#22c55e' }
}

async function onFinishDraw() {
  const coords = mapComp.value?.finishDraw()
  if (!coords) {
    error.value = '至少需要 3 个边界点'
    return
  }
  draftCoordinates.value = coords
  drawing.value = false
  // 面积预览用前端近似：交给后端计算，先提示
  error.value = ''
}

function onCancelDraw() {
  drawing.value = false
  mapComp.value?.cancelDraw()
  draftCoordinates.value = null
}

function onSelectPlot(id: number) {
  selectedId.value = id
  drawing.value = false
  draftCoordinates.value = null
  const p = plots.value.find((x) => x.id === id)
  if (p) {
    editing.value = true
    form.value = {
      name: p.name,
      code: p.code || '',
      category: p.category || '农田',
      crop: p.crop || '',
      description: p.description || '',
      color: p.color || '#22c55e'
    }
  }
}

async function onSave() {
  error.value = ''
  const coordinates = draftCoordinates.value
    ? draftCoordinates.value
    : selected.value?.coordinates
  if (!coordinates || coordinates.length < 3) {
    error.value = '地块边界未绘制（至少 3 个点）'
    return
  }
  const body: PlotSaveRequest = {
    name: form.value.name,
    code: form.value.code || undefined,
    category: form.value.category,
    crop: form.value.crop || undefined,
    description: form.value.description || undefined,
    color: form.value.color,
    coordinates
  }
  try {
    if (editing.value && selectedId.value) {
      await updatePlot(selectedId.value, body)
    } else {
      await createPlot(body)
    }
    drawing.value = false
    draftCoordinates.value = null
    await refresh()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '保存失败'
  }
}

async function onDelete(p: PlotView) {
  if (!confirm(`确认删除地块「${p.name}」？`)) return
  try {
    await deletePlot(p.id)
    if (selectedId.value === p.id) {
      selectedId.value = null
      editing.value = false
    }
    await refresh()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '删除失败'
  }
}

let unsub: (() => void) | undefined
onMounted(async () => {
  await refresh()
  unsub = subscribe((type) => {
    if (type === 'plot_update') refresh()
  })
})
onUnmounted?.()
function onUnmounted() {
  unsub?.()
}
</script>

<template>
  <div class="page">
    <AppHeader title="地块管理" />
    <main class="layout">
      <aside class="list-panel panel">
        <div class="toolbar">
          <button v-if="canOperate()" class="primary" @click="startCreate">新建地块</button>
        </div>
        <p v-if="error" class="error">{{ error }}</p>
        <ul class="plot-list">
          <li
            v-for="p in plots"
            :key="p.id"
            :class="{ selected: selectedId === p.id }"
            @click="onSelectPlot(p.id)"
          >
            <span class="el-dot" :style="{ background: p.color || '#22c55e' }"></span>
            <div class="info">
              <div class="name">{{ p.name }}<span v-if="p.code" class="code">（{{ p.code }}）</span></div>
              <div class="sub">
                {{ p.category || '未分类' }}
                <template v-if="p.areaMu"> · {{ p.areaMu }} 亩</template>
                <template v-if="p.crop"> · {{ p.crop }}</template>
              </div>
            </div>
            <button v-if="canOperate()" class="danger" @click.stop="onDelete(p)">删除</button>
          </li>
        </ul>
        <p v-if="plots.length === 0" class="empty">暂无地块，点击「新建地块」在地图上绘制边界。</p>
      </aside>
      <main class="map-panel panel">
        <div class="map-title">
          <template v-if="drawing">绘制模式：在地图上依次点击边界拐点（至少 3 个），完成后保存</template>
          <template v-else-if="selected">地块：{{ selected.name }}</template>
          <template v-else>点击左侧地块查看，或新建地块绘制边界</template>
        </div>
        <div class="map-wrap">
          <PlotMap
            ref="mapComp"
            :plots="plots"
            :selected-id="selectedId"
            :drawing="drawing"
            @draw-finish="onFinishDraw"
            @select="onSelectPlot"
          />
        </div>
        <form v-if="canOperate() && (draftCoordinates || selected)" class="save-form" @submit.prevent="onSave">
          <input v-model="form.name" placeholder="地块名称 *" required />
          <input v-model="form.code" placeholder="编号" />
          <select v-model="form.category">
            <option v-for="c in categories" :key="c" :value="c">{{ c }}</option>
          </select>
          <input v-model="form.crop" placeholder="种植内容/用途" />
          <input v-model="form.description" placeholder="备注" />
          <button type="submit" class="primary">{{ editing && !draftCoordinates ? '更新信息' : '保存地块' }}</button>
          <button v-if="drawing" type="button" @click="onCancelDraw">放弃绘制</button>
        </form>
      </main>
    </main>
  </div>
</template>

<style scoped>
.layout {
  display: grid;
  grid-template-columns: 320px 1fr;
  gap: 10px;
  padding: 10px;
  height: calc(100% - 48px);
}
.toolbar {
  padding: 10px 12px 0;
}
button.primary {
  background: var(--accent);
  border: none;
  border-radius: 6px;
  color: #fff;
  cursor: pointer;
  padding: 8px 14px;
}
button.danger {
  background: none;
  border: 1px solid rgba(220, 38, 38, 0.5);
  border-radius: 4px;
  color: #f87171;
  cursor: pointer;
  padding: 4px 10px;
  font-size: 12px;
}
.plot-list {
  list-style: none;
  margin: 0;
  padding: 8px;
  overflow-y: auto;
}
.plot-list li {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px;
  border-radius: 6px;
  cursor: pointer;
}
.plot-list li:hover {
  background: rgba(255, 255, 255, 0.04);
}
.plot-list li.selected {
  background: rgba(34, 197, 94, 0.15);
}
.info {
  flex: 1;
  min-width: 0;
}
.name {
  font-size: 13px;
}
.code {
  color: var(--muted);
  font-size: 11px;
}
.sub {
  color: var(--muted);
  font-size: 11px;
}
.map-panel {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  position: relative;
}
.map-title {
  padding: 10px 12px;
  color: var(--muted);
  font-size: 12px;
}
.map-wrap {
  flex: 1;
}
.save-form {
  position: absolute;
  bottom: 14px;
  left: 14px;
  right: 14px;
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  background: rgba(17, 26, 46, 0.92);
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 10px;
  z-index: 1000;
}
.save-form input, .save-form select {
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: 6px;
  color: var(--text);
  padding: 7px 9px;
  min-width: 110px;
  flex: 1;
}
.save-form button {
  border: none;
  border-radius: 6px;
  cursor: pointer;
  padding: 7px 14px;
}
.save-form .primary {
  background: var(--accent);
  color: #fff;
}
.save-form button[type='button'] {
  background: var(--border);
  color: var(--text);
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

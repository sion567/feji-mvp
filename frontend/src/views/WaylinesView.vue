<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import {
  listWaylines, waylineDetail, deleteWayline, uploadWayline,
  type WaylineFileView, type WaylineDetail
} from '../api/waylineApi'
import { canOperate } from '../api/auth'
import AppHeader from '../components/AppHeader.vue'
import WaylineMap from '../components/WaylineMap.vue'

const files = ref<WaylineFileView[]>([])
const selected = ref<WaylineFileView | null>(null)
const detail = ref<WaylineDetail | null>(null)
const uploading = ref(false)
const error = ref('')
const fileInput = ref<HTMLInputElement>()

async function refresh() {
  try {
    files.value = await listWaylines()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载失败'
  }
}

async function onUpload(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  uploading.value = true
  error.value = ''
  try {
    await uploadWayline(file)
    await refresh()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '上传失败'
  } finally {
    uploading.value = false
    input.value = ''
  }
}

async function onPreview(f: WaylineFileView) {
  if (selected.value?.id === f.id) {
    selected.value = null
    detail.value = null
    return
  }
  try {
    detail.value = await waylineDetail(f.id)
    selected.value = f
  } catch (e) {
    error.value = e instanceof Error ? e.message : '解析预览失败'
  }
}

async function onDelete(f: WaylineFileView) {
  if (!confirm(`确认删除航线「${f.name}」？`)) return
  try {
    await deleteWayline(f.id)
    if (selected.value?.id === f.id) {
      selected.value = null
      detail.value = null
    }
    await refresh()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '删除失败'
  }
}

const previewTitle = computed(() => {
  if (!detail.value) return ''
  return `航线预览 · ${detail.value.droneModelName || detail.value.name} · ${detail.value.waylines?.length || 0} 条航线`
})

onMounted(refresh)
</script>

<template>
  <div class="page">
    <AppHeader title="航线管理" />
    <main class="layout">
      <aside class="list-panel panel">
        <div class="toolbar">
          <button v-if="canOperate()" class="primary" :disabled="uploading" @click="fileInput?.click()">
            {{ uploading ? '上传中…' : '上传 KMZ 航线' }}
          </button>
          <input ref="fileInput" type="file" accept=".kmz" hidden @change="onUpload" />
        </div>
        <p v-if="error" class="error">{{ error }}</p>
        <ul class="file-list">
          <li
            v-for="f in files"
            :key="f.id"
            :class="{ selected: selected?.id === f.id }"
            @click="onPreview(f)"
          >
            <div class="info">
              <div class="name">{{ f.name }}</div>
              <div class="sub">
                {{ f.droneModelName || '未知机型' }} ·
                {{ f.waylineCount || 0 }} 条航线 / {{ f.waypointCount || 0 }} 航点
              </div>
              <div class="sub">{{ f.creator || '-' }} · {{ new Date(f.createdAt).toLocaleString() }}</div>
            </div>
            <button v-if="canOperate()" class="danger" @click.stop="onDelete(f)">删除</button>
          </li>
        </ul>
        <p v-if="files.length === 0" class="empty">暂无航线，上传 Pilot 导出的 KMZ 文件。</p>
      </aside>
      <main class="map-panel panel">
        <template v-if="detail">
          <div class="map-title">{{ previewTitle }}</div>
          <WaylineMap :wayline="detail" />
        </template>
        <p v-else class="empty center">点击左侧航线进行地图预览。</p>
      </main>
    </main>
  </div>
</template>

<style scoped>
.layout {
  display: grid;
  grid-template-columns: 340px 1fr;
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
.file-list {
  list-style: none;
  margin: 0;
  padding: 8px;
  overflow-y: auto;
}
.file-list li {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px;
  border-radius: 6px;
  cursor: pointer;
}
.file-list li:hover {
  background: rgba(255, 255, 255, 0.04);
}
.file-list li.selected {
  background: rgba(59, 130, 246, 0.15);
}
.info {
  flex: 1;
  min-width: 0;
}
.name {
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.sub {
  color: var(--muted);
  font-size: 11px;
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
.map-panel > .wayline-map {
  flex: 1;
}
.empty.center {
  margin: auto;
}
.error {
  color: #f87171;
  font-size: 12px;
  padding: 4px 12px;
}
</style>

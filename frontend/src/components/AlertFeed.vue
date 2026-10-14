<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { listAlerts, type DeviceAlertView } from '../api/alertApi'
import { useWs } from '../ws/wsClient'

const MAX_ITEMS = 50

const alerts = ref<DeviceAlertView[]>([])
const collapsed = ref(false)
const { subscribe } = useWs()

const criticalCount = computed(() => alerts.value.filter((a) => a.level === 'CRITICAL').length)

let unsub: (() => void) | undefined

onMounted(async () => {
  try {
    alerts.value = await listAlerts({ limit: MAX_ITEMS })
  } catch {
    // 告警模块不可用时仅保留实时推送
  }
  unsub = subscribe((type, data) => {
    if (type !== 'device_alert') return
    // WS 推送无 id，用负数占位避免与历史记录 key 冲突
    alerts.value = [{ ...(data as Omit<DeviceAlertView, 'id'>), id: -Date.now() } as DeviceAlertView, ...alerts.value].slice(0, MAX_ITEMS)
  })
})

onUnmounted(() => unsub?.())

const LEVEL_META: Record<string, { label: string; color: string }> = {
  CRITICAL: { label: '严重', color: '#ef4444' },
  WARN: { label: '警告', color: '#f59e0b' },
  NOTICE: { label: '提示', color: '#60a5fa' }
}

const CATEGORY_LABEL: Record<string, string> = {
  HMS: '健康',
  BATTERY: '电量',
  RTK: '定位'
}

function meta(level: string) {
  return LEVEL_META[level] ?? { label: level, color: '#94a3b8' }
}

function categoryLabel(category: string) {
  return CATEGORY_LABEL[category] ?? category
}

function timeText(occurredAt: number) {
  const diff = Date.now() - occurredAt
  if (diff < 60_000) return '刚刚'
  if (diff < 3600_000) return `${Math.floor(diff / 60_000)} 分钟前`
  if (diff < 24 * 3600_000) return `${Math.floor(diff / 3600_000)} 小时前`
  return new Date(occurredAt).toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
}
</script>

<template>
  <div class="alert-feed">
    <button class="feed-toggle" :class="{ urgent: criticalCount > 0 }" @click="collapsed = !collapsed">
      实时告警 {{ alerts.length }}<template v-if="criticalCount > 0">（{{ criticalCount }} 严重）</template>
      <span class="arrow">{{ collapsed ? '▾' : '▴' }}</span>
    </button>
    <ul v-if="!collapsed" class="feed-list">
      <li v-for="a in alerts" :key="a.id" :class="{ critical: a.level === 'CRITICAL' }">
        <span class="lv-dot" :style="{ background: meta(a.level).color }"></span>
        <div class="body">
          <div class="msg">
            <span class="lv" :style="{ color: meta(a.level).color }">{{ meta(a.level).label }}</span>
            <span class="cat">{{ categoryLabel(a.category) }}</span>
            {{ a.message }}
          </div>
          <div class="sub">{{ a.sn }} · {{ timeText(a.occurredAt) }}</div>
        </div>
      </li>
      <li v-if="alerts.length === 0" class="empty">暂无告警</li>
    </ul>
  </div>
</template>

<style scoped>
.alert-feed {
  position: absolute;
  top: 10px;
  right: 10px;
  z-index: 30;
  width: 320px;
  max-width: 40%;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  pointer-events: none;
}
.alert-feed > * {
  pointer-events: auto;
}
.feed-toggle {
  background: var(--panel);
  border: 1px solid var(--border);
  border-radius: 6px;
  color: var(--text);
  cursor: pointer;
  font-size: 12px;
  padding: 5px 10px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.25);
}
.feed-toggle.urgent {
  border-color: #ef4444;
  color: #ef4444;
}
.arrow {
  margin-left: 6px;
  color: var(--muted);
}
.feed-list {
  list-style: none;
  margin: 6px 0 0;
  padding: 4px;
  width: 100%;
  max-height: 260px;
  overflow-y: auto;
  background: var(--panel);
  border: 1px solid var(--border);
  border-radius: 6px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.25);
}
.feed-list li {
  display: flex;
  gap: 8px;
  padding: 6px;
  border-radius: 4px;
  align-items: flex-start;
}
.feed-list li.critical {
  background: rgba(239, 68, 68, 0.12);
}
.lv-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-top: 5px;
  flex-shrink: 0;
}
.body {
  flex: 1;
  min-width: 0;
}
.msg {
  font-size: 12px;
  line-height: 1.4;
  overflow-wrap: anywhere;
}
.lv {
  font-size: 11px;
  margin-right: 4px;
}
.cat {
  color: var(--muted);
  font-size: 11px;
  margin-right: 4px;
}
.sub {
  color: var(--muted);
  font-size: 11px;
  margin-top: 2px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.empty {
  color: var(--muted);
  font-size: 12px;
  justify-content: center;
}
</style>

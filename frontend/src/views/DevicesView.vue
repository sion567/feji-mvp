<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { listDevices, renameDevice, unbindDevice, type DeviceEntry } from '../api/deviceApi'
import { canOperate } from '../api/auth'
import { kindLabel } from '../lib/device'
import AppHeader from '../components/AppHeader.vue'

const devices = ref<DeviceEntry[]>([])
let timer: number | undefined

onMounted(async () => {
  await refresh()
  timer = window.setInterval(refresh, 5000)
})

onUnmounted(() => window.clearInterval(timer))

async function refresh() {
  try {
    devices.value = await listDevices()
  } catch {
    // 401 由 http 层统一处理
  }
}

async function onRename(d: DeviceEntry) {
  const name = prompt('新设备名称', d.deviceName || d.modelName || '')
  if (name == null || !name.trim()) return
  try {
    await renameDevice(d.sn, name.trim())
    await refresh()
  } catch (e) {
    alert(e instanceof Error ? e.message : '改名失败')
  }
}

async function onUnbind(d: DeviceEntry) {
  if (!confirm(`确认移除设备 ${d.sn}？（档案与轨迹将一并清除）`)) return
  try {
    await unbindDevice(d.sn)
    await refresh()
  } catch (e) {
    alert(e instanceof Error ? e.message : '移除失败')
  }
}

function displayNameOf(d: DeviceEntry) {
  return d.deviceName || d.modelName || d.model || d.sn
}

function fmtTime(ts: number) {
  return ts ? new Date(ts).toLocaleString() : '-'
}
</script>

<template>
  <div class="page">
    <AppHeader title="设备管理" />
    <main class="page-body">
      <table class="table">
        <thead>
          <tr>
            <th>SN</th>
            <th>名称</th>
            <th>型号</th>
            <th>类型</th>
            <th>在线</th>
            <th>父设备</th>
            <th>最近活动</th>
            <th v-if="canOperate()">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="d in devices" :key="d.sn">
            <td>{{ d.sn }}</td>
            <td>{{ displayNameOf(d) }}</td>
            <td>{{ d.modelName || d.model || '-' }}</td>
            <td>{{ kindLabel(d.kind) }}</td>
            <td>
              <span class="dot" :class="d.online ? 'on' : 'off'"></span>
              {{ d.online ? '在线' : '离线' }}
            </td>
            <td>{{ d.parentSn || '-' }}</td>
            <td>{{ fmtTime(d.lastSeen) }}</td>
            <td v-if="canOperate()">
              <button class="op" @click="onRename(d)">改名</button>
              <button class="op danger" @click="onUnbind(d)">移除</button>
            </td>
          </tr>
        </tbody>
      </table>
      <p v-if="devices.length === 0" class="empty">暂无设备。</p>
    </main>
  </div>
</template>

<style scoped>
.page-body {
  padding: 16px;
}
.table {
  width: 100%;
  border-collapse: collapse;
}
.table th,
.table td {
  text-align: left;
  padding: 10px 12px;
  border-bottom: 1px solid var(--border);
}
.table th {
  color: var(--muted);
  font-weight: normal;
  font-size: 12px;
}
.op {
  background: none;
  border: 1px solid var(--border);
  border-radius: 4px;
  color: var(--text);
  cursor: pointer;
  padding: 4px 10px;
  margin-right: 6px;
  font-size: 12px;
}
.op.danger {
  color: #f87171;
  border-color: rgba(220, 38, 38, 0.5);
}
</style>


<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import {
  listWaylines, createJob, listJobs, cancelJob,
  type JobView, type WaylineFileView
} from '../api/waylineApi'
import { listDevices, type DeviceEntry } from '../api/deviceApi'
import { updateJobReport } from '../api/flightApi'
import { canOperate } from '../api/auth'
import { useWs } from '../ws/wsClient'
import AppHeader from '../components/AppHeader.vue'

const jobs = ref<JobView[]>([])
const waylines = ref<WaylineFileView[]>([])
const devices = ref<DeviceEntry[]>([])
const error = ref('')
const { subscribe } = useWs()

// 创建表单
const showCreate = ref(false)
const form = ref({
  name: '',
  waylineFileId: 0,
  droneSn: '',
  taskType: 'IMMEDIATE' as 'IMMEDIATE' | 'TIMED' | 'RECURRING',
  executeDate: '',
  executeTime: '09:00'
})

const taskTypeLabel: Record<string, string> = {
  IMMEDIATE: '立即执行',
  TIMED: '定时执行',
  RECURRING: '每日巡检'
}
const statusLabel: Record<string, string> = {
  PENDING: '待执行',
  DISPATCHING: '指令下发中',
  RUNNING: '执行中',
  SUCCESS: '已完成',
  FAILED: '失败',
  CANCELED: '已取消'
}
const statusColor: Record<string, string> = {
  PENDING: 'var(--muted)',
  DISPATCHING: 'var(--amber)',
  RUNNING: 'var(--accent)',
  SUCCESS: 'var(--green)',
  FAILED: '#f87171',
  CANCELED: 'var(--muted)'
}

const waylineName = (id: number) => waylines.value.find((w) => w.id === id)?.name || `#${id}`

async function refresh() {
  try {
    jobs.value = await listJobs()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载失败'
  }
}

async function onDeviceSelect(event: Event) {
  form.value.droneSn = (event.target as HTMLSelectElement).value
}

async function submit() {
  error.value = ''
  const body: Parameters<typeof createJob>[0] = {
    name: form.value.name || undefined,
    waylineFileId: form.value.waylineFileId,
    droneSn: form.value.droneSn,
    taskType: form.value.taskType
  }
  if (form.value.taskType === 'TIMED') {
    if (!form.value.executeDate) {
      error.value = '请选择定时执行日期'
      return
    }
    body.executeTime = new Date(`${form.value.executeDate}T${form.value.executeTime || '09:00'}:00`).getTime()
  }
  if (form.value.taskType === 'RECURRING') {
    const [h, m] = (form.value.executeTime || '09:00').split(':').map(Number)
    body.executeTime = (h * 60 + m) * 60_000
  }
  try {
    const created = await createJob(body)
    if (created.complianceWarning && created.complianceMessage) {
      alert('合规提醒：' + created.complianceMessage)
    }
    showCreate.value = false
    await refresh()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '创建失败'
  }
}

const reportLabel: Record<string, string> = { NONE: '未报备', FILED: '已报备', APPROVED: '已批复' }

async function onReportChange(job: JobView, event: Event) {
  const status = (event.target as HTMLSelectElement).value as 'NONE' | 'FILED' | 'APPROVED'
  let reportNo: string | undefined
  if (status === 'APPROVED') {
    const input = prompt('请输入 UOM 批复号：')
    if (input == null) {
      await refresh()
      return
    }
    reportNo = input.trim() || undefined
  }
  try {
    await updateJobReport(job.id, status, reportNo)
    job.reportStatus = status
    job.reportNo = reportNo || null
  } catch (e) {
    error.value = e instanceof Error ? e.message : '报备登记失败'
    await refresh()
  }
}

async function onCancel(job: JobView) {
  if (!confirm(`确认取消任务「${job.name}」？`)) return
  try {
    await cancelJob(job.id)
    await refresh()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '取消失败'
  }
}

function fmtTime(ms: number | null) {
  return ms ? new Date(ms).toLocaleString() : '-'
}

let unsub: (() => void) | undefined
let timer: number | undefined

onMounted(async () => {
  await Promise.all([refresh(), loadDeps()])
  unsub = subscribe((type) => {
    if (type === 'job_update') refresh()
  })
  timer = window.setInterval(refresh, 15000)
})

onUnmounted(() => {
  unsub?.()
  window.clearInterval(timer)
})

async function loadDeps() {
  try {
    const [w, d] = await Promise.all([listWaylines(), listDevices()])
    waylines.value = w
    devices.value = d
    if (w.length && !form.value.waylineFileId) form.value.waylineFileId = w[0].id
  } catch {
    // 忽略
  }
}

const activeJobs = computed(() => jobs.value.filter((j) => !['SUCCESS', 'FAILED', 'CANCELED'].includes(j.status)))
</script>

<template>
  <div class="page">
    <AppHeader title="任务管理" />
    <main class="page-body">
      <div class="toolbar">
        <button v-if="canOperate()" class="primary" @click="showCreate = !showCreate">
          {{ showCreate ? '取消' : '新建任务' }}
        </button>
        <span class="hint">进行中 {{ activeJobs.length }} / 共 {{ jobs.length }}</span>
      </div>
      <p v-if="error" class="error">{{ error }}</p>

      <form v-if="showCreate" class="create-form panel" @submit.prevent="submit">
        <label>任务名称 <input v-model="form.name" placeholder="留空自动命名" /></label>
        <label>航线
          <select v-model.number="form.waylineFileId">
            <option v-for="w in waylines" :key="w.id" :value="w.id">{{ w.name }}</option>
          </select>
        </label>
        <label>执行设备
          <select :value="form.droneSn" @change="onDeviceSelect">
            <option value="" disabled>选择机场/无人机</option>
            <option v-for="d in devices" :key="d.sn" :value="d.sn">
              {{ d.deviceName || d.modelName || d.sn }}（{{ d.online ? '在线' : '离线' }}）
            </option>
          </select>
        </label>
        <label>类型
          <select v-model="form.taskType">
            <option value="IMMEDIATE">立即执行</option>
            <option value="TIMED">定时执行（单次）</option>
            <option value="RECURRING">每日巡检</option>
          </select>
        </label>
        <label v-if="form.taskType === 'TIMED'">日期 <input v-model="form.executeDate" type="date" /></label>
        <label v-if="form.taskType !== 'IMMEDIATE'">时刻 <input v-model="form.executeTime" type="time" /></label>
        <button type="submit" class="primary">创建并下发</button>
      </form>

      <table class="table">
        <thead>
          <tr>
            <th>ID</th><th>任务</th><th>航线</th><th>执行设备</th><th>类型</th>
            <th>计划时间</th><th>状态</th><th>进度</th><th>报备</th><th>最近消息</th><th v-if="canOperate()">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="j in jobs" :key="j.id">
            <td>{{ j.id }}</td>
            <td>{{ j.name }}</td>
            <td>{{ waylineName(j.waylineFileId) }}</td>
            <td>{{ j.droneSn }}</td>
            <td>{{ taskTypeLabel[j.taskType] || j.taskType }}</td>
            <td>{{ j.taskType === 'RECURRING' ? '每日' : fmtTime(j.executeTime) }}</td>
            <td :style="{ color: statusColor[j.status] }">{{ statusLabel[j.status] || j.status }}</td>
            <td>
              <div class="progress"><div class="bar" :style="{ width: j.progress + '%' }"></div></div>
              {{ j.progress }}%
            </td>
            <td>
              <select
                v-if="canOperate()"
                :value="j.reportStatus || 'NONE'"
                @change="onReportChange(j, $event)"
              >
                <option value="NONE">未报备</option>
                <option value="FILED">已报备</option>
                <option value="APPROVED">已批复</option>
              </select>
              <span v-else>{{ reportLabel[j.reportStatus || 'NONE'] }}</span>
              <div v-if="j.reportNo" class="report-no">{{ j.reportNo }}</div>
            </td>
            <td class="msg">{{ j.message || '-' }}</td>
            <td v-if="canOperate()">
              <button
                v-if="!['SUCCESS', 'FAILED', 'CANCELED'].includes(j.status)"
                class="op danger"
                @click="onCancel(j)"
              >取消</button>
            </td>
          </tr>
        </tbody>
      </table>
      <p v-if="jobs.length === 0" class="empty">暂无任务。</p>
    </main>
  </div>
</template>

<style scoped>
.page-body {
  padding: 16px;
  overflow-y: auto;
}
.toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}
.hint {
  color: var(--muted);
  font-size: 12px;
}
button.primary {
  background: var(--accent);
  border: none;
  border-radius: 6px;
  color: #fff;
  cursor: pointer;
  padding: 8px 14px;
}
button.op {
  background: none;
  border: 1px solid var(--border);
  border-radius: 4px;
  color: var(--text);
  cursor: pointer;
  padding: 4px 10px;
  font-size: 12px;
}
button.op.danger {
  color: #f87171;
  border-color: rgba(220, 38, 38, 0.5);
}
.create-form {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  padding: 12px;
  margin-bottom: 14px;
  align-items: flex-end;
}
.create-form label {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 12px;
  color: var(--muted);
}
.create-form input, .create-form select {
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: 6px;
  color: var(--text);
  padding: 7px 9px;
  min-width: 130px;
}
.table {
  width: 100%;
  border-collapse: collapse;
}
.table th, .table td {
  text-align: left;
  padding: 9px 10px;
  border-bottom: 1px solid var(--border);
  font-size: 13px;
}
.table th {
  color: var(--muted);
  font-weight: normal;
  font-size: 12px;
}
.progress {
  display: inline-block;
  width: 70px;
  height: 6px;
  background: var(--border);
  border-radius: 3px;
  margin-right: 6px;
  vertical-align: middle;
  overflow: hidden;
}
.bar {
  height: 100%;
  background: var(--accent);
}
.table select {
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: 4px;
  color: var(--text);
  padding: 3px 6px;
  font-size: 12px;
}
.report-no {
  color: var(--muted);
  font-size: 11px;
  max-width: 110px;
  overflow: hidden;
  text-overflow: ellipsis;
}
.msg {
  max-width: 220px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--muted);
  font-size: 12px;
}
.error {
  color: #f87171;
  font-size: 13px;
}
</style>

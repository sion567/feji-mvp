<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import {
  listUsers, createUser, updateUserRole, deleteUser,
  listWorkspaces, createWorkspace, listAuditLogs,
  type UserView, type WorkspaceView, type AuditLogView
} from '../api/systemApi'
import { isAdmin, type Role } from '../api/auth'
import AppHeader from '../components/AppHeader.vue'

const tab = ref<'users' | 'workspaces' | 'audit'>('users')
const error = ref('')

const users = ref<UserView[]>([])
const workspaces = ref<WorkspaceView[]>([])
const logs = ref<AuditLogView[]>([])

const showCreate = ref(false)
const form = ref({ username: '', password: '', displayName: '', role: 'OPERATOR' as Role, workspaceId: 1 })
const newWorkspaceName = ref('')

const admin = computed(() => isAdmin())

const roleLabel: Record<string, string> = { ADMIN: '管理员', OPERATOR: '操作员', OBSERVER: '观察者' }

async function refresh() {
  error.value = ''
  try {
    if (tab.value === 'users') {
      users.value = await listUsers()
      workspaces.value = await listWorkspaces()
    } else if (tab.value === 'workspaces') {
      workspaces.value = await listWorkspaces()
    } else {
      logs.value = await listAuditLogs()
    }
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载失败'
  }
}

function switchTab(t: 'users' | 'workspaces' | 'audit') {
  tab.value = t
  refresh()
}

async function submitCreate() {
  error.value = ''
  try {
    await createUser({
      username: form.value.username,
      password: form.value.password,
      displayName: form.value.displayName || undefined,
      role: form.value.role,
      workspaceId: form.value.workspaceId
    })
    showCreate.value = false
    form.value = { username: '', password: '', displayName: '', role: 'OPERATOR', workspaceId: 1 }
    await refresh()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '创建失败'
  }
}

async function onRoleChange(user: UserView, event: Event) {
  const role = (event.target as HTMLSelectElement).value as Role
  try {
    await updateUserRole(user.id, role)
    user.role = role
  } catch (e) {
    error.value = e instanceof Error ? e.message : '修改失败'
    await refresh()
  }
}

async function onDeleteUser(user: UserView) {
  if (!confirm(`确认删除用户 ${user.username}？`)) return
  try {
    await deleteUser(user.id)
    await refresh()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '删除失败'
  }
}

async function onCreateWorkspace() {
  if (!newWorkspaceName.value.trim()) return
  try {
    await createWorkspace(newWorkspaceName.value.trim())
    newWorkspaceName.value = ''
    await refresh()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '创建失败'
  }
}

function fmtTime(value: string | null) {
  return value ? new Date(value).toLocaleString() : '-'
}

onMounted(refresh)
</script>

<template>
  <div class="page">
    <AppHeader title="系统管理" />
    <main class="page-body">
      <div class="tabs">
        <button :class="{ active: tab === 'users' }" @click="switchTab('users')">用户管理</button>
        <button :class="{ active: tab === 'workspaces' }" @click="switchTab('workspaces')">工作空间</button>
        <button :class="{ active: tab === 'audit' }" @click="switchTab('audit')">审计日志</button>
      </div>

      <p v-if="!admin" class="empty">当前账号为非管理员，仅可查看。</p>
      <p v-if="error" class="error">{{ error }}</p>

      <!-- 用户管理 -->
      <template v-if="tab === 'users'">
        <div v-if="admin" class="toolbar">
          <button class="primary" @click="showCreate = !showCreate">{{ showCreate ? '取消' : '新建用户' }}</button>
        </div>
        <form v-if="showCreate" class="inline-form" @submit.prevent="submitCreate">
          <input v-model="form.username" placeholder="用户名" required />
          <input v-model="form.password" type="password" placeholder="密码" required />
          <input v-model="form.displayName" placeholder="显示名（可选）" />
          <select v-model="form.role">
            <option value="ADMIN">管理员</option>
            <option value="OPERATOR">操作员</option>
            <option value="OBSERVER">观察者</option>
          </select>
          <select v-model.number="form.workspaceId">
            <option v-for="ws in workspaces" :key="ws.id" :value="ws.id">{{ ws.name }}</option>
          </select>
          <button type="submit" class="primary">保存</button>
        </form>
        <table class="table">
          <thead>
            <tr><th>ID</th><th>用户名</th><th>显示名</th><th>角色</th><th>工作空间</th><th>创建时间</th><th v-if="admin">操作</th></tr>
          </thead>
          <tbody>
            <tr v-for="u in users" :key="u.id">
              <td>{{ u.id }}</td>
              <td>{{ u.username }}</td>
              <td>{{ u.displayName || '-' }}</td>
              <td>
                <select v-if="admin" :value="u.role" @change="onRoleChange(u, $event)">
                  <option value="ADMIN">管理员</option>
                  <option value="OPERATOR">操作员</option>
                  <option value="OBSERVER">观察者</option>
                </select>
                <span v-else>{{ roleLabel[u.role] || u.role }}</span>
              </td>
              <td>{{ workspaces.find((w) => w.id === u.workspaceId)?.name || u.workspaceId }}</td>
              <td>{{ fmtTime(u.createdAt) }}</td>
              <td v-if="admin"><button class="danger" @click="onDeleteUser(u)">删除</button></td>
            </tr>
          </tbody>
        </table>
        <p v-if="users.length === 0" class="empty">暂无用户。</p>
      </template>

      <!-- 工作空间 -->
      <template v-if="tab === 'workspaces'">
        <div v-if="admin" class="toolbar">
          <input v-model="newWorkspaceName" placeholder="新工作空间名称" @keyup.enter="onCreateWorkspace" />
          <button class="primary" @click="onCreateWorkspace">创建</button>
        </div>
        <table class="table">
          <thead><tr><th>ID</th><th>名称</th><th>创建时间</th></tr></thead>
          <tbody>
            <tr v-for="w in workspaces" :key="w.id">
              <td>{{ w.id }}</td>
              <td>{{ w.name }}</td>
              <td>{{ fmtTime(w.createdAt) }}</td>
            </tr>
          </tbody>
        </table>
      </template>

      <!-- 审计日志 -->
      <template v-if="tab === 'audit'">
        <div class="toolbar">
          <button class="primary" @click="refresh">刷新</button>
          <span class="hint">最近 {{ logs.length }} 条</span>
        </div>
        <table class="table">
          <thead><tr><th>时间</th><th>用户</th><th>动作</th><th>详情</th><th>结果</th></tr></thead>
          <tbody>
            <tr v-for="log in logs" :key="log.id">
              <td>{{ fmtTime(log.createdAt) }}</td>
              <td>{{ log.username || '-' }}</td>
              <td>{{ log.action }}</td>
              <td class="detail-cell">{{ log.detail || '-' }}</td>
              <td>
                <span :class="log.success ? 'ok' : 'fail'">{{ log.success ? '成功' : '失败' }}</span>
              </td>
            </tr>
          </tbody>
        </table>
        <p v-if="logs.length === 0" class="empty">暂无审计记录。</p>
      </template>
    </main>
  </div>
</template>

<style scoped>
.tabs {
  display: flex;
  gap: 8px;
  margin-bottom: 14px;
}
.tabs button {
  background: var(--panel);
  border: 1px solid var(--border);
  border-radius: 6px;
  color: var(--muted);
  cursor: pointer;
  padding: 8px 16px;
}
.tabs button.active {
  color: #fff;
  border-color: var(--accent);
}
.toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}
.toolbar input {
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: 6px;
  color: var(--text);
  padding: 8px 10px;
}
.hint {
  color: var(--muted);
  font-size: 12px;
}
button.primary, .inline-form button {
  background: var(--accent);
  border: none;
  border-radius: 6px;
  color: #fff;
  cursor: pointer;
  padding: 8px 14px;
}
button.danger {
  background: none;
  border: 1px solid #dc2626;
  border-radius: 6px;
  color: #f87171;
  cursor: pointer;
  padding: 4px 10px;
}
.inline-form {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}
.inline-form input, .inline-form select {
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: 6px;
  color: var(--text);
  padding: 8px 10px;
}
.table {
  width: 100%;
  border-collapse: collapse;
}
.table th, .table td {
  padding: 9px 12px;
  border-bottom: 1px solid var(--border);
  text-align: left;
}
.table th {
  color: var(--muted);
  font-weight: normal;
  font-size: 12px;
}
.table select {
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: 4px;
  color: var(--text);
  padding: 3px 6px;
}
.detail-cell {
  max-width: 420px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--muted);
  font-size: 12px;
}
.ok { color: var(--green); }
.fail { color: #f87171; }
.error { color: #f87171; }
</style>

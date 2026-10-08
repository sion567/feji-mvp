<script setup lang="ts">
import { useRouter } from 'vue-router'
import { getUsername, clearToken, isAdmin, getRole } from '../api/auth'
import { useWs } from '../ws/wsClient'

defineProps<{ title: string }>()

const router = useRouter()
const { connected } = useWs()

const roleLabel: Record<string, string> = { ADMIN: '管理员', OPERATOR: '操作员', OBSERVER: '观察者' }

function logout() {
  clearToken()
  router.push('/login')
}
</script>

<template>
  <header class="topbar">
    <div class="brand">{{ title }}</div>
    <div class="spacer"></div>
    <span class="ws-dot" :class="{ on: connected }" :title="connected ? '实时通道已连接' : '实时通道未连接'"></span>
    <span class="ws-text">{{ connected ? '实时' : '离线' }}</span>
    <nav>
      <router-link to="/">态势大屏</router-link>
      <router-link to="/devices">设备管理</router-link>
      <router-link to="/waylines">航线管理</router-link>
      <router-link to="/jobs">任务管理</router-link>
      <router-link to="/flights">飞行记录</router-link>
      <router-link to="/plots">地块管理</router-link>
      <router-link v-if="isAdmin()" to="/system">系统管理</router-link>
    </nav>
    <span class="user">{{ getUsername() }}（{{ roleLabel[getRole()] || getRole() }}）</span>
    <button class="link" @click="logout">退出</button>
  </header>
</template>

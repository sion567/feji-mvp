<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import mpegts from 'mpegts.js'
import { startLive, stopLive, type LiveSessionView } from '../api/liveApi'
import type { DeviceEntry } from '../api/deviceApi'

const props = defineProps<{ device: DeviceEntry | null; sessions: LiveSessionView[] }>()

const session = computed(
  () =>
    props.sessions.find(
      (s) => s.state !== 'FAILED' && props.device && s.videoId.includes(props.device.sn)
    ) ?? null
)

const videoEl = ref<HTMLVideoElement>()
const playError = ref('')
let player: mpegts.Player | null = null

watch(
  session,
  (s) => {
    destroyPlayer()
    playError.value = ''
    if (s && s.state === 'LIVE' && videoEl.value) {
      if (!mpegts.getFeatureList().mseLivePlayback) {
        playError.value = '当前浏览器不支持 HTTP-FLV 播放，建议使用 Chrome/Edge'
        return
      }
      const url = s.playUrls['http-flv']
      if (!url) {
        playError.value = '后端未返回播放地址'
        return
      }
      player = mpegts.createPlayer(
        { type: 'flv', isLive: true, url },
        { enableStashBuffer: false, autoCleanupSourceBuffer: true }
      )
      player.attachMediaElement(videoEl.value)
      player.load()
      player.play().catch(() => {
        playError.value = '播放失败：流媒体服务器未就绪或没有视频流'
      })
    }
  },
  { immediate: true }
)

function destroyPlayer() {
  if (player) {
    try {
      player.pause()
      player.unload()
      player.detachMediaElement()
      player.destroy()
    } catch {
      // 播放器可能尚未初始化完成
    }
    player = null
  }
}

onBeforeUnmount(destroyPlayer)

async function toggle() {
  if (!props.device) return
  if (session.value) {
    await stopLive(session.value.videoId)
  } else {
    await startLive(props.device.parentSn || props.device.sn, props.device.sn)
  }
}
</script>

<template>
  <div class="live-panel">
    <div class="panel-title">实时直播</div>
    <template v-if="device">
      <video v-show="session?.state === 'LIVE'" ref="videoEl" class="video" muted autoplay controls></video>
      <p v-if="session?.state === 'STARTING'" class="state">开播指令已下发，等待设备推流…</p>
      <p v-if="session?.state === 'STOPPING'" class="state">正在停止…</p>
      <p v-if="playError" class="error">{{ playError }}</p>
      <p v-if="!session" class="empty">未在直播，点击下方按钮向设备下发开播指令。</p>
      <button class="live-btn" :class="{ stop: session }" @click="toggle">
        {{ session ? '停止直播' : '开始直播' }}
      </button>
    </template>
    <p v-else class="empty">选中设备后可开启实时直播。</p>
  </div>
</template>

<style scoped>
.live-panel {
  padding-bottom: 10px;
}
.video {
  display: block;
  width: 100%;
  aspect-ratio: 16 / 9;
  background: #000;
  border-radius: 6px;
}
.state {
  color: var(--amber);
  font-size: 12px;
  padding: 8px 12px 0;
}
.error {
  color: #f87171;
  font-size: 12px;
  padding: 8px 12px 0;
}
.empty {
  color: var(--muted);
  font-size: 12px;
  padding: 4px 12px 8px;
}
.live-btn {
  margin: 8px 12px 0;
  padding: 8px 0;
  width: calc(100% - 24px);
  border: none;
  border-radius: 6px;
  background: var(--accent);
  color: #fff;
  cursor: pointer;
  font-size: 13px;
}
.live-btn.stop {
  background: #dc2626;
}
</style>

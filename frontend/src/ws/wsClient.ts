import { ref } from 'vue'

type WsListener = (type: string, data: any) => void

const connected = ref(false)
const listeners = new Set<WsListener>()
let socket: WebSocket | null = null
let retryTimer: number | undefined

function connect() {
  if (socket || retryTimer !== undefined) return
  const proto = location.protocol === 'https:' ? 'wss' : 'ws'
  socket = new WebSocket(`${proto}://${location.host}/ws/v1/messages`)
  socket.onopen = () => {
    connected.value = true
  }
  socket.onmessage = (event) => {
    try {
      const msg = JSON.parse(event.data)
      listeners.forEach((fn) => fn(msg.type, msg.data))
    } catch {
      // 忽略非 JSON 帧
    }
  }
  socket.onclose = () => {
    connected.value = false
    socket = null
    retryTimer = window.setTimeout(() => {
      retryTimer = undefined
      connect()
    }, 3000)
  }
  socket.onerror = () => socket?.close()
}

export function useWs() {
  connect()
  return {
    connected,
    subscribe(fn: WsListener) {
      listeners.add(fn)
      return () => listeners.delete(fn)
    }
  }
}

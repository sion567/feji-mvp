// WS 冒烟测试脚本：连接后端 WebSocket，收集 5 秒内收到的推送并打印
// 用法: node scripts/ws-smoke.mjs [ws地址]
const url = process.argv[2] || 'ws://localhost:6789/ws/v1/messages'
const socket = new WebSocket(url)
const received = []

socket.onopen = () => console.log('WS 已连接:', url)
socket.onmessage = (e) => received.push(JSON.parse(e.data))
socket.onclose = () => console.log('WS 已关闭')
socket.onerror = () => {
  console.error('WS 连接失败')
  process.exit(1)
}

setTimeout(() => {
  console.log(`共收到 ${received.length} 条推送`)
  const byType = {}
  for (const m of received) byType[m.type] = (byType[m.type] || 0) + 1
  console.log('按类型统计:', JSON.stringify(byType))
  for (const m of received.slice(0, 2)) {
    console.log('样例:', JSON.stringify(m).slice(0, 220))
  }
  process.exit(0)
}, 5000)

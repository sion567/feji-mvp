#!/usr/bin/env node
/**
 * 虚拟设备模拟器：模拟 1 台机场(Dock 2) + 1 架无人机(M3TD) 接入平台。
 *
 * 能力：
 *  - 上线推送 sys/product/{sn}/status（机场+子机拓扑），退出时推送离线
 *  - 每秒推送机场/无人机 OSD（无人机绕机场圆周飞行，电量缓慢下降）
 *  - 周期上报直播能力 live_capacity_update
 *  - 应答 services 指令（live_start_push/live_stop_push 等，回 services_reply）
 *  - 收到开播指令后可选调用 ffmpeg 向下发地址推测试流（默认开启，--no-ffmpeg 关闭）
 *  - --demo-alarm：循环演示安全告警（HMS 事件 → 低电量 → RTK 断连，每 45 秒切换一种）
 *
 * 用法:
 *   node mock-dock.mjs [--broker mqtt://localhost:1883] [--dock-sn MOCK-DOCK-001]
 *                      [--drone-sn MOCK-DRONE-001] [--lat 31.02] [--lng 121.44]
 *                      [--no-ffmpeg] [--demo-alarm]
 */
import { randomUUID } from 'node:crypto'
import { spawn } from 'node:child_process'
import mqtt from 'mqtt'

const args = process.argv.slice(2)
function arg(name, def) {
  const i = args.indexOf(name)
  return i >= 0 && args[i + 1] !== undefined ? args[i + 1] : def
}

const broker = arg('--broker', 'mqtt://localhost:1883')
const dockSn = arg('--dock-sn', 'MOCK-DOCK-001')
const droneSn = arg('--drone-sn', 'MOCK-DRONE-001')
const baseLat = parseFloat(arg('--lat', '31.02'))
const baseLng = parseFloat(arg('--lng', '121.44'))
const useFfmpeg = !args.includes('--no-ffmpeg')
const demoAlarm = args.includes('--demo-alarm')

const client = mqtt.connect(broker, { clientId: 'mock-dock-' + randomUUID().slice(0, 8) })
let droneAngle = 0
let battery = 95
let ffmpegProc = null

function statusEnvelope(data) {
  return { tid: randomUUID(), bid: randomUUID(), timestamp: Date.now(), data }
}

function eventEnvelope(method, data) {
  return { tid: randomUUID(), bid: randomUUID(), timestamp: Date.now(), method, data }
}

function publish(topic, obj) {
  client.publish(topic, JSON.stringify(obj), { qos: 1 })
}

function publishOnline() {
  publish(`sys/product/${dockSn}/status`, statusEnvelope({
    online: { sn: dockSn, model: '3-2-0', version: 'mock-1.0.0', child: [{ sn: droneSn, model: '0-91-1' }] }
  }))
}

function publishOffline() {
  publish(`sys/product/${dockSn}/status`, statusEnvelope({ offline: { sn: droneSn } }))
  publish(`sys/product/${dockSn}/status`, statusEnvelope({ offline: { sn: dockSn } }))
}

function publishCapacity() {
  publish(`thing/product/${dockSn}/events`, eventEnvelope('live_capacity_update', {
    live_capacity: {
      camera_list: [{
        camera_index: '93-0-0',
        available_video_number: 1,
        coexist_video_number_max: 1,
        video_list: [{ video_index: 'normal-0', video_type: 'normal', switchable_video_types: ['normal'] }]
      }]
    }
  }))
}

// ---- 无人机位置状态（供 OSD 循环与 DRC 目标移动共用）----
let flying = false
let droneLat = baseLat
let droneLng = baseLng
let droneAlt = 100
const currentDroneLat = () => droneLat
const currentDroneLng = () => droneLng
const currentDroneAlt = () => droneAlt

function tick() {
  publish(`thing/product/${dockSn}/osd`, { tid: randomUUID(), bid: randomUUID(), timestamp: Date.now(), data: {
    mode_code: flying ? 1 : 0, latitude: baseLat, longitude: baseLng, height: 15,
    cover_state: flying ? 1 : 0, putter_state: flying ? 1 : 0, drone_in_dock: !flying, drone_charge_state: flying ? 0 : 2,
    humidity: 62, temperature: 24.5, environment_temperature: 22.8, wind_speed: 2.0, job_number: 42
  } })

  if (!flying) {
    // 地面态：待命在机场位置
    droneLat = baseLat
    droneLng = baseLng
    droneAlt = 0
  } else if (target) {
    // DRC 目标点：每秒朝目标移动 20%
    const move = (cur, dst) => cur + (dst - cur) * 0.2
    droneLat = move(droneLat, target.lat)
    droneLng = move(droneLng, target.lng)
    droneAlt = move(droneAlt, target.alt)
  } else {
    droneAngle += 0.05
    droneLat = baseLat + 0.004 * Math.cos(droneAngle)
    droneLng = baseLng + 0.004 * Math.sin(droneAngle)
    droneAlt = 100 + 10 * Math.sin(droneAngle)
  }
  if (flying) {
    battery = Math.max(20, battery - 0.05)
  }
  const batteryDip = Date.now() < batteryDipUntil
  const rtkLoss = Date.now() < rtkLossUntil
  publish(`thing/product/${droneSn}/osd`, { tid: randomUUID(), bid: randomUUID(), timestamp: Date.now(), data: {
    mode_code: flying ? 5 : 0, gear: flying ? 1 : 0, latitude: droneLat, longitude: droneLng,
    height: flying ? droneAlt : 0, elevation: flying ? droneAlt - 12 : 0,
    horizontal_speed: flying ? 5.2 : 0, vertical_speed: 0.1,
    attitude_head: ((droneAngle * 180) / Math.PI + 90) % 360,
    home_distance: flying ? 380 : 0, wind_speed: 3.1, wind_direction: 120,
    battery: { capacity_percent: batteryDip ? 12 : Math.round(battery), remain_flight_time: 1100, return_home_power: 15, landing_power: 5 },
    position_state: { gps_number: 18, is_fixed: !rtkLoss, quality: rtkLoss ? 3 : 5, rtk_number: rtkLoss ? 0 : 18 },
    total_flight_time: 430, firmware_version: 'mock-1.0.0'
  } })
}

function startFfmpeg(url) {
  stopFfmpeg()
  console.log('[mock] ffmpeg 推流 →', url)
  ffmpegProc = spawn('ffmpeg', [
    '-re', '-stream_loop', '-1',
    '-f', 'lavfi', '-i', 'testsrc2=size=1280x720:rate=25',
    '-f', 'lavfi', '-i', 'sine=frequency=1000',
    '-c:v', 'libx264', '-preset', 'ultrafast', '-tune', 'zerolatency',
    '-pix_fmt', 'yuv420p', '-g', '50',
    '-c:a', 'aac',
    '-f', 'flv', url
  ], { stdio: 'ignore' })
  ffmpegProc.on('error', (e) => console.error('[mock] ffmpeg 启动失败:', e.message))
  ffmpegProc.on('exit', (code) => console.log('[mock] ffmpeg 退出, code =', code))
}

function stopFfmpeg() {
  if (ffmpegProc) {
    try { ffmpegProc.kill('SIGKILL') } catch { /* 进程已退出 */ }
    ffmpegProc = null
  }
}

// ---- 安全告警演示（--demo-alarm）：45 秒一循环，依次 HMS 事件 / 低电量 / RTK 断连 ----
let batteryDipUntil = 0
let rtkLossUntil = 0
let demoStep = 0

function publishHmsEvent() {
  publish(`thing/product/${droneSn}/events`, eventEnvelope('device_hms', { hms: [
    { code: '0x16100086', created_at: Date.now(), host: 'DRONE', level: 2, module: 'battery', key: 'hms_0x16100086_bat_overheat' },
    { code: '0x16220008', created_at: Date.now(), host: 'DRONE', level: 3, module: 'flight_control', key: 'hms_0x16220008_esc_err' }
  ] }))
  console.log('[mock] 演示：上报 device_hms 健康告警')
}

function cycleDemoAlarm() {
  const step = demoStep++ % 3
  if (step === 0) {
    publishHmsEvent()
  } else if (step === 1) {
    batteryDipUntil = Date.now() + 8000
    console.log('[mock] 演示：低电量（8 秒后恢复）')
  } else {
    rtkLossUntil = Date.now() + 8000
    console.log('[mock] 演示：RTK 断连（8 秒后恢复）')
  }
}

client.on('connect', () => {
  console.log(`[mock] 已连接 ${broker}, 机场=${dockSn}, 无人机=${droneSn}, ffmpeg推流=${useFfmpeg ? '开' : '关'}`)
  client.subscribe([
    `thing/product/${dockSn}/services`,
    `thing/product/${droneSn}/services`,
    `thing/product/${dockSn}/drc/down`,
    `thing/product/${droneSn}/drc/down`
  ])
  publishOnline()
  publishCapacity()
  // 周期重发上线/能力，规避平台侧晚启动漏掉一次性状态推送
  setInterval(publishOnline, 10000)
  setInterval(publishCapacity, 10000)
  setInterval(tick, 1000)
  if (demoAlarm) {
    setTimeout(cycleDemoAlarm, 15000)
    setInterval(cycleDemoAlarm, 45000)
  }
})

client.on('message', (topic, payload) => {
  let msg
  try {
    msg = JSON.parse(payload.toString())
  } catch {
    return
  }
  const sn = topic.split('/')[2]
  if (topic.endsWith('/drc/down')) {
    handleDrcDown(sn, msg)
    return
  }
  const method = msg.method || ''
  const data = msg.data || {}
  console.log('[mock] 收到指令:', method, JSON.stringify(data))

  switch (method) {
    case 'live_start_push':
      if (useFfmpeg && typeof data.url === 'string' && data.url.startsWith('rtmp://')) {
        startFfmpeg(data.url)
      }
      reply(sn, msg, { result: 0, video_id: data.video_id })
      break
    case 'live_stop_push':
      stopFfmpeg()
      reply(sn, msg, { result: 0 })
      break
    case 'flighttask_prepare': {
      // task_type: 0 立即 / 1 定时 —— 定时任务到 execute_time 开始回报进度（已过期则立即执行）
      const task = data.flight_id ? data : (data.flighttask || {})
      const flightId = task.flight_id
      if (Number(task.task_type) === 1) {
        const delay = Math.max(0, Math.min((task.execute_time || 0) - Date.now(), 24 * 3600_000))
        if (delay > 1500) {
          setTimeout(() => { flying = true }, delay)
        } else {
          flying = true
        }
      }
      reply(sn, msg, { result: 0 })
      break
    }
    case 'flighttask_execute': {
      const flightId = (data && data.flight_id) || 'unknown'
      flying = true
      startWaylineProgress(flightId)
      reply(sn, msg, { result: 0 })
      break
    }
    case 'flighttask_cancel':
      stopWaylineProgress()
      reply(sn, msg, { result: 0 })
      break
    case 'drc_enter':
      drcActive = true
      console.log('[mock] 进入 DRC 模式')
      reply(sn, msg, { result: 0 })
      break
    case 'drc_exit':
      drcActive = false
      console.log('[mock] 退出 DRC 模式')
      reply(sn, msg, { result: 0 })
      break
    case 'drc_flyto':
      setTarget(data.lat, data.lng, data.height)
      reply(sn, msg, { result: 0 })
      break
    case 'takeoff_to_point':
      setTarget(data.lat, data.lng, data.height)
      reply(sn, msg, { result: 0 })
      break
    default:
      reply(sn, msg, { result: 0 })
  }
})

// ---- DRC 下行通道：心跳 / 摇杆 ----
let drcActive = false
let target = null // {lat, lng, alt}

function setTarget(lat, lng, alt) {
  if (typeof lat !== 'number' || typeof lng !== 'number') return
  flying = true
  target = { lat, lng, alt: typeof alt === 'number' ? alt : 100 }
  console.log('[mock] 飞向目标点:', lat.toFixed(5), lng.toFixed(5), '高度', target.alt)
}

function handleDrcDown(sn, msg) {
  const method = msg.method || ''
  const data = msg.data || {}
  if (method === 'heartbeat') {
    // 心跳无需动作，按协议回 ack 到 drc/up
    publish(`thing/product/${sn}/drc/up`, {
      tid: randomUUID(), bid: randomUUID(), timestamp: Date.now(), method: 'heartbeat', data: { result: 0 }
    })
  } else if (method === 'drone_control') {
    // 摇杆量模拟为位置微移（无真实动力模型，仅示意）
    if (target == null) {
      target = { lat: currentDroneLat, lng: currentDroneLng, alt: currentDroneAlt }
    }
    target.lat += (data.y || 0) * 0.00002
    target.lng += (data.x || 0) * 0.00002
    target.alt = Math.max(10, target.alt + (data.z || 0) * 0.02)
    publish(`thing/product/${sn}/drc/up`, {
      tid: randomUUID(), bid: randomUUID(), timestamp: Date.now(), method: 'drone_control', data: { result: 0 }
    })
  }
}

// ---- 航线任务进度模拟：约 16 秒从 0 飞到 100% ----
let progressTimer = null
let progressPercent = 0
let progressFlightId = null

function startWaylineProgress(flightId) {
  stopWaylineProgress()
  progressFlightId = flightId
  progressPercent = 0
  console.log('[mock] 航线任务开始执行, flight_id =', flightId)
  progressTimer = setInterval(() => {
    progressPercent = Math.min(100, progressPercent + 7)
    const done = progressPercent >= 100
    // 任务进度按协议从网关（机场）上报
    publish(`thing/product/${dockSn}/events`, {
      tid: randomUUID(), bid: randomUUID(), timestamp: Date.now(), method: 'flighttask_progress',
      data: {
        status: done ? 'ok' : 'in_progress',
        progress: { current_step: progressPercent < 50 ? 2 : 3, percent: progressPercent },
        ext: { flight_id: progressFlightId }
      }
    })
    if (done) {
      stopWaylineProgress()
      console.log('[mock] 航线任务完成')
      // 任务完成后 5 秒模拟返航降落
      setTimeout(() => {
        flying = false
        target = null
        console.log('[mock] 已降落回机场')
      }, 5000)
    }
  }, 1200)
}

function stopWaylineProgress() {
  if (progressTimer) {
    clearInterval(progressTimer)
    progressTimer = null
  }
}

function reply(sn, req, data) {
  publish(`thing/product/${sn}/services_reply`, {
    tid: req.tid || randomUUID(),
    bid: req.bid || randomUUID(),
    timestamp: Date.now(),
    method: req.method,
    data
  })
}

process.on('SIGINT', () => {
  console.log('\n[mock] 推送离线并退出…')
  publishOffline()
  stopFfmpeg()
  setTimeout(() => process.exit(0), 300)
})

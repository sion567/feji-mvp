const DRONE: Record<number, string> = {
  0: '待机',
  1: '起飞准备',
  2: '起飞准备完成',
  3: '手动飞行',
  4: '自动起飞',
  5: '航线飞行',
  6: '全景拍照',
  7: '智能跟随',
  8: 'ADS-B 避让',
  9: '自动返航',
  10: '自动降落',
  11: '迫降',
  12: '三桨降落',
  13: '升级中',
  14: '未连接'
}

const DOCK: Record<number, string> = {
  0: '待机',
  1: '作业中'
}

function label(table: Record<number, string>, code?: number) {
  if (code == null) return '-'
  return table[code] ?? `未知(${code})`
}

export function droneModeLabel(code?: number) {
  return label(DRONE, code)
}

export function dockModeLabel(code?: number) {
  return label(DOCK, code)
}

export function coverLabel(code?: number) {
  if (code == null) return '-'
  return code === 0 ? '关闭' : code === 1 ? '打开' : `未知(${code})`
}

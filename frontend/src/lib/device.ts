export function kindLabel(kind?: string | null) {
  if (kind === 'dock') return '机场'
  if (kind === 'drone') return '无人机'
  if (kind === 'rc') return '遥控器'
  return '设备'
}

export function fmtNum(value: unknown, unit = '') {
  return typeof value === 'number' && isFinite(value) ? `${Math.round(value * 100) / 100}${unit}` : '-'
}

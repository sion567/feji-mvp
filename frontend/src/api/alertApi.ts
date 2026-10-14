import { get } from './http'

export interface DeviceAlertView {
  id: number
  sn: string
  category: 'HMS' | 'BATTERY' | 'RTK' | string
  level: 'NOTICE' | 'WARN' | 'CRITICAL' | string
  eventKey: string | null
  message: string | null
  payload: string | null
  occurredAt: number
  createdAt: string
}

export function listAlerts(params: { sn?: string; level?: string; limit?: number } = {}) {
  const query = new URLSearchParams()
  if (params.sn) query.set('sn', params.sn)
  if (params.level) query.set('level', params.level)
  query.set('limit', String(params.limit ?? 50))
  return get<DeviceAlertView[]>(`/api/v1/alerts?${query.toString()}`)
}

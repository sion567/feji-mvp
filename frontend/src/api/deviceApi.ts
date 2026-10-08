import { get, patch, del } from './http'

export interface DeviceEntry {
  sn: string
  deviceName: string | null
  modelName: string | null
  model: string | null
  kind: string | null
  online: boolean
  parentSn: string | null
  lastSeen: number
}

export interface TrackPoint {
  lng: number
  lat: number
  alt: number | null
  ts: number
}

export function listDevices() {
  return get<DeviceEntry[]>('/api/v1/devices')
}

export function latestOsd(sn: string) {
  return get<string>(`/api/v1/devices/${sn}/osd`)
}

export function renameDevice(sn: string, deviceName: string) {
  return patch<void>(`/api/v1/devices/${sn}`, { deviceName })
}

export function unbindDevice(sn: string) {
  return del<void>(`/api/v1/devices/${sn}`)
}

export function getTrack(sn: string) {
  return get<TrackPoint[]>(`/api/v1/devices/${sn}/track`)
}

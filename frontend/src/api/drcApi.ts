import { get, post } from './http'

export function drcEnter(sn: string) {
  return post<void>(`/api/v1/drc/${sn}/enter`)
}

export function drcExit(sn: string) {
  return post<void>(`/api/v1/drc/${sn}/exit`)
}

export function drcFlyTo(sn: string, lat: number, lng: number, height: number, safetyHeight = 80) {
  return post<void>(`/api/v1/drc/${sn}/flyto`, { lat, lng, height, safetyHeight })
}

export function drcTakeoff(
  sn: string,
  height: number,
  lat?: number,
  lng?: number,
  safetyHeight = 80
) {
  return post<void>(`/api/v1/drc/${sn}/takeoff`, { lat, lng, height, safetyHeight })
}

export function drcControl(sn: string, x: number, y: number, z: number) {
  return post<void>(`/api/v1/drc/${sn}/control`, { x, y, z })
}

export function drcState() {
  return get<string[]>('/api/v1/drc/state')
}

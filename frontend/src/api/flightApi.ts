import { get, patch } from './http'

export interface FlightRecordView {
  id: number
  droneSn: string
  jobId: number | null
  jobName: string | null
  status: 'FLYING' | 'CLOSED'
  takeoffTime: number | null
  landingTime: number | null
  takeoffLat: number | null
  takeoffLng: number | null
  landingLat: number | null
  landingLng: number | null
  maxHeight: number | null
  distanceM: number | null
  reportStatus: 'NONE' | 'FILED' | 'APPROVED' | null
  reportNo: string | null
  createdAt: string
}

export interface FlightTelemetryPoint {
  id: number
  flightId: number
  ts: number
  lat: number | null
  lng: number | null
  alt: number | null
  elevation: number | null
  speed: number | null
  heading: number | null
  battery: number | null
  mode: string | null
}

export function listFlights(limit = 200) {
  return get<FlightRecordView[]>(`/api/v1/flights?limit=${limit}`)
}

export function flightDetail(id: number) {
  return get<FlightRecordView>(`/api/v1/flights/${id}`)
}

export function flightTelemetry(id: number) {
  return get<FlightTelemetryPoint[]>(`/api/v1/flights/${id}/telemetry`)
}

export function updateJobReport(id: number, reportStatus: 'NONE' | 'FILED' | 'APPROVED', reportNo?: string) {
  return patch<unknown>(`/api/v1/wayline-jobs/${id}/report`, { reportStatus, reportNo })
}

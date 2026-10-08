import { get, post } from './http'

export type LiveSessionState = 'STARTING' | 'LIVE' | 'STOPPING' | 'FAILED'

export interface LiveSessionView {
  videoId: string
  gatewaySn: string
  streamId: string
  videoQuality: number
  state: LiveSessionState
  startedAt: number
  playUrls: Record<string, string>
}

export function startLive(gatewaySn: string, droneSn?: string, videoQuality = 3) {
  return post<LiveSessionView>('/api/v1/live/start', { gatewaySn, droneSn, videoQuality })
}

export function stopLive(videoId: string) {
  return post<void>('/api/v1/live/stop', { videoId })
}

export function listLives() {
  return get<LiveSessionView[]>('/api/v1/live/sessions')
}

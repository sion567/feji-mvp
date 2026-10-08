import { get, post, postForm, del } from './http'

// ---- 航线库 ----

export interface WaylineFileView {
  id: number
  name: string
  fileKey: string
  sizeBytes: number | null
  droneModelKey: string | null
  droneModelName: string | null
  payloadModelName: string | null
  templateType: string | null
  waylineCount: number | null
  waypointCount: number | null
  creator: string | null
  createdAt: string
}

export interface WaypointAction {
  actionId: number
  func: string
  params: Record<string, string>
}

export interface WaypointActionGroup {
  groupId: number
  startIndex: number | null
  endIndex: number | null
  triggerType: string | null
  actions: WaypointAction[]
}

export interface WaylineWaypoint {
  index: number | null
  longitude: number | null
  latitude: number | null
  executeHeight: number | null
  speed: number | null
  turnMode: string | null
  headingMode: string | null
  actionGroups: WaypointActionGroup[]
}

export interface Wayline {
  waylineId: number | null
  templateId: number | null
  autoFlightSpeed: number | null
  executeHeightMode: string | null
  waypoints: WaylineWaypoint[]
}

export interface WaylineDetail {
  name: string
  author: string | null
  droneModelKey: string | null
  droneModelName: string | null
  payloadModelName: string | null
  templates: { templateId: number | null; templateType: string | null }[]
  waylines: Wayline[]
}

export function uploadWayline(file: File) {
  const form = new FormData()
  form.append('file', file)
  return postForm<WaylineFileView>('/api/v1/waylines/upload', form)
}

export function listWaylines() {
  return get<WaylineFileView[]>('/api/v1/waylines')
}

export function waylineDetail(id: number) {
  return get<WaylineDetail>(`/api/v1/waylines/${id}`)
}

export function deleteWayline(id: number) {
  return del<void>(`/api/v1/waylines/${id}`)
}

// ---- 航线任务 ----

export interface JobView {
  id: number
  name: string
  flightId: string | null
  waylineFileId: number
  droneSn: string
  taskType: 'IMMEDIATE' | 'TIMED' | 'RECURRING'
  executeTime: number | null
  status: 'PENDING' | 'DISPATCHING' | 'RUNNING' | 'SUCCESS' | 'FAILED' | 'CANCELED'
  progress: number
  message: string | null
  reportStatus: 'NONE' | 'FILED' | 'APPROVED' | null
  reportNo: string | null
  creator: string | null
  updatedAt: string | null
  complianceWarning?: boolean
  complianceMessage?: string | null
}

export function createJob(data: {
  name?: string
  waylineFileId: number
  droneSn: string
  taskType: 'IMMEDIATE' | 'TIMED' | 'RECURRING'
  executeTime?: number
}) {
  return post<JobView>('/api/v1/wayline-jobs', data)
}

export function listJobs() {
  return get<JobView[]>('/api/v1/wayline-jobs')
}

export function cancelJob(id: number) {
  return post<void>(`/api/v1/wayline-jobs/${id}/cancel`)
}

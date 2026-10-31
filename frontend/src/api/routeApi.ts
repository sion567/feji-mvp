import { post } from './http'
import type { WaylineFileView } from './waylineApi'

export interface RoutePointInput {
  lat: number
  lng: number
}

/** 排序后的点：sourceIndex 为选点原始下标，便于回显 */
export interface RoutePointView {
  lat: number
  lng: number
  sourceIndex: number
}

export interface RoutePlanResponse {
  orderedPoints: RoutePointView[]
  totalDistanceMeters: number
  strategy: 'BRUTE_FORCE' | 'NN_2OPT'
}

/** 选点航线排序预览（起点 = 设备当前定位） */
export function planRoute(sn: string, points: RoutePointInput[], height?: number) {
  return post<RoutePlanResponse>('/api/v1/route/plan', { sn, points, height })
}

/** 选点生成 KMZ 航线入库（后续用 createJob 下发执行） */
export function composeRoute(
  sn: string,
  name: string,
  points: RoutePointInput[],
  height?: number,
  speed?: number
) {
  return post<WaylineFileView>('/api/v1/route/compose', { sn, name, points, height, speed })
}

import { get, post, patch, del } from './http'

export interface PlotView {
  id: number
  name: string
  code: string | null
  category: string | null
  crop: string | null
  areaMu: number | null
  description: string | null
  coordinates: [number, number][]
  color: string | null
  creator: string | null
  createdAt: string
}

export interface PlotSaveRequest {
  name: string
  code?: string
  category?: string
  crop?: string
  description?: string
  color?: string
  coordinates: [number, number][]
}

export function listPlots() {
  return get<PlotView[]>('/api/v1/plots')
}

export function createPlot(data: PlotSaveRequest) {
  return post<PlotView>('/api/v1/plots', data)
}

export function updatePlot(id: number, data: PlotSaveRequest) {
  return patch<PlotView>(`/api/v1/plots/${id}`, data)
}

export function deletePlot(id: number) {
  return del<void>(`/api/v1/plots/${id}`)
}

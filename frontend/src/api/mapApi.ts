import { get, post, del } from './http'

export interface MapElementView {
  id: number
  name: string
  elementType: 'POINT' | 'LINE' | 'POLYGON'
  coordinates: [number, number][]
  color: string | null
  creator: string | null
  createdAt: string
}

export function listMapElements() {
  return get<MapElementView[]>('/api/v1/map-elements')
}

export function createMapElement(data: {
  name: string
  elementType: 'POINT' | 'LINE' | 'POLYGON'
  coordinates: [number, number][]
  color?: string
}) {
  return post<MapElementView>('/api/v1/map-elements', data)
}

export function deleteMapElement(id: number) {
  return del<void>(`/api/v1/map-elements/${id}`)
}

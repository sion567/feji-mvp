import { get, post, patch, del } from './http'
import type { Role } from './auth'

export interface UserView {
  id: number
  username: string
  displayName: string | null
  role: Role
  workspaceId: number
  createdAt: string
}

export interface WorkspaceView {
  id: number
  name: string
  createdAt: string
}

export interface AuditLogView {
  id: number
  username: string | null
  action: string
  detail: string | null
  success: boolean
  createdAt: string
}

export function listUsers() {
  return get<UserView[]>('/api/v1/users')
}

export function createUser(data: {
  username: string
  password: string
  displayName?: string
  role: Role
  workspaceId?: number
}) {
  return post<UserView>('/api/v1/users', data)
}

export function updateUserRole(id: number, role: Role) {
  return patch<void>(`/api/v1/users/${id}/role`, { id, role })
}

export function deleteUser(id: number) {
  return del<void>(`/api/v1/users/${id}`)
}

export function listWorkspaces() {
  return get<WorkspaceView[]>('/api/v1/workspaces')
}

export function createWorkspace(name: string) {
  return post<WorkspaceView>('/api/v1/workspaces', { name })
}

export function listAuditLogs(limit = 200) {
  return get<AuditLogView[]>(`/api/v1/audit-logs?limit=${limit}`)
}

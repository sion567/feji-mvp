import { post, TOKEN_KEY, USER_KEY, clearToken as clearStorage } from './http'

export type Role = 'ADMIN' | 'OPERATOR' | 'OBSERVER'

export interface LoginResult {
  token: string
  username: string
  workspaceId: number
  role: Role
}

const ROLE_KEY = 'feiji_role'

export function saveSession(result: LoginResult) {
  localStorage.setItem(TOKEN_KEY, result.token)
  localStorage.setItem(USER_KEY, result.username)
  localStorage.setItem(ROLE_KEY, result.role)
}

export function getToken() {
  return localStorage.getItem(TOKEN_KEY)
}

export function getUsername() {
  return localStorage.getItem(USER_KEY)
}

export function getRole(): Role {
  return (localStorage.getItem(ROLE_KEY) as Role) || 'OBSERVER'
}

export function isAdmin() {
  return getRole() === 'ADMIN'
}

export function canOperate() {
  return getRole() === 'ADMIN' || getRole() === 'OPERATOR'
}

export function clearToken() {
  clearStorage()
  localStorage.removeItem(ROLE_KEY)
}

export function login(username: string, password: string) {
  return post<LoginResult>('/api/v1/auth/login', { username, password }).then(saveSession)
}

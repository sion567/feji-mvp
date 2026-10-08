const TOKEN_KEY = 'feiji_token'
const USER_KEY = 'feiji_user'

function clearToken() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
}

export async function request<T>(url: string, options: RequestInit = {}): Promise<T> {
  const token = localStorage.getItem(TOKEN_KEY)
  const isForm = options.body instanceof FormData
  const res = await fetch(url, {
    ...options,
    headers: {
      ...(isForm ? {} : { 'Content-Type': 'application/json' }),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(options.headers || {})
    }
  })
  if (res.status === 401) {
    clearToken()
    location.href = '/login'
    throw new Error('未登录或登录已过期')
  }
  const body = await res.json()
  if (body.code !== 0) {
    throw new Error(body.message || `请求失败(${body.code})`)
  }
  return body.data as T
}

export function post<T>(url: string, data?: unknown): Promise<T> {
  return request<T>(url, { method: 'POST', body: data ? JSON.stringify(data) : undefined })
}

export function postForm<T>(url: string, form: FormData): Promise<T> {
  return request<T>(url, { method: 'POST', body: form })
}

export function patch<T>(url: string, data?: unknown): Promise<T> {
  return request<T>(url, { method: 'PATCH', body: data ? JSON.stringify(data) : undefined })
}

export function del<T>(url: string): Promise<T> {
  return request<T>(url, { method: 'DELETE' })
}

export function get<T>(url: string): Promise<T> {
  return request<T>(url)
}

export { TOKEN_KEY, USER_KEY, clearToken }

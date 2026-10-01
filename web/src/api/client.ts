export type Role = 'STUDENT' | 'ADMIN'

export interface SessionUser {
  id: number
  name: string
  mobile: string
  role: Role
}

export interface Session {
  accessToken: string
  refreshToken: string
  user: SessionUser
}

/** Error returned by the API; `message` is written for end users and can be shown as-is. */
export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly fieldErrors: Record<string, string>

  constructor(status: number, code: string, message: string, fieldErrors: Record<string, string> = {}) {
    super(message)
    this.status = status
    this.code = code
    this.fieldErrors = fieldErrors
  }
}

// Session storage (not local storage): many students share one school tablet,
// so closing the tab should log the student out.
const SESSION_KEY = 'quiz.session'
const listeners = new Set<(session: Session | null) => void>()

export function getSession(): Session | null {
  try {
    const raw = sessionStorage.getItem(SESSION_KEY)
    return raw ? (JSON.parse(raw) as Session) : null
  } catch {
    return null
  }
}

export function setSession(session: Session | null) {
  try {
    if (session) sessionStorage.setItem(SESSION_KEY, JSON.stringify(session))
    else sessionStorage.removeItem(SESSION_KEY)
  } catch {
    // Storage unavailable (e.g. private mode); the session still lives in memory via listeners.
  }
  listeners.forEach((listener) => listener(session))
}

export function onSessionChange(listener: (session: Session | null) => void): () => void {
  listeners.add(listener)
  return () => listeners.delete(listener)
}

const API_BASE = '/api'

async function toApiError(res: Response): Promise<ApiError> {
  try {
    const body = await res.json()
    return new ApiError(res.status, body.code ?? 'ERROR', body.detail ?? 'Something went wrong. Please try again.', body.errors ?? {})
  } catch {
    return new ApiError(res.status, 'ERROR', 'Something went wrong. Please try again.')
  }
}

async function send(path: string, init: RequestInit, accessToken?: string): Promise<Response> {
  const headers = new Headers(init.headers)
  if (init.body) headers.set('Content-Type', 'application/json')
  if (accessToken) headers.set('Authorization', `Bearer ${accessToken}`)
  try {
    return await fetch(API_BASE + path, { ...init, headers })
  } catch {
    throw new ApiError(0, 'NETWORK', 'No internet connection. Please try again.')
  }
}

let refreshing: Promise<Session | null> | null = null

/** Gets a new token pair; concurrent callers share one request so the refresh token is used once. */
function refreshSession(): Promise<Session | null> {
  refreshing ??= (async () => {
    const current = getSession()
    if (!current) return null
    const res = await send('/auth/refresh', {
      method: 'POST',
      body: JSON.stringify({ refreshToken: current.refreshToken }),
    })
    if (!res.ok) {
      setSession(null)
      return null
    }
    const next = (await res.json()) as Session
    setSession(next)
    return next
  })().finally(() => {
    refreshing = null
  })
  return refreshing
}

/** Calls the API as the logged-in user, refreshing the access token once if it has expired. */
export async function apiFetch<T>(path: string, init: RequestInit = {}): Promise<T> {
  let res = await send(path, init, getSession()?.accessToken)
  if (res.status === 401 && getSession()) {
    const renewed = await refreshSession()
    if (!renewed) throw new ApiError(401, 'SESSION_EXPIRED', 'Please log in again.')
    res = await send(path, init, renewed.accessToken)
  }
  if (!res.ok) throw await toApiError(res)
  return (res.status === 204 ? undefined : await res.json()) as T
}

/** Calls an endpoint that does not need a logged-in user. */
export async function publicFetch<T>(path: string, init: RequestInit = {}): Promise<T> {
  const res = await send(path, init)
  if (!res.ok) throw await toApiError(res)
  return (res.status === 204 ? undefined : await res.json()) as T
}

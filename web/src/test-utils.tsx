import { render } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { vi } from 'vitest'
import App from './App'
import { setSession, type Role, type Session } from './api/client'
import { AuthProvider } from './auth/AuthProvider'

export function renderApp(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <AuthProvider>
        <App />
      </AuthProvider>
    </MemoryRouter>,
  )
}

export function session(role: Role, overrides: Partial<Session> = {}): Session {
  return {
    accessToken: 'access-1',
    refreshToken: 'refresh-1',
    user: { id: 1, name: role === 'ADMIN' ? 'Head Teacher' : 'Asha', mobile: '9876543210', role },
    ...overrides,
  }
}

export function signIn(role: Role) {
  setSession(session(role))
}

export function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

export function problem(status: number, code: string, detail: string, errors?: Record<string, string>): Response {
  return json({ status, code, detail, errors }, status)
}

type Handler = (url: string, init: RequestInit) => Response | Promise<Response>

/** Stubs fetch; each call is answered by the first handler whose key ("METHOD /path") the request starts with. */
export function mockApi(handlers: Record<string, Handler>) {
  const fetchMock = vi.fn(async (input: RequestInfo | URL, init: RequestInit = {}) => {
    const url = String(input)
    const key = `${init.method ?? 'GET'} ${url}`
    const match = Object.keys(handlers).find((k) => key.startsWith(k))
    if (!match) throw new Error(`Unexpected request: ${key}`)
    return handlers[match](url, init)
  })
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

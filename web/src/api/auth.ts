import { publicFetch, type Session } from './client'

export interface RegisterInput {
  name: string
  mobile: string
  pin: string
  email?: string
  school?: string
}

export function register(input: RegisterInput): Promise<Session> {
  return publicFetch('/auth/register', { method: 'POST', body: JSON.stringify(input) })
}

export function login(mobile: string, pin: string): Promise<Session> {
  return publicFetch('/auth/login', { method: 'POST', body: JSON.stringify({ mobile, pin }) })
}

export function logout(refreshToken: string): Promise<void> {
  return publicFetch('/auth/logout', { method: 'POST', body: JSON.stringify({ refreshToken }) })
}

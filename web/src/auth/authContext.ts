import { createContext, useContext } from 'react'
import type { RegisterInput } from '../api/auth'
import type { Role, SessionUser } from '../api/client'

export interface AuthContextValue {
  user: SessionUser | null
  login: (mobile: string, pin: string) => Promise<SessionUser>
  register: (input: RegisterInput) => Promise<SessionUser>
  logout: () => Promise<void>
}

export const AuthContext = createContext<AuthContextValue | null>(null)

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used inside <AuthProvider>')
  return ctx
}

export function homePathFor(role: Role): string {
  return role === 'ADMIN' ? '/admin/users' : '/student'
}

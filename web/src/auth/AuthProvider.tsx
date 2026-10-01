import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import * as authApi from '../api/auth'
import { getSession, onSessionChange, setSession, type Session } from '../api/client'
import { AuthContext } from './authContext'

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSessionState] = useState<Session | null>(getSession)

  // Keeps React in sync when the API client refreshes or drops the session.
  useEffect(() => onSessionChange(setSessionState), [])

  const login = useCallback(async (mobile: string, pin: string) => {
    const next = await authApi.login(mobile, pin)
    setSession(next)
    return next.user
  }, [])

  const register = useCallback(async (input: authApi.RegisterInput) => {
    const next = await authApi.register(input)
    setSession(next)
    return next.user
  }, [])

  const logout = useCallback(async () => {
    const current = getSession()
    setSession(null)
    if (current) {
      // Best effort: the local session is already gone even if this request fails.
      await authApi.logout(current.refreshToken).catch(() => {})
    }
  }, [])

  const value = useMemo(
    () => ({ user: session?.user ?? null, login, register, logout }),
    [session, login, register, logout],
  )
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

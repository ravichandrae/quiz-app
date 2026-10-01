import { describe, expect, it } from 'vitest'
import { json, mockApi, problem, session } from '../test-utils'
import { apiFetch, ApiError, getSession, setSession } from './client'

describe('apiFetch', () => {
  it('sends the access token', async () => {
    setSession(session('STUDENT'))
    const fetchMock = mockApi({ 'GET /api/me': () => json({ id: 1 }) })

    await apiFetch('/me')

    const headers = new Headers(fetchMock.mock.calls[0][1]?.headers)
    expect(headers.get('Authorization')).toBe('Bearer access-1')
  })

  it('refreshes an expired access token once and retries', async () => {
    setSession(session('STUDENT'))
    const renewed = session('STUDENT', { accessToken: 'access-2', refreshToken: 'refresh-2' })
    const fetchMock = mockApi({
      'GET /api/me': (_url, init) =>
        new Headers(init.headers).get('Authorization') === 'Bearer access-2'
          ? json({ id: 1 })
          : problem(401, 'UNAUTHORIZED', ''),
      'POST /api/auth/refresh': () => json(renewed),
    })

    // Two calls at once must share one refresh, because each refresh token works only once.
    await Promise.all([apiFetch('/me'), apiFetch('/me')])

    expect(fetchMock.mock.calls.filter(([url]) => String(url).endsWith('/auth/refresh'))).toHaveLength(1)
    expect(getSession()?.refreshToken).toBe('refresh-2')
  })

  it('logs out when the refresh token is no longer valid', async () => {
    setSession(session('STUDENT'))
    mockApi({
      'GET /api/me': () => problem(401, 'UNAUTHORIZED', ''),
      'POST /api/auth/refresh': () => problem(401, 'SESSION_EXPIRED', 'Please log in again.'),
    })

    await expect(apiFetch('/me')).rejects.toMatchObject({ code: 'SESSION_EXPIRED' })
    expect(getSession()).toBeNull()
  })

  it('turns problem details into an ApiError with field errors', async () => {
    mockApi({
      'GET /api/thing': () => problem(400, 'VALIDATION_FAILED', 'Please check the details you entered.', { pin: 'PIN must be 4 to 6 digits' }),
    })

    const error = await apiFetch('/thing').catch((e: unknown) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect(error).toMatchObject({ status: 400, fieldErrors: { pin: 'PIN must be 4 to 6 digits' } })
  })

  it('reports a network failure in simple words', async () => {
    mockApi({ 'GET /api/me': () => Promise.reject(new TypeError('Failed to fetch')) })

    await expect(apiFetch('/me')).rejects.toThrow('No internet connection. Please try again.')
  })
})

import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import type { UserSummary } from '../api/admin'
import { json, mockApi, renderApp, signIn } from '../test-utils'

const asha: UserSummary = {
  id: 7,
  name: 'Asha',
  mobile: '9876543210',
  email: null,
  school: 'ZP High School',
  role: 'STUDENT',
  active: true,
  createdAt: '2026-09-30T10:00:00Z',
  lockedUntil: null,
}

function page(content: UserSummary[]) {
  return json({ content, page: 0, size: 20, totalElements: content.length, totalPages: content.length ? 1 : 0 })
}

describe('AdminUsersPage', () => {
  it('lists students and turns one off', async () => {
    signIn('ADMIN')
    const fetchMock = mockApi({
      'GET /api/admin/users': () => page([asha]),
      'PATCH /api/admin/users/7': () => json({ ...asha, active: false }),
    })
    renderApp('/admin/users')

    const row = (await screen.findByText('Asha')).closest('tr')!
    expect(within(row).getByText('ZP High School')).toBeInTheDocument()
    expect(within(row).getByText('Active')).toBeInTheDocument()

    await userEvent.click(within(row).getByRole('button', { name: 'Turn off Asha' }))

    expect(await within(row).findByText('Turned off')).toBeInTheDocument()
    const patch = fetchMock.mock.calls.find(([, init]) => init?.method === 'PATCH')
    expect(JSON.parse(String(patch?.[1]?.body))).toEqual({ active: false })
  })

  it('searches by name or mobile number', async () => {
    signIn('ADMIN')
    const fetchMock = mockApi({ 'GET /api/admin/users': () => page([]) })
    renderApp('/admin/users')
    await screen.findByText('No students found.')

    await userEvent.type(screen.getByRole('textbox', { name: 'Search by name or mobile number' }), 'asha')
    await userEvent.click(screen.getByRole('button', { name: 'Search' }))

    const last = String(fetchMock.mock.calls.at(-1)?.[0])
    expect(new URL(last, 'http://x').searchParams.get('q')).toBe('asha')
  })

  it('shows when a student is locked out', async () => {
    signIn('ADMIN')
    const lockedUntil = new Date(Date.now() + 10 * 60_000).toISOString()
    mockApi({ 'GET /api/admin/users': () => page([{ ...asha, lockedUntil }]) })
    renderApp('/admin/users')

    const row = (await screen.findByText('Asha')).closest('tr')!
    expect(within(row).getByText('Locked')).toBeInTheDocument()
  })

  it('resets a PIN and unlocks the student', async () => {
    signIn('ADMIN')
    const lockedUntil = new Date(Date.now() + 10 * 60_000).toISOString()
    const fetchMock = mockApi({
      'GET /api/admin/users': () => page([{ ...asha, lockedUntil }]),
      'POST /api/admin/users/7/reset-pin': () => json({ ...asha, lockedUntil: null }),
    })
    renderApp('/admin/users')
    const row = (await screen.findByText('Asha')).closest('tr')!

    await userEvent.click(within(row).getByRole('button', { name: 'Reset PIN for Asha' }))
    const panel = screen.getByRole('region', { name: 'Reset PIN for Asha' })
    await userEvent.type(within(panel).getByLabelText('New PIN'), '8642')
    await userEvent.type(within(panel).getByLabelText('Type the new PIN again'), '8642')
    await userEvent.click(within(panel).getByRole('button', { name: 'Save new PIN' }))

    expect(await within(panel).findByRole('status')).toHaveTextContent("Asha's PIN has been changed.")
    expect(within(row).getByText('Active')).toBeInTheDocument()
    const call = fetchMock.mock.calls.find(([url]) => String(url).endsWith('/reset-pin'))
    expect(JSON.parse(String(call?.[1]?.body))).toEqual({ pin: '8642' })

    await userEvent.click(within(panel).getByRole('button', { name: 'Done' }))
    expect(screen.queryByRole('region', { name: 'Reset PIN for Asha' })).not.toBeInTheDocument()
  })

  it('does not reset the PIN when the two PINs differ', async () => {
    signIn('ADMIN')
    const fetchMock = mockApi({ 'GET /api/admin/users': () => page([asha]) })
    renderApp('/admin/users')
    await userEvent.click(await screen.findByRole('button', { name: 'Reset PIN for Asha' }))

    await userEvent.type(screen.getByLabelText('New PIN'), '8642')
    await userEvent.type(screen.getByLabelText('Type the new PIN again'), '8643')
    await userEvent.click(screen.getByRole('button', { name: 'Save new PIN' }))

    expect(screen.getByText('The two PINs are not the same')).toBeInTheDocument()
    expect(fetchMock.mock.calls.some(([url]) => String(url).endsWith('/reset-pin'))).toBe(false)
  })
})

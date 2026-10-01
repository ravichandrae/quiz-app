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
})

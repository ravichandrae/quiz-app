import { screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { json, mockApi, renderApp, signIn } from './test-utils'

describe('routing', () => {
  it('sends logged-out visitors to the login page', () => {
    renderApp('/student')
    expect(screen.getByRole('heading', { name: 'Log in' })).toBeInTheDocument()
  })

  it('keeps students out of admin pages', () => {
    signIn('STUDENT')
    renderApp('/admin/users')
    expect(screen.getByRole('heading', { name: 'Hello, Asha!' })).toBeInTheDocument()
  })

  it('takes a logged-in admin from the login page to the student list', async () => {
    signIn('ADMIN')
    mockApi({ 'GET /api/admin/users': () => json({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }) })
    renderApp('/login')
    expect(screen.getByRole('heading', { name: 'Students' })).toBeInTheDocument()
    expect(await screen.findByText('No students found.')).toBeInTheDocument()
  })
})

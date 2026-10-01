import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { getSession } from '../api/client'
import { json, mockApi, problem, renderApp, session } from '../test-utils'

describe('LoginPage', () => {
  it('logs a student in with digits-only mobile number and opens their home page', async () => {
    const fetchMock = mockApi({ 'POST /api/auth/login': () => json(session('STUDENT')) })
    renderApp('/login')

    await userEvent.type(screen.getByLabelText('Mobile number'), '98765 43210')
    await userEvent.type(screen.getByLabelText('PIN'), '4321')
    await userEvent.click(screen.getByRole('button', { name: 'Log in' }))

    expect(await screen.findByRole('heading', { name: 'Hello, Asha!' })).toBeInTheDocument()
    expect(JSON.parse(String(fetchMock.mock.calls[0][1]?.body))).toEqual({ mobile: '9876543210', pin: '4321' })
    expect(getSession()?.user.name).toBe('Asha')
  })

  it('shows the server message for a wrong PIN', async () => {
    mockApi({
      'POST /api/auth/login': () => problem(401, 'INVALID_CREDENTIALS', 'Wrong mobile number or PIN. Please try again.'),
    })
    renderApp('/login')

    await userEvent.type(screen.getByLabelText('Mobile number'), '9876543210')
    await userEvent.type(screen.getByLabelText('PIN'), '0000')
    await userEvent.click(screen.getByRole('button', { name: 'Log in' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Wrong mobile number or PIN. Please try again.')
    expect(getSession()).toBeNull()
  })

  it('accepts only digits in the PIN box', async () => {
    renderApp('/login')
    const pin = screen.getByLabelText('PIN')

    await userEvent.type(pin, '12ab34')

    expect(pin).toHaveValue('1234')
  })
})

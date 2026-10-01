import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { json, mockApi, problem, renderApp, session } from '../test-utils'

async function fillForm({ pin = '4321', confirmPin = '4321' } = {}) {
  await userEvent.type(screen.getByLabelText('Your name'), 'Asha')
  await userEvent.type(screen.getByLabelText('Mobile number'), '9876543210')
  await userEvent.type(screen.getByLabelText('Choose a PIN'), pin)
  await userEvent.type(screen.getByLabelText('Type your PIN again'), confirmPin)
  await userEvent.click(screen.getByRole('button', { name: 'Create account' }))
}

describe('RegisterPage', () => {
  it('registers and opens the student home page', async () => {
    const fetchMock = mockApi({
      'POST /api/auth/register': () => json(session('STUDENT'), 201),
      'GET /api/me/quizzes': () => json([]),
    })
    renderApp('/register')

    await fillForm()

    expect(await screen.findByRole('heading', { name: 'Hello, Asha!' })).toBeInTheDocument()
    expect(JSON.parse(String(fetchMock.mock.calls[0][1]?.body))).toEqual({ name: 'Asha', mobile: '9876543210', pin: '4321' })
  })

  it('stops when the two PINs differ', async () => {
    const fetchMock = mockApi({})
    renderApp('/register')

    await fillForm({ confirmPin: '1234' })

    expect(screen.getByText('The two PINs are not the same')).toBeInTheDocument()
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('shows field errors from the server next to each field', async () => {
    mockApi({
      'POST /api/auth/register': () =>
        problem(400, 'VALIDATION_FAILED', 'Please check the details you entered.', { mobile: 'Mobile number must be 10 digits' }),
    })
    renderApp('/register')

    await fillForm()

    expect(await screen.findByText('Mobile number must be 10 digits')).toBeInTheDocument()
    expect(screen.getByLabelText('Mobile number')).toHaveAttribute('aria-invalid', 'true')
  })

  it('shows a clear message when the mobile number is taken', async () => {
    mockApi({
      'POST /api/auth/register': () =>
        problem(409, 'MOBILE_TAKEN', 'This mobile number is already registered. Please log in.'),
    })
    renderApp('/register')

    await fillForm()

    expect(await screen.findByRole('alert')).toHaveTextContent('This mobile number is already registered. Please log in.')
  })
})

import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import type { Question } from '../api/questions'
import { json, mockApi, problem, renderApp, signIn } from '../test-utils'

const capital: Question = {
  id: 5,
  text: 'What is the capital of India?',
  options: ['Mumbai', 'Delhi', 'Kolkata', 'Chennai'],
  correctOption: 1,
  timeLimitSeconds: 45,
  createdAt: '2026-10-01T10:00:00Z',
  updatedAt: '2026-10-01T10:00:00Z',
}

function page(content: Question[]) {
  return json({ content, page: 0, size: 20, totalElements: content.length, totalPages: content.length ? 1 : 0 })
}

function bodyOf(call: unknown[] | undefined) {
  return JSON.parse(String((call?.[1] as RequestInit | undefined)?.body))
}

describe('question bank', () => {
  it('lists questions with their correct answer and time', async () => {
    signIn('ADMIN')
    mockApi({ 'GET /api/admin/questions': () => page([capital]) })
    renderApp('/admin/questions')

    const row = (await screen.findByText('What is the capital of India?')).closest('tr')!
    expect(within(row).getByText('B. Delhi')).toBeInTheDocument()
    expect(within(row).getByText('45 sec')).toBeInTheDocument()
  })

  it('asks before deleting and explains when a quiz still uses the question', async () => {
    signIn('ADMIN')
    const fetchMock = mockApi({
      'GET /api/admin/questions': () => page([capital]),
      'DELETE /api/admin/questions/5': () =>
        problem(409, 'QUESTION_IN_USE', 'This question is used in the quiz "Geography". Remove it from the quiz first.'),
    })
    renderApp('/admin/questions')

    await userEvent.click(await screen.findByRole('button', { name: 'Delete question 1' }))
    expect(fetchMock.mock.calls.some(([, init]) => init?.method === 'DELETE')).toBe(false)
    await userEvent.click(screen.getByRole('button', { name: 'Yes, delete' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('used in the quiz "Geography"')
  })

  it('adds a question', async () => {
    signIn('ADMIN')
    const fetchMock = mockApi({
      'POST /api/admin/questions': () => json(capital, 201),
      'GET /api/admin/questions': () => page([capital]),
    })
    renderApp('/admin/questions/new')

    await userEvent.type(screen.getByLabelText('Question'), 'What is the capital of India?')
    for (const [letter, answer] of [['A', 'Mumbai'], ['B', 'Delhi'], ['C', 'Kolkata'], ['D', 'Chennai']]) {
      await userEvent.type(screen.getByRole('textbox', { name: `Answer ${letter}` }), answer)
    }
    await userEvent.click(screen.getByRole('radio', { name: 'Answer B is correct' }))
    await userEvent.selectOptions(screen.getByLabelText('Time to answer'), '45 sec')
    await userEvent.click(screen.getByRole('button', { name: 'Save question' }))

    expect(await screen.findByRole('status')).toHaveTextContent('Question added.')
    const post = fetchMock.mock.calls.find(([, init]) => init?.method === 'POST')
    expect(bodyOf(post)).toEqual({
      text: 'What is the capital of India?',
      options: ['Mumbai', 'Delhi', 'Kolkata', 'Chennai'],
      correctOption: 1,
      timeLimitSeconds: 45,
    })
  })

  it('requires choosing the correct answer before saving', async () => {
    signIn('ADMIN')
    const fetchMock = mockApi({})
    renderApp('/admin/questions/new')

    await userEvent.click(screen.getByRole('button', { name: 'Save question' }))

    expect(screen.getByText('Please choose the correct answer')).toBeInTheDocument()
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('shows server errors next to the answer they belong to', async () => {
    signIn('ADMIN')
    mockApi({
      'PUT /api/admin/questions/5': () =>
        problem(400, 'VALIDATION_FAILED', 'Please check the details you entered.', { 'options[2]': 'Each answer must be different' }),
      'GET /api/admin/questions/5': () => json(capital),
    })
    renderApp('/admin/questions/5')

    const answerC = await screen.findByRole('textbox', { name: 'Answer C' })
    expect(answerC).toHaveValue('Kolkata')
    await userEvent.click(screen.getByRole('button', { name: 'Save question' }))

    expect(await screen.findByText('Each answer must be different')).toBeInTheDocument()
    expect(answerC).toHaveAttribute('aria-invalid', 'true')
  })
})

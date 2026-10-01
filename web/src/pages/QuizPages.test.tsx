import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import type { Question } from '../api/questions'
import type { QuizDetail, QuizSummary } from '../api/quizzes'
import { json, mockApi, renderApp, signIn } from '../test-utils'

function question(id: number, text: string, timeLimitSeconds = 30): Question {
  return {
    id,
    text,
    options: ['A', 'B', 'C', 'D'],
    correctOption: 0,
    timeLimitSeconds,
    createdAt: '2026-10-01T10:00:00Z',
    updatedAt: '2026-10-01T10:00:00Z',
  }
}

function page<T>(content: T[]) {
  return json({ content, page: 0, size: 20, totalElements: content.length, totalPages: content.length ? 1 : 0 })
}

function bodyOf(call: unknown[] | undefined) {
  return JSON.parse(String((call?.[1] as RequestInit | undefined)?.body))
}

const bank = [question(1, 'Plants need?', 30), question(2, 'Sun is a?', 60), question(3, 'Water boils at?', 45)]

describe('quizzes', () => {
  it('lists quizzes with their time', async () => {
    signIn('ADMIN')
    const quizzes: QuizSummary[] = [
      { id: 1, title: 'Science', questionCount: 3, questionTimeSeconds: 135, totalTimeLimitSeconds: null, showAnswers: true, updatedAt: '' },
      { id: 2, title: 'Maths', questionCount: 10, questionTimeSeconds: 600, totalTimeLimitSeconds: 300, showAnswers: false, updatedAt: '' },
    ]
    mockApi({ 'GET /api/admin/quizzes': () => page(quizzes) })
    renderApp('/admin/quizzes')

    const science = (await screen.findByText('Science')).closest('tr')!
    expect(within(science).getByText('Up to 2 min 15 sec')).toBeInTheDocument()
    const maths = screen.getByText('Maths').closest('tr')!
    expect(within(maths).getByText('5 min')).toBeInTheDocument()
    expect(within(maths).getByText('No')).toBeInTheDocument()
  })

  it('builds a quiz from the question bank in the chosen order', async () => {
    signIn('ADMIN')
    const fetchMock = mockApi({
      'GET /api/admin/questions': () => page(bank),
      'POST /api/admin/quizzes': () => json({ id: 9 }, 201),
      'GET /api/admin/quizzes': () => page([]),
    })
    renderApp('/admin/quizzes/new')

    await userEvent.type(screen.getByLabelText('Quiz title'), 'Science Week 1')
    await userEvent.click(await screen.findByRole('button', { name: 'Add: Plants need?' }))
    await userEvent.click(screen.getByRole('button', { name: 'Add: Water boils at?' }))
    expect(screen.getByRole('button', { name: 'Already added: Plants need?' })).toBeDisabled()
    expect(screen.getByText('2 questions · up to 1 min 15 sec')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Move question 2 up' }))
    await userEvent.click(screen.getByLabelText('Set a time limit for the whole quiz'))
    await userEvent.clear(screen.getByLabelText('Minutes for the whole quiz'))
    await userEvent.type(screen.getByLabelText('Minutes for the whole quiz'), '5')
    await userEvent.click(screen.getByLabelText('Show students the correct answers after they finish'))
    await userEvent.click(screen.getByRole('button', { name: 'Save quiz' }))

    expect(await screen.findByRole('status')).toHaveTextContent('Quiz created.')
    const post = fetchMock.mock.calls.find(([, init]) => init?.method === 'POST')
    expect(bodyOf(post)).toEqual({
      title: 'Science Week 1',
      totalTimeLimitSeconds: 300,
      showAnswers: false,
      questionIds: [3, 1],
    })
  })

  it('warns when the quiz time is shorter than its questions', async () => {
    signIn('ADMIN')
    const quiz: QuizDetail = {
      id: 4,
      title: 'Long quiz',
      totalTimeLimitSeconds: 60,
      showAnswers: true,
      questionTimeSeconds: 135,
      questions: bank,
      createdAt: '',
      updatedAt: '',
    }
    mockApi({
      'GET /api/admin/quizzes/4': () => json(quiz),
      'GET /api/admin/questions': () => page(bank),
    })
    renderApp('/admin/quizzes/4')

    expect(await screen.findByDisplayValue('Long quiz')).toBeInTheDocument()
    expect(screen.getByLabelText('Minutes for the whole quiz')).toHaveValue(1)
    expect(screen.getByText(/questions add up to 2 min 15 sec/)).toBeInTheDocument()
  })

  it('removes a question from the quiz before saving', async () => {
    signIn('ADMIN')
    const quiz: QuizDetail = {
      id: 4,
      title: 'Edit me',
      totalTimeLimitSeconds: null,
      showAnswers: true,
      questionTimeSeconds: 135,
      questions: bank,
      createdAt: '',
      updatedAt: '',
    }
    const fetchMock = mockApi({
      'GET /api/admin/quizzes/4': () => json(quiz),
      'GET /api/admin/questions': () => page(bank),
      'PUT /api/admin/quizzes/4': () => json(quiz),
      'GET /api/admin/quizzes': () => page([]),
    })
    renderApp('/admin/quizzes/4')

    await userEvent.click(await screen.findByRole('button', { name: 'Remove question 2' }))
    await userEvent.click(screen.getByRole('button', { name: 'Save quiz' }))

    await screen.findByRole('status')
    const put = fetchMock.mock.calls.find(([, init]) => init?.method === 'PUT')
    expect(bodyOf(put)).toMatchObject({ totalTimeLimitSeconds: null, questionIds: [1, 3] })
  })
})

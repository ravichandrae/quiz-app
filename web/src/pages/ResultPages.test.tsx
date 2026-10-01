import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import type { AttemptDetail, AttemptRow, MyReview, ReviewQuestion } from '../api/results'
import { json, mockApi, renderApp, signIn } from '../test-utils'

const questions: ReviewQuestion[] = [
  { position: 1, text: 'What gives us light?', options: ['Sun', 'Moon', 'Star', 'Cloud'], selectedOption: 0, correctOption: 0, outcome: 'CORRECT' },
  { position: 2, text: 'What shines at night?', options: ['Sun', 'Moon', 'Star', 'Cloud'], selectedOption: 3, correctOption: 1, outcome: 'WRONG' },
  { position: 3, text: 'Which is wet?', options: ['Rain', 'Sand', 'Rock', 'Dust'], selectedOption: null, correctOption: 0, outcome: 'NO_ANSWER' },
]

const review: MyReview = {
  attemptId: 77,
  quizId: 1,
  quizTitle: 'Science Week 1',
  finishedAt: '2026-10-01T10:00:00Z',
  score: 1,
  questionCount: 3,
  percentage: 33,
  finishReason: 'ALL_ANSWERED',
  answersShown: true,
  questions,
}

function row(overrides: Partial<AttemptRow> = {}): AttemptRow {
  return {
    id: 77,
    studentId: 1,
    studentName: 'Asha',
    studentMobile: '9876543210',
    quizId: 9,
    quizTitle: 'Science Week 1',
    status: 'COMPLETED',
    score: 8,
    questionCount: 10,
    percentage: 80,
    startedAt: '2026-10-01T10:00:00Z',
    finishedAt: '2026-10-01T10:05:00Z',
    late: false,
    ...overrides,
  }
}

function page<T>(content: T[]) {
  return json({ content, page: 0, size: 20, totalElements: content.length, totalPages: content.length ? 1 : 0 })
}

describe('student results', () => {
  it('lists finished quizzes in My scores', async () => {
    signIn('STUDENT')
    mockApi({
      'GET /api/me/results': () =>
        json([{ attemptId: 77, quizId: 1, quizTitle: 'Science Week 1', finishedAt: '2026-10-01T10:00:00Z', score: 8, questionCount: 10, percentage: 80 }]),
    })
    renderApp('/student/results')

    const card = (await screen.findByRole('heading', { name: 'Science Week 1' })).closest('li')!
    expect(within(card).getByText('80%')).toBeInTheDocument()
    expect(within(card).getByText('Score: 8 out of 10')).toBeInTheDocument()
    expect(within(card).getByRole('link', { name: 'See results for Science Week 1' })).toHaveAttribute('href', '/student/results/77')
    expect(screen.getByRole('link', { name: 'My scores' })).toHaveAttribute('aria-current', 'page')
    expect(screen.getByRole('link', { name: 'My quizzes' })).not.toHaveAttribute('aria-current')
  })

  it('shows each answer marked right or wrong with the correct answer', async () => {
    signIn('STUDENT')
    mockApi({ 'GET /api/me/results/77': () => json(review) })
    renderApp('/student/results/77')

    expect(await screen.findByLabelText('You got 1 out of 3')).toBeInTheDocument()
    const answers = within(screen.getByRole('list', { name: 'Your answers' }))
    const [first, second, third] = answers.getAllByRole('listitem').filter((li) => li.classList.contains('result-question'))
    expect(within(first).getByText('Right')).toBeInTheDocument()
    expect(within(first).getByText('(your answer, correct answer)')).toBeInTheDocument()
    expect(within(second).getByText('Wrong')).toBeInTheDocument()
    expect(within(second).getByText('Cloud').closest('li')).toHaveTextContent('(your answer)')
    expect(within(second).getByText('Moon').closest('li')).toHaveTextContent('(correct answer)')
    expect(within(third).getByText('Time ran out')).toBeInTheDocument()
  })

  it('shows only the score when the teacher hides the answers', async () => {
    signIn('STUDENT')
    mockApi({ 'GET /api/me/results/77': () => json({ ...review, answersShown: false, questions: [] }) })
    renderApp('/student/results/77')

    expect(await screen.findByText('Your teacher will go through the answers with you.')).toBeInTheDocument()
    expect(screen.queryByRole('list', { name: 'Your answers' })).not.toBeInTheDocument()
  })
})

describe('admin results', () => {
  it('filters by the quiz in the address and marks unfinished and late attempts', async () => {
    signIn('ADMIN')
    const fetchMock = mockApi({
      'GET /api/admin/attempts': () =>
        page([row(), row({ id: 78, studentName: 'Ravi', status: 'IN_PROGRESS', finishedAt: null, late: true })]),
      'GET /api/admin/quizzes': () => page([{ id: 9, title: 'Science Week 1' }]),
    })
    renderApp('/admin/results?quizId=9')

    const asha = (await screen.findByText('Asha')).closest('tr')!
    expect(within(asha).getByText('(80%)')).toBeInTheDocument()
    const ravi = screen.getByText('Ravi').closest('tr')!
    expect(within(ravi).getByText('Not finished')).toBeInTheDocument()
    expect(within(ravi).getByText('Late')).toBeInTheDocument()
    const listCall = fetchMock.mock.calls.find(([url]) => String(url).startsWith('/api/admin/attempts?'))
    expect(new URL(String(listCall?.[0]), 'http://x').searchParams.get('quizId')).toBe('9')
    expect(await screen.findByRole('combobox', { name: 'Quiz' })).toHaveValue('9')
  })

  it('downloads the filtered results as CSV', async () => {
    signIn('ADMIN')
    const createObjectURL = vi.fn(() => 'blob:results')
    vi.stubGlobal('URL', Object.assign(URL, { createObjectURL, revokeObjectURL: vi.fn() }))
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
    const fetchMock = mockApi({
      'GET /api/admin/attempts/export': () => new Response('"Student name"\r\n', { headers: { 'Content-Type': 'text/csv' } }),
      'GET /api/admin/attempts': () => page([row()]),
      'GET /api/admin/quizzes': () => page([]),
    })
    renderApp('/admin/results?quizId=9&q=asha')

    await userEvent.click(await screen.findByRole('button', { name: 'Download CSV' }))

    await vi.waitFor(() => expect(click).toHaveBeenCalled())
    const exportCall = fetchMock.mock.calls.find(([url]) => String(url).startsWith('/api/admin/attempts/export'))
    const params = new URL(String(exportCall?.[0]), 'http://x').searchParams
    expect([params.get('quizId'), params.get('q')]).toEqual(['9', 'asha'])
    expect(new Headers(exportCall?.[1]?.headers).get('Authorization')).toBe('Bearer access-1')
  })

  it("shows a student's answers with the time taken", async () => {
    signIn('ADMIN')
    const detail: AttemptDetail = {
      attempt: row({ score: 1, questionCount: 3, percentage: 33 }),
      studentSchool: 'ZP School',
      dueAt: null,
      totalTimeLimitSeconds: null,
      finishReason: 'ALL_ANSWERED',
      questions: questions.map((q, i) => ({ ...q, timeLimitSeconds: 30, secondsTaken: [4, 12, 30][i] })),
    }
    mockApi({ 'GET /api/admin/attempts/77': () => json(detail) })
    renderApp('/admin/results/77')

    expect(await screen.findByRole('heading', { name: 'Asha · Science Week 1' })).toBeInTheDocument()
    expect(screen.getByText('1 / 3 (33%)')).toBeInTheDocument()
    expect(screen.getByText('ZP School')).toBeInTheDocument()
    expect(screen.getByText('Time taken: 4 sec of 30 sec')).toBeInTheDocument()
    expect(screen.getByText('Time taken: 30 sec of 30 sec')).toBeInTheDocument()
  })
})

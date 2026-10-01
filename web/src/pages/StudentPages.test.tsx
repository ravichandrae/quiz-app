import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import type { AttemptState, MyQuiz } from '../api/attempts'
import { json, mockApi, problem, renderApp, signIn } from '../test-utils'

function myQuiz(overrides: Partial<MyQuiz> = {}): MyQuiz {
  return {
    quizId: 1,
    title: 'Science Week 1',
    questionCount: 2,
    questionTimeSeconds: 60,
    totalTimeLimitSeconds: null,
    assignedAt: '',
    dueAt: null,
    status: 'NEW',
    attemptId: null,
    score: null,
    ...overrides,
  }
}

function questionState(position: number, text: string, secondsLeft = 30): AttemptState {
  return {
    attemptId: 77,
    quizTitle: 'Science Week 1',
    status: 'IN_PROGRESS',
    questionCount: 2,
    question: { position, text, options: ['Sun', 'Moon', 'Star', 'Cloud'], timeLimitSeconds: 30, secondsLeft },
    quizSecondsLeft: null,
    result: null,
  }
}

const finished: AttemptState = {
  attemptId: 77,
  quizTitle: 'Science Week 1',
  status: 'COMPLETED',
  questionCount: 2,
  question: null,
  quizSecondsLeft: null,
  result: { score: 1, questionCount: 2, percentage: 50, finishReason: 'ALL_ANSWERED' },
}

function bodyOf(call: unknown[] | undefined) {
  return JSON.parse(String((call?.[1] as RequestInit | undefined)?.body))
}

describe('student dashboard', () => {
  it('shows each quiz with what the student can do next', async () => {
    signIn('STUDENT')
    mockApi({
      'GET /api/me/quizzes': () =>
        json([
          myQuiz({ quizId: 1, title: 'Science', questionCount: 10, questionTimeSeconds: 450, dueAt: '2099-10-05T18:29:59Z' }),
          myQuiz({ quizId: 2, title: 'Maths', questionCount: 1, totalTimeLimitSeconds: 300, status: 'IN_PROGRESS', attemptId: 5 }),
          myQuiz({ quizId: 3, title: 'History', questionCount: 10, status: 'COMPLETED', attemptId: 6, score: 8 }),
        ]),
    })
    renderApp('/student')

    expect(await screen.findByRole('status')).toHaveTextContent('You have 1 new quiz!')
    const science = screen.getByRole('heading', { name: 'Science' }).closest('li')!
    expect(within(science).getByText('New')).toBeInTheDocument()
    expect(within(science).getByText(/Up to 7 min 30 sec/)).toBeInTheDocument()
    expect(within(science).getByText(/Finish by/)).toBeInTheDocument()
    expect(within(science).getByRole('link', { name: 'Start Science' })).toHaveAttribute('href', '/student/quizzes/1')

    const maths = screen.getByRole('heading', { name: 'Maths' }).closest('li')!
    expect(within(maths).getByText(/1 question$/)).toBeInTheDocument()
    expect(within(maths).getByRole('link', { name: 'Continue Maths' })).toBeInTheDocument()

    const history = screen.getByRole('heading', { name: 'History' }).closest('li')!
    expect(within(history).getByText('Your score: 8 out of 10')).toBeInTheDocument()
    expect(within(history).getByRole('link', { name: 'See results for History' })).toHaveAttribute('href', '/student/results/6')
  })

  it('says when there are no quizzes', async () => {
    signIn('STUDENT')
    mockApi({ 'GET /api/me/quizzes': () => json([]) })
    renderApp('/student')

    expect(await screen.findByText('You have no quizzes yet. Your teacher will add them soon.')).toBeInTheDocument()
  })
})

describe('taking a quiz', () => {
  it('goes from the introduction through each question to the score', async () => {
    signIn('STUDENT')
    const fetchMock = mockApi({
      'GET /api/me/quizzes/1': () => json(myQuiz()),
      'POST /api/me/quizzes/1/attempt': () => json(questionState(1, 'What gives us light in the day?')),
      'POST /api/me/attempts/77/answers': (_url, init) =>
        bodyOf([null, init]).position === 1 ? json(questionState(2, 'What shines at night?')) : json(finished),
      'GET /api/me/quizzes': () => json([]),
    })
    renderApp('/student/quizzes/1')

    expect(await screen.findByText(/You cannot go back to a question/)).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Start quiz' }))

    expect(await screen.findByRole('heading', { name: 'What gives us light in the day?' })).toHaveFocus()
    expect(screen.getByText('Question 1 of 2')).toBeInTheDocument()
    expect(screen.getByRole('timer')).toHaveAccessibleName('30 seconds left')
    const submit = screen.getByRole('button', { name: 'Submit' })
    expect(submit).toBeDisabled()
    await userEvent.click(screen.getByRole('radio', { name: 'Sun' }))
    await userEvent.click(submit)

    expect(await screen.findByRole('heading', { name: 'What shines at night?' })).toBeInTheDocument()
    // The new question starts with nothing selected.
    expect(screen.getByRole('radio', { name: 'Sun' })).not.toBeChecked()
    await userEvent.click(screen.getByRole('radio', { name: 'Cloud' }))
    await userEvent.click(screen.getByRole('button', { name: 'Submit' }))

    expect(await screen.findByRole('heading', { name: 'Well done!' })).toBeInTheDocument()
    expect(screen.getByLabelText('You got 1 out of 2')).toBeInTheDocument()
    expect(screen.getByText('50%')).toBeInTheDocument()
    const answers = fetchMock.mock.calls.filter(([url]) => String(url).endsWith('/answers')).map((c) => bodyOf(c))
    expect(answers).toEqual([
      { position: 1, selectedOption: 0 },
      { position: 2, selectedOption: 3 },
    ])
  })

  it('moves on without an answer when the time runs out', async () => {
    signIn('STUDENT')
    const fetchMock = mockApi({
      'GET /api/me/quizzes/1': () => json(myQuiz()),
      'POST /api/me/quizzes/1/attempt': () => json(questionState(1, 'Quick one', 1)),
      'POST /api/me/attempts/77/answers': () => json(questionState(2, 'Next one')),
    })
    renderApp('/student/quizzes/1')
    await userEvent.click(await screen.findByRole('button', { name: 'Start quiz' }))
    // Choosing without submitting does not count.
    await userEvent.click(await screen.findByRole('radio', { name: 'Moon' }))

    expect(await screen.findByRole('heading', { name: 'Next one' }, { timeout: 3000 })).toBeInTheDocument()
    const call = fetchMock.mock.calls.find(([url]) => String(url).endsWith('/answers'))
    expect(bodyOf(call)).toEqual({ position: 1, selectedOption: null })
  })

  it('resumes a quiz in progress straight away', async () => {
    signIn('STUDENT')
    mockApi({
      'GET /api/me/quizzes/1': () => json(myQuiz({ status: 'IN_PROGRESS', attemptId: 77 })),
      'POST /api/me/quizzes/1/attempt': () => json(questionState(2, 'Where we left off', 12)),
    })
    renderApp('/student/quizzes/1')

    expect(await screen.findByRole('heading', { name: 'Where we left off' })).toBeInTheDocument()
    expect(screen.getByText('Question 2 of 2')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Start quiz' })).not.toBeInTheDocument()
  })

  it('keeps the chosen answer and offers to try again when the network fails', async () => {
    signIn('STUDENT')
    let online = false
    mockApi({
      'GET /api/me/quizzes/1': () => json(myQuiz()),
      'POST /api/me/quizzes/1/attempt': () => json(questionState(1, 'First')),
      'POST /api/me/attempts/77/answers': () =>
        online ? json(questionState(2, 'Second')) : Promise.reject(new TypeError('Failed to fetch')),
    })
    renderApp('/student/quizzes/1')
    await userEvent.click(await screen.findByRole('button', { name: 'Start quiz' }))
    await userEvent.click(await screen.findByRole('radio', { name: 'Star' }))
    await userEvent.click(screen.getByRole('button', { name: 'Submit' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('No internet connection. Please try again.')
    expect(screen.getByRole('radio', { name: 'Star' })).toBeChecked()

    online = true
    await userEvent.click(screen.getByRole('button', { name: 'Try again' }))
    expect(await screen.findByRole('heading', { name: 'Second' })).toBeInTheDocument()
  })

  it('shows where the server is when it has already moved on', async () => {
    signIn('STUDENT')
    mockApi({
      'GET /api/me/quizzes/1': () => json(myQuiz()),
      'POST /api/me/quizzes/1/attempt': () => json(questionState(1, 'First')),
      'POST /api/me/attempts/77/answers': () => problem(409, 'QUESTION_NOT_OPEN', 'This question is not open yet.'),
      'GET /api/me/attempts/77': () => json(finished),
    })
    renderApp('/student/quizzes/1')
    await userEvent.click(await screen.findByRole('button', { name: 'Start quiz' }))
    await userEvent.click(await screen.findByRole('radio', { name: 'Sun' }))
    await userEvent.click(screen.getByRole('button', { name: 'Submit' }))

    expect(await screen.findByRole('heading', { name: 'Well done!' })).toBeInTheDocument()
  })

  it('does not restart a finished quiz', async () => {
    signIn('STUDENT')
    mockApi({ 'GET /api/me/quizzes/1': () => json(myQuiz({ status: 'COMPLETED', attemptId: 77, score: 2 })) })
    renderApp('/student/quizzes/1')

    expect(await screen.findByText('You have finished this quiz. Your score: 2 out of 2.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Start quiz' })).not.toBeInTheDocument()
  })

  it('warns that a quiz is past its due date', async () => {
    signIn('STUDENT')
    mockApi({ 'GET /api/me/quizzes/1': () => json(myQuiz({ dueAt: '2020-01-01T00:00:00Z' })) })
    renderApp('/student/quizzes/1')

    expect(await screen.findByText('This quiz was due earlier. You can still take it.')).toBeInTheDocument()
  })
})

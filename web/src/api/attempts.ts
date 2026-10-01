import { apiFetch } from './client'

export type MyQuizStatus = 'NEW' | 'IN_PROGRESS' | 'COMPLETED'

/** A quiz on a student's dashboard. `score` is set once the quiz is completed. */
export interface MyQuiz {
  quizId: number
  title: string
  questionCount: number
  questionTimeSeconds: number
  totalTimeLimitSeconds: number | null
  assignedAt: string
  dueAt: string | null
  status: MyQuizStatus
  attemptId: number | null
  score: number | null
}

export interface CurrentQuestion {
  /** 1-based. */
  position: number
  text: string
  options: string[]
  timeLimitSeconds: number
  secondsLeft: number
}

export interface AttemptResult {
  score: number
  questionCount: number
  percentage: number
  finishReason: 'ALL_ANSWERED' | 'TIME_UP'
}

/** Where the student is in a quiz: the question to answer now, or the result once it is over. */
export interface AttemptState {
  attemptId: number
  quizTitle: string
  status: 'IN_PROGRESS' | 'COMPLETED'
  questionCount: number
  question: CurrentQuestion | null
  quizSecondsLeft: number | null
  result: AttemptResult | null
}

export function listMyQuizzes(): Promise<MyQuiz[]> {
  return apiFetch('/me/quizzes')
}

export function getMyQuiz(quizId: number): Promise<MyQuiz> {
  return apiFetch(`/me/quizzes/${quizId}`)
}

/** Starts the quiz, or resumes the attempt in progress. */
export function startAttempt(quizId: number): Promise<AttemptState> {
  return apiFetch(`/me/quizzes/${quizId}/attempt`, { method: 'POST' })
}

export function getAttempt(attemptId: number): Promise<AttemptState> {
  return apiFetch(`/me/attempts/${attemptId}`)
}

/** `selectedOption` is null when time ran out without an answer. */
export function submitAnswer(attemptId: number, position: number, selectedOption: number | null): Promise<AttemptState> {
  return apiFetch(`/me/attempts/${attemptId}/answers`, {
    method: 'POST',
    body: JSON.stringify({ position, selectedOption }),
  })
}

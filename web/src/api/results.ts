import type { Page } from './admin'
import { apiDownload, apiFetch } from './client'

export type Outcome = 'CORRECT' | 'WRONG' | 'NO_ANSWER' | 'NOT_REACHED'

/** A finished quiz in the student's score history. */
export interface MyResult {
  attemptId: number
  quizId: number
  quizTitle: string
  finishedAt: string
  score: number
  questionCount: number
  percentage: number
}

export interface ReviewQuestion {
  position: number
  text: string
  options: string[]
  selectedOption: number | null
  correctOption: number
  outcome: Outcome
}

/** A finished attempt as the student sees it; `questions` is empty when the quiz hides answers. */
export interface MyReview {
  attemptId: number
  quizId: number
  quizTitle: string
  finishedAt: string
  score: number
  questionCount: number
  percentage: number
  finishReason: 'ALL_ANSWERED' | 'TIME_UP'
  answersShown: boolean
  questions: ReviewQuestion[]
}

export interface AttemptRow {
  id: number
  studentId: number
  studentName: string
  studentMobile: string
  quizId: number
  quizTitle: string
  status: 'IN_PROGRESS' | 'COMPLETED'
  score: number
  questionCount: number
  percentage: number
  startedAt: string
  finishedAt: string | null
  late: boolean
}

export interface DetailQuestion extends ReviewQuestion {
  timeLimitSeconds: number
  secondsTaken: number | null
}

export interface AttemptDetail {
  attempt: AttemptRow
  studentSchool: string | null
  dueAt: string | null
  totalTimeLimitSeconds: number | null
  finishReason: 'ALL_ANSWERED' | 'TIME_UP' | null
  questions: DetailQuestion[]
}

export interface AttemptFilter {
  quizId?: number
  q?: string
}

function filterParams({ quizId, q }: AttemptFilter): URLSearchParams {
  const params = new URLSearchParams()
  if (quizId) params.set('quizId', String(quizId))
  if (q) params.set('q', q)
  return params
}

export function listMyResults(): Promise<MyResult[]> {
  return apiFetch('/me/results')
}

export function getMyReview(attemptId: number): Promise<MyReview> {
  return apiFetch(`/me/results/${attemptId}`)
}

export function listAttempts(filter: AttemptFilter & { page?: number; size?: number }): Promise<Page<AttemptRow>> {
  const params = filterParams(filter)
  params.set('page', String(filter.page ?? 0))
  params.set('size', String(filter.size ?? 20))
  return apiFetch(`/admin/attempts?${params}`)
}

export function getAttemptDetail(attemptId: number): Promise<AttemptDetail> {
  return apiFetch(`/admin/attempts/${attemptId}`)
}

export function downloadAttemptsCsv(filter: AttemptFilter): Promise<void> {
  return apiDownload(`/admin/attempts/export?${filterParams(filter)}`, 'quiz-results.csv')
}

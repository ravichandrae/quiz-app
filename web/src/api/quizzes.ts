import type { Page } from './admin'
import { apiFetch } from './client'
import type { Question } from './questions'

export interface QuizSummary {
  id: number
  title: string
  questionCount: number
  questionTimeSeconds: number
  totalTimeLimitSeconds: number | null
  showAnswers: boolean
  updatedAt: string
}

export interface QuizDetail {
  id: number
  title: string
  totalTimeLimitSeconds: number | null
  showAnswers: boolean
  questionTimeSeconds: number
  questions: Question[]
  createdAt: string
  updatedAt: string
}

export interface QuizInput {
  title: string
  totalTimeLimitSeconds: number | null
  showAnswers: boolean
  questionIds: number[]
}

export function listQuizzes({ q, page = 0, size = 20 }: { q?: string; page?: number; size?: number }): Promise<Page<QuizSummary>> {
  const params = new URLSearchParams({ page: String(page), size: String(size) })
  if (q) params.set('q', q)
  return apiFetch(`/admin/quizzes?${params}`)
}

export function getQuiz(id: number): Promise<QuizDetail> {
  return apiFetch(`/admin/quizzes/${id}`)
}

export function createQuiz(input: QuizInput): Promise<QuizDetail> {
  return apiFetch('/admin/quizzes', { method: 'POST', body: JSON.stringify(input) })
}

export function updateQuiz(id: number, input: QuizInput): Promise<QuizDetail> {
  return apiFetch(`/admin/quizzes/${id}`, { method: 'PUT', body: JSON.stringify(input) })
}

export function deleteQuiz(id: number): Promise<void> {
  return apiFetch(`/admin/quizzes/${id}`, { method: 'DELETE' })
}

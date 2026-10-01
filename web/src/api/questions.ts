import type { Page } from './admin'
import { apiFetch } from './client'

export interface Question {
  id: number
  text: string
  options: string[]
  correctOption: number
  timeLimitSeconds: number
  createdAt: string
  updatedAt: string
}

export interface QuestionInput {
  text: string
  options: string[]
  correctOption: number
  timeLimitSeconds: number
}

export const TIME_LIMIT_CHOICES = [30, 45, 60, 75, 90, 105, 120]
export const OPTION_LABELS = ['A', 'B', 'C', 'D']

export function listQuestions({ q, page = 0, size = 20 }: { q?: string; page?: number; size?: number }): Promise<Page<Question>> {
  const params = new URLSearchParams({ page: String(page), size: String(size) })
  if (q) params.set('q', q)
  return apiFetch(`/admin/questions?${params}`)
}

export function getQuestion(id: number): Promise<Question> {
  return apiFetch(`/admin/questions/${id}`)
}

export function createQuestion(input: QuestionInput): Promise<Question> {
  return apiFetch('/admin/questions', { method: 'POST', body: JSON.stringify(input) })
}

export function updateQuestion(id: number, input: QuestionInput): Promise<Question> {
  return apiFetch(`/admin/questions/${id}`, { method: 'PUT', body: JSON.stringify(input) })
}

export function deleteQuestion(id: number): Promise<void> {
  return apiFetch(`/admin/questions/${id}`, { method: 'DELETE' })
}

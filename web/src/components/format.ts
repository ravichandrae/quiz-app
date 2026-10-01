import { ApiError } from '../api/client'

/** "45 sec", "2 min", "1 min 30 sec". */
export function formatDuration(totalSeconds: number): string {
  const minutes = Math.floor(totalSeconds / 60)
  const seconds = totalSeconds % 60
  if (minutes === 0) return `${seconds} sec`
  return seconds === 0 ? `${minutes} min` : `${minutes} min ${seconds} sec`
}

export interface FormError {
  /** Messages for individual fields, keyed by field name. */
  fieldErrors: Record<string, string>
  /** A message for the whole form, when the error is not about a particular field. */
  message: string | null
}

/** Splits an error from the API into per-field messages and a general message. */
export function toFormError(err: unknown, fallback: string): FormError {
  if (!(err instanceof ApiError)) return { fieldErrors: {}, message: fallback }
  const hasFieldErrors = Object.keys(err.fieldErrors).length > 0
  return { fieldErrors: err.fieldErrors, message: hasFieldErrors ? null : err.message }
}

export function errorMessage(err: unknown, fallback: string): string {
  return err instanceof ApiError ? err.message : fallback
}

/** "5 min" for a quiz with an overall limit, otherwise "Up to 2 min 30 sec" (the questions' total). */
export function quizTimeText(quiz: { totalTimeLimitSeconds: number | null; questionTimeSeconds: number }): string {
  return quiz.totalTimeLimitSeconds === null
    ? `Up to ${formatDuration(quiz.questionTimeSeconds)}`
    : formatDuration(quiz.totalTimeLimitSeconds)
}

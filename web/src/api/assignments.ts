import { apiFetch } from './client'

export type TargetType = 'STUDENT' | 'GROUP' | 'ALL'

export interface Assignment {
  id: number
  targetType: TargetType
  studentId: number | null
  studentName: string | null
  studentMobile: string | null
  groupId: number | null
  groupName: string | null
  assignedAt: string
  dueAt: string | null
}

export interface AssignInput {
  targetType: TargetType
  studentId?: number
  groupId?: number
  dueAt?: string
}


/** "All students", "Group: Class 7A" or "Asha (9876543210)". */
export function describeTarget(a: Assignment): string {
  if (a.targetType === 'ALL') return 'All students'
  if (a.targetType === 'GROUP') return `Group: ${a.groupName}`
  return `${a.studentName} (${a.studentMobile})`
}

export function listAssignments(quizId: number): Promise<Assignment[]> {
  return apiFetch(`/admin/quizzes/${quizId}/assignments`)
}

export function assignQuiz(quizId: number, input: AssignInput): Promise<Assignment> {
  return apiFetch(`/admin/quizzes/${quizId}/assignments`, { method: 'POST', body: JSON.stringify(input) })
}

export function unassignQuiz(quizId: number, assignmentId: number): Promise<void> {
  return apiFetch(`/admin/quizzes/${quizId}/assignments/${assignmentId}`, { method: 'DELETE' })
}

import { apiFetch, type Role } from './client'

export interface UserSummary {
  id: number
  name: string
  mobile: string
  email: string | null
  school: string | null
  role: Role
  active: boolean
  createdAt: string
}

export interface Page<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface StudentFilter {
  q?: string
  active?: boolean
  page?: number
  size?: number
}

export function listStudents({ q, active, page = 0, size = 20 }: StudentFilter): Promise<Page<UserSummary>> {
  const params = new URLSearchParams({ page: String(page), size: String(size) })
  if (q) params.set('q', q)
  if (active !== undefined) params.set('active', String(active))
  return apiFetch(`/admin/users?${params}`)
}

export function setUserActive(id: number, active: boolean): Promise<UserSummary> {
  return apiFetch(`/admin/users/${id}`, { method: 'PATCH', body: JSON.stringify({ active }) })
}

export function createAdmin(input: { name: string; mobile: string; pin: string }): Promise<UserSummary> {
  return apiFetch('/admin/admins', { method: 'POST', body: JSON.stringify(input) })
}

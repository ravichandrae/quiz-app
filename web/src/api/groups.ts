import type { UserSummary } from './admin'
import { apiFetch } from './client'

export interface GroupSummary {
  id: number
  name: string
  memberCount: number
  createdAt: string
}

export interface GroupDetail {
  id: number
  name: string
  members: UserSummary[]
  createdAt: string
}

export function listGroups(): Promise<GroupSummary[]> {
  return apiFetch('/admin/groups')
}

export function getGroup(id: number): Promise<GroupDetail> {
  return apiFetch(`/admin/groups/${id}`)
}

export function createGroup(name: string): Promise<GroupDetail> {
  return apiFetch('/admin/groups', { method: 'POST', body: JSON.stringify({ name }) })
}

export function renameGroup(id: number, name: string): Promise<GroupDetail> {
  return apiFetch(`/admin/groups/${id}`, { method: 'PUT', body: JSON.stringify({ name }) })
}

export function deleteGroup(id: number): Promise<void> {
  return apiFetch(`/admin/groups/${id}`, { method: 'DELETE' })
}

export function addMembers(id: number, studentIds: number[]): Promise<GroupDetail> {
  return apiFetch(`/admin/groups/${id}/members`, { method: 'POST', body: JSON.stringify({ studentIds }) })
}

export function removeMember(id: number, studentId: number): Promise<GroupDetail> {
  return apiFetch(`/admin/groups/${id}/members/${studentId}`, { method: 'DELETE' })
}

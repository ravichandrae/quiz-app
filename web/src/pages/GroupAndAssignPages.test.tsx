import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import type { UserSummary } from '../api/admin'
import type { Assignment } from '../api/assignments'
import type { GroupDetail } from '../api/groups'
import type { QuizDetail } from '../api/quizzes'
import { json, mockApi, renderApp, signIn } from '../test-utils'

function student(id: number, name: string): UserSummary {
  return { id, name, mobile: `98765000${id.toString().padStart(2, '0')}`, email: null, school: null, role: 'STUDENT', active: true, createdAt: '', lockedUntil: null }
}

function page<T>(content: T[]) {
  return json({ content, page: 0, size: 10, totalElements: content.length, totalPages: content.length ? 1 : 0 })
}

function bodyOf(call: unknown[] | undefined) {
  return JSON.parse(String((call?.[1] as RequestInit | undefined)?.body))
}

const asha = student(1, 'Asha')
const ravi = student(2, 'Ravi')

describe('groups', () => {
  it('creates a group and opens it', async () => {
    signIn('ADMIN')
    mockApi({
      'GET /api/admin/groups/3': () => json({ id: 3, name: 'Class 7A', members: [], createdAt: '' }),
      'POST /api/admin/groups': () => json({ id: 3, name: 'Class 7A', members: [], createdAt: '' }, 201),
      'GET /api/admin/groups': () => json([]),
      'GET /api/admin/users': () => page([asha]),
    })
    renderApp('/admin/groups')

    expect(await screen.findByText('No groups yet.')).toBeInTheDocument()
    await userEvent.type(screen.getByLabelText('New group name'), 'Class 7A')
    await userEvent.click(screen.getByRole('button', { name: 'Create group' }))

    expect(await screen.findByRole('heading', { name: 'Class 7A' })).toBeInTheDocument()
    expect(screen.getByText('No students yet. Add them below.')).toBeInTheDocument()
  })

  it('adds and removes students', async () => {
    signIn('ADMIN')
    const group: GroupDetail = { id: 3, name: 'Class 7A', members: [asha], createdAt: '' }
    const fetchMock = mockApi({
      'POST /api/admin/groups/3/members': () => json({ ...group, members: [asha, ravi] }),
      'DELETE /api/admin/groups/3/members/1': () => json({ ...group, members: [ravi] }),
      'GET /api/admin/groups/3': () => json(group),
      'GET /api/admin/users': () => page([asha, ravi]),
    })
    renderApp('/admin/groups/3')

    expect(await screen.findByRole('button', { name: 'In group: Asha' })).toBeDisabled()
    await userEvent.click(screen.getByRole('button', { name: 'Add: Ravi' }))
    expect(await screen.findByRole('button', { name: 'Remove Ravi from the group' })).toBeInTheDocument()
    expect(bodyOf(fetchMock.mock.calls.find(([, init]) => init?.method === 'POST'))).toEqual({ studentIds: [2] })

    await userEvent.click(screen.getByRole('button', { name: 'Remove Asha from the group' }))
    const members = screen.getByRole('region', { name: /Students in this group/ })
    expect(await within(members).findByText('Students in this group (1)')).toBeInTheDocument()
    expect(within(members).queryByText('Asha')).not.toBeInTheDocument()
  })
})

describe('assigning a quiz', () => {
  const quiz: QuizDetail = {
    id: 9,
    title: 'Science Week 1',
    totalTimeLimitSeconds: null,
    showAnswers: true,
    questionTimeSeconds: 90,
    questions: [],
    createdAt: '',
    updatedAt: '',
  }
  const everyone: Assignment = {
    id: 50,
    targetType: 'ALL',
    studentId: null,
    studentName: null,
    studentMobile: null,
    groupId: null,
    groupName: null,
    assignedAt: '',
    dueAt: null,
  }

  it('gives a quiz to a group with a due date', async () => {
    signIn('ADMIN')
    const fetchMock = mockApi({
      'GET /api/admin/quizzes/9/assignments': () => json([]),
      'POST /api/admin/quizzes/9/assignments': () =>
        json({ ...everyone, id: 51, targetType: 'GROUP', groupId: 3, groupName: 'Class 7A' }, 201),
      'GET /api/admin/quizzes/9': () => json(quiz),
      'GET /api/admin/groups': () => json([{ id: 3, name: 'Class 7A', memberCount: 30, createdAt: '' }]),
    })
    renderApp('/admin/quizzes/9/assign')

    expect(await screen.findByText('Nobody yet. Choose below who should take this quiz.')).toBeInTheDocument()
    await userEvent.selectOptions(await screen.findByLabelText('Group'), 'Class 7A (30 students)')
    await userEvent.type(screen.getByLabelText('Finish by (optional)'), '2099-10-05')
    await userEvent.click(screen.getByRole('button', { name: 'Give quiz' }))

    expect(await screen.findByRole('status')).toHaveTextContent('Quiz given to Group: Class 7A.')
    const body = bodyOf(fetchMock.mock.calls.find(([, init]) => init?.method === 'POST'))
    expect(body).toMatchObject({ targetType: 'GROUP', groupId: 3 })
    // End of 5 Oct in the viewer's time zone.
    expect(new Date(body.dueAt).getTime()).toBe(new Date('2099-10-05T23:59:59').getTime())
  })

  it('gives a quiz to one chosen student', async () => {
    signIn('ADMIN')
    const fetchMock = mockApi({
      'GET /api/admin/quizzes/9/assignments': () => json([]),
      'POST /api/admin/quizzes/9/assignments': () =>
        json({ ...everyone, targetType: 'STUDENT', studentId: 2, studentName: 'Ravi', studentMobile: ravi.mobile }, 201),
      'GET /api/admin/quizzes/9': () => json(quiz),
      'GET /api/admin/groups': () => json([]),
      'GET /api/admin/users': () => page([asha, ravi]),
    })
    renderApp('/admin/quizzes/9/assign')

    await userEvent.click(await screen.findByLabelText('One student'))
    await userEvent.click(await screen.findByRole('button', { name: 'Choose: Ravi' }))
    expect(screen.getByText(/Chosen: Ravi/)).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Give quiz' }))

    await screen.findByRole('status')
    expect(bodyOf(fetchMock.mock.calls.find(([, init]) => init?.method === 'POST'))).toEqual({
      targetType: 'STUDENT',
      studentId: 2,
    })
  })

  it('asks for a group before giving the quiz', async () => {
    signIn('ADMIN')
    const fetchMock = mockApi({
      'GET /api/admin/quizzes/9/assignments': () => json([]),
      'GET /api/admin/quizzes/9': () => json(quiz),
      'GET /api/admin/groups': () => json([{ id: 3, name: 'Class 7A', memberCount: 30, createdAt: '' }]),
    })
    renderApp('/admin/quizzes/9/assign')

    await userEvent.click(await screen.findByRole('button', { name: 'Give quiz' }))

    expect(screen.getByText('Please choose a group')).toBeInTheDocument()
    expect(fetchMock.mock.calls.some(([, init]) => init?.method === 'POST')).toBe(false)
  })

  it('shows who has the quiz and takes it away', async () => {
    signIn('ADMIN')
    let current = [everyone]
    mockApi({
      'GET /api/admin/quizzes/9/assignments': () => json(current),
      'DELETE /api/admin/quizzes/9/assignments/50': () => {
        current = []
        return new Response(null, { status: 204 })
      },
      'GET /api/admin/quizzes/9': () => json(quiz),
      'GET /api/admin/groups': () => json([]),
    })
    renderApp('/admin/quizzes/9/assign')

    expect(await screen.findByText('All students')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Remove All students' }))
    await userEvent.click(screen.getByRole('button', { name: 'Yes, remove' }))

    expect(await screen.findByText('Nobody yet. Choose below who should take this quiz.')).toBeInTheDocument()
  })
})

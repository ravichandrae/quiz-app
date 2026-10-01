import { useCallback, useId, useState, type FormEvent } from 'react'
import { Link, useParams } from 'react-router'
import type { UserSummary } from '../api/admin'
import { assignQuiz, describeTarget, listAssignments, unassignQuiz, type TargetType } from '../api/assignments'
import { listGroups } from '../api/groups'
import { getQuiz } from '../api/quizzes'
import { ConfirmButton } from '../components/ConfirmButton'
import { errorMessage, formatDuration, toFormError } from '../components/format'
import { StudentPicker } from '../components/StudentPicker'
import { useLoad } from '../components/useLoad'

const dateFormat = new Intl.DateTimeFormat('en-IN', { weekday: 'short', day: 'numeric', month: 'short', year: 'numeric' })

/** Today's date as yyyy-mm-dd in the viewer's time zone, for the date picker's minimum. */
function today(): string {
  const now = new Date()
  return new Date(now.getTime() - now.getTimezoneOffset() * 60_000).toISOString().slice(0, 10)
}

/** The end of the chosen day in the viewer's time zone, so "due 5 Oct" includes all of 5 Oct. */
function endOfDay(date: string): string {
  return new Date(`${date}T23:59:59`).toISOString()
}

export function QuizAssignPage() {
  const quizId = Number(useParams().id)
  const ids = useId()
  const quiz = useLoad(useCallback(() => getQuiz(quizId), [quizId]), 'Could not load the quiz.')
  const assignments = useLoad(useCallback(() => listAssignments(quizId), [quizId]), 'Could not load assignments.')
  const groups = useLoad(useCallback(() => listGroups(), []), 'Could not load groups.')

  const [targetType, setTargetType] = useState<TargetType>('GROUP')
  const [groupId, setGroupId] = useState('')
  const [student, setStudent] = useState<UserSummary | null>(null)
  const [dueDate, setDueDate] = useState('')
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [message, setMessage] = useState<{ ok: boolean; text: string } | null>(null)
  const [busy, setBusy] = useState(false)

  if (quiz.error) {
    return (
      <main>
        <p role="alert" className="alert">
          {quiz.error}
        </p>
        <Link to="/admin/quizzes">Back to quizzes</Link>
      </main>
    )
  }
  if (!quiz.data) return <main aria-busy="true">Loading…</main>

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setMessage(null)
    setFieldErrors({})
    if (targetType === 'GROUP' && !groupId) {
      setFieldErrors({ groupId: 'Please choose a group' })
      return
    }
    if (targetType === 'STUDENT' && !student) {
      setFieldErrors({ studentId: 'Please choose a student' })
      return
    }
    setBusy(true)
    try {
      const created = await assignQuiz(quizId, {
        targetType,
        groupId: targetType === 'GROUP' ? Number(groupId) : undefined,
        studentId: targetType === 'STUDENT' ? student?.id : undefined,
        dueAt: dueDate ? endOfDay(dueDate) : undefined,
      })
      assignments.reload()
      setStudent(null)
      setMessage({ ok: true, text: `Quiz given to ${describeTarget(created)}.` })
    } catch (err) {
      const formError = toFormError(err, 'Could not give the quiz.')
      setFieldErrors(formError.fieldErrors)
      if (formError.message) setMessage({ ok: false, text: formError.message })
    } finally {
      setBusy(false)
    }
  }

  async function handleRemove(assignmentId: number) {
    setMessage(null)
    try {
      await unassignQuiz(quizId, assignmentId)
      assignments.reload()
    } catch (err) {
      setMessage({ ok: false, text: errorMessage(err, 'Could not remove it.') })
    }
  }

  const q = quiz.data
  return (
    <main className="wide">
      <p>
        <Link to="/admin/quizzes">← All quizzes</Link>
      </p>
      <h1>Give “{q.title}” to students</h1>
      <p className="field__hint">
        {q.questions.length} questions ·{' '}
        {q.totalTimeLimitSeconds === null
          ? `up to ${formatDuration(q.questionTimeSeconds)}`
          : formatDuration(q.totalTimeLimitSeconds)}
      </p>

      <section className="panel" aria-labelledby={`${ids}-current`}>
        <h2 id={`${ids}-current`}>Who has this quiz</h2>
        {assignments.error && (
          <p role="alert" className="alert">
            {assignments.error}
          </p>
        )}
        {assignments.data && assignments.data.length === 0 && (
          <p className="empty">Nobody yet. Choose below who should take this quiz.</p>
        )}
        {assignments.data && assignments.data.length > 0 && (
          <ul className="bank">
            {assignments.data.map((a) => (
              <li key={a.id}>
                <span className="chosen__text">{describeTarget(a)}</span>
                <span className="chosen__time">
                  {a.dueAt ? `Due ${dateFormat.format(new Date(a.dueAt))}` : 'No due date'}
                </span>
                <ConfirmButton
                  label="Remove"
                  ariaLabel={`Remove ${describeTarget(a)}`}
                  question="Take the quiz away?"
                  onConfirm={() => handleRemove(a.id)}
                />
              </li>
            ))}
          </ul>
        )}
      </section>

      <form className="panel" onSubmit={handleSubmit} noValidate aria-labelledby={`${ids}-give`}>
        <h2 id={`${ids}-give`}>Give the quiz</h2>
        <fieldset className="choices">
          <legend>Who should take it?</legend>
          {(
            [
              ['GROUP', 'A group'],
              ['STUDENT', 'One student'],
              ['ALL', 'All students (also students who join later)'],
            ] as [TargetType, string][]
          ).map(([value, label]) => (
            <div className="checkbox" key={value}>
              <input
                type="radio"
                name="target"
                id={`${ids}-${value}`}
                checked={targetType === value}
                onChange={() => setTargetType(value)}
              />
              <label htmlFor={`${ids}-${value}`}>{label}</label>
            </div>
          ))}
        </fieldset>

        {targetType === 'GROUP' && (
          <div className="field narrow">
            <label htmlFor={`${ids}-group`}>Group</label>
            {groups.data && groups.data.length === 0 ? (
              <p className="empty">
                There are no groups yet. <Link to="/admin/groups">Create a group</Link> first.
              </p>
            ) : (
              <select
                id={`${ids}-group`}
                value={groupId}
                onChange={(e) => setGroupId(e.target.value)}
                aria-invalid={fieldErrors.groupId ? true : undefined}
              >
                <option value="">Choose a group…</option>
                {groups.data?.map((g) => (
                  <option key={g.id} value={g.id}>
                    {g.name} ({g.memberCount} {g.memberCount === 1 ? 'student' : 'students'})
                  </option>
                ))}
              </select>
            )}
            {fieldErrors.groupId && <span className="field__error">{fieldErrors.groupId}</span>}
          </div>
        )}

        {targetType === 'STUDENT' && (
          <div className="field">
            {student ? (
              <p className="notice">
                Chosen: {student.name} ({student.mobile}){' '}
                <button type="button" className="button button--secondary button--small" onClick={() => setStudent(null)}>
                  Change
                </button>
              </p>
            ) : (
              <StudentPicker actionLabel="Choose" onPick={setStudent} />
            )}
            {fieldErrors.studentId && <span className="field__error">{fieldErrors.studentId}</span>}
          </div>
        )}

        <div className="field narrow">
          <label htmlFor={`${ids}-due`}>Finish by (optional)</label>
          <input
            id={`${ids}-due`}
            type="date"
            min={today()}
            value={dueDate}
            onChange={(e) => setDueDate(e.target.value)}
            aria-invalid={fieldErrors.dueAt ? true : undefined}
          />
          {fieldErrors.dueAt && <span className="field__error">{fieldErrors.dueAt}</span>}
        </div>

        {message && (
          <p role={message.ok ? 'status' : 'alert'} className={message.ok ? 'notice' : 'alert'}>
            {message.text}
          </p>
        )}
        <button type="submit" className="button" disabled={busy}>
          Give quiz
        </button>
      </form>
    </main>
  )
}

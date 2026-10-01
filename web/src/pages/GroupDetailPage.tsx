import { useCallback, useState, type FormEvent } from 'react'
import { Link, useParams } from 'react-router'
import { addMembers, getGroup, removeMember, renameGroup, type GroupDetail } from '../api/groups'
import { Field } from '../components/Field'
import { errorMessage, toFormError } from '../components/format'
import { StudentPicker } from '../components/StudentPicker'
import { useLoad } from '../components/useLoad'

export function GroupDetailPage() {
  const groupId = Number(useParams().id)
  const { data: group, error, setData } = useLoad(
    useCallback(() => getGroup(groupId), [groupId]),
    'Could not load the group.',
  )
  const [actionError, setActionError] = useState<string | null>(null)

  if (error) {
    return (
      <main>
        <p role="alert" className="alert">
          {error}
        </p>
        <Link to="/admin/groups">Back to groups</Link>
      </main>
    )
  }
  if (!group) return <main aria-busy="true">Loading…</main>

  async function change(action: () => Promise<GroupDetail>, fallback: string) {
    setActionError(null)
    try {
      setData(await action())
    } catch (err) {
      setActionError(errorMessage(err, fallback))
    }
  }

  return (
    <main className="wide">
      <p>
        <Link to="/admin/groups">← All groups</Link>
      </p>
      <h1>{group.name}</h1>

      <RenameForm key={group.name} group={group} onRenamed={setData} />

      {actionError && (
        <p role="alert" className="alert">
          {actionError}
        </p>
      )}

      <section className="panel" aria-labelledby="members-title">
        <h2 id="members-title">Students in this group ({group.members.length})</h2>
        {group.members.length === 0 ? (
          <p className="empty">No students yet. Add them below.</p>
        ) : (
          <ul className="bank">
            {group.members.map((m) => (
              <li key={m.id}>
                <span className="chosen__text">
                  {m.name}
                  {!m.active && <span className="badge badge--off">Turned off</span>}
                </span>
                <span className="chosen__time">{m.mobile}</span>
                <button
                  type="button"
                  className="button button--secondary button--small"
                  onClick={() => change(() => removeMember(group.id, m.id), 'Could not remove the student.')}
                  aria-label={`Remove ${m.name} from the group`}
                >
                  Remove
                </button>
              </li>
            ))}
          </ul>
        )}
      </section>

      <section className="panel" aria-labelledby="add-title">
        <h2 id="add-title">Add students</h2>
        <StudentPicker
          actionLabel="Add"
          disabledIds={new Set(group.members.map((m) => m.id))}
          disabledLabel="In group"
          onPick={(student) => change(() => addMembers(group.id, [student.id]), 'Could not add the student.')}
        />
      </section>
    </main>
  )
}

function RenameForm({ group, onRenamed }: { group: GroupDetail; onRenamed: (group: GroupDetail) => void }) {
  const [name, setName] = useState(group.name)
  const [error, setError] = useState<string | undefined>()
  const [busy, setBusy] = useState(false)

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setBusy(true)
    setError(undefined)
    try {
      onRenamed(await renameGroup(group.id, name))
    } catch (err) {
      const formError = toFormError(err, 'Could not rename the group.')
      setError(formError.fieldErrors.name ?? formError.message ?? undefined)
    } finally {
      setBusy(false)
    }
  }

  return (
    <form className="toolbar toolbar--aligned" onSubmit={handleSubmit} noValidate>
      <Field label="Group name" maxLength={100} value={name} onChange={(e) => setName(e.target.value)} error={error} />
      <button type="submit" className="button button--secondary" disabled={busy || name.trim() === group.name}>
        Rename
      </button>
    </form>
  )
}

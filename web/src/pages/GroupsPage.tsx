import { useCallback, useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router'
import { createGroup, deleteGroup, listGroups } from '../api/groups'
import { ConfirmButton } from '../components/ConfirmButton'
import { Field } from '../components/Field'
import { errorMessage, toFormError } from '../components/format'
import { useLoad } from '../components/useLoad'

export function GroupsPage() {
  const navigate = useNavigate()
  const { data: groups, error, reload } = useLoad(useCallback(() => listGroups(), []), 'Could not load groups.')
  const [name, setName] = useState('')
  const [nameError, setNameError] = useState<string | undefined>()
  const [actionError, setActionError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function handleCreate(e: FormEvent) {
    e.preventDefault()
    setBusy(true)
    setNameError(undefined)
    setActionError(null)
    try {
      const group = await createGroup(name)
      navigate(`/admin/groups/${group.id}`)
    } catch (err) {
      const formError = toFormError(err, 'Could not create the group.')
      setNameError(formError.fieldErrors.name)
      setActionError(formError.message)
    } finally {
      setBusy(false)
    }
  }

  async function handleDelete(id: number) {
    setActionError(null)
    try {
      await deleteGroup(id)
      reload()
    } catch (err) {
      setActionError(errorMessage(err, 'Could not delete the group.'))
    }
  }

  return (
    <main className="wide">
      <h1>Groups</h1>
      <p className="field__hint">
        Put students into groups, such as a class, to give them a quiz together. Students who join a group later also
        get its quizzes.
      </p>

      <form className="toolbar toolbar--aligned" onSubmit={handleCreate} noValidate>
        <Field
          label="New group name"
          placeholder="e.g. Class 7A"
          maxLength={100}
          value={name}
          onChange={(e) => setName(e.target.value)}
          error={nameError}
        />
        <button type="submit" className="button" disabled={busy}>
          Create group
        </button>
      </form>

      {(error || actionError) && (
        <p role="alert" className="alert">
          {actionError ?? error}
        </p>
      )}

      {groups && groups.length === 0 && <p className="empty">No groups yet.</p>}

      {groups && groups.length > 0 && (
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Group</th>
                <th>Students</th>
                <th>
                  <span className="visually-hidden">Actions</span>
                </th>
              </tr>
            </thead>
            <tbody>
              {groups.map((group) => (
                <tr key={group.id}>
                  <td className="wrap">{group.name}</td>
                  <td>{group.memberCount}</td>
                  <td>
                    <div className="actions">
                      <Link
                        to={`/admin/groups/${group.id}`}
                        className="button button--secondary button--small"
                        aria-label={`Open ${group.name}`}
                      >
                        Open
                      </Link>
                      <ConfirmButton
                        label="Delete"
                        ariaLabel={`Delete ${group.name}`}
                        question="Delete this group? Its students lose the group's quizzes."
                        onConfirm={() => handleDelete(group.id)}
                      />
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </main>
  )
}

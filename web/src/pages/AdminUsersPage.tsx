import { useEffect, useState, type FormEvent } from 'react'
import { createAdmin, listStudents, resetPin, setUserActive, type Page, type UserSummary } from '../api/admin'
import { ApiError } from '../api/client'
import { digitsOnly } from '../components/digitsOnly'
import { Field } from '../components/Field'
import { Pager } from '../components/Pager'

type StatusFilter = 'all' | 'active' | 'inactive'

const dateFormat = new Intl.DateTimeFormat('en-IN', { day: 'numeric', month: 'short', year: 'numeric' })

function isLocked(user: UserSummary): boolean {
  return user.lockedUntil !== null && new Date(user.lockedUntil).getTime() > Date.now()
}

function statusOf(user: UserSummary): { label: string; className: string } {
  if (!user.active) return { label: 'Turned off', className: 'badge--off' }
  if (isLocked(user)) return { label: 'Locked', className: 'badge--off' }
  return { label: 'Active', className: 'badge--ok' }
}

export function AdminUsersPage() {
  const [query, setQuery] = useState('')
  const [submittedQuery, setSubmittedQuery] = useState('')
  const [status, setStatus] = useState<StatusFilter>('all')
  const [page, setPage] = useState(0)
  const [result, setResult] = useState<Page<UserSummary> | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busyId, setBusyId] = useState<number | null>(null)
  const [resetting, setResetting] = useState<UserSummary | null>(null)

  useEffect(() => {
    // Ignore responses for an outdated search, which could otherwise arrive last and win.
    let current = true
    listStudents({
      q: submittedQuery || undefined,
      active: status === 'all' ? undefined : status === 'active',
      page,
    }).then(
      (next) => {
        if (!current) return
        setResult(next)
        setError(null)
      },
      (err: unknown) => {
        if (current) setError(err instanceof ApiError ? err.message : 'Could not load students.')
      },
    )
    return () => {
      current = false
    }
  }, [submittedQuery, status, page])

  function handleSearch(e: FormEvent) {
    e.preventDefault()
    setPage(0)
    setSubmittedQuery(query.trim())
  }

  function replaceUser(updated: UserSummary) {
    setResult((r) => r && { ...r, content: r.content.map((u) => (u.id === updated.id ? updated : u)) })
  }

  async function toggleActive(user: UserSummary) {
    setBusyId(user.id)
    setError(null)
    try {
      const updated = await setUserActive(user.id, !user.active)
      replaceUser(updated)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not update the student.')
    } finally {
      setBusyId(null)
    }
  }

  return (
    <main className="wide">
      <h1>Students</h1>

      <form className="toolbar" onSubmit={handleSearch} role="search">
        <input
          aria-label="Search by name or mobile number"
          placeholder="Search by name or mobile"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
        />
        <select
          aria-label="Status"
          value={status}
          onChange={(e) => {
            setPage(0)
            setStatus(e.target.value as StatusFilter)
          }}
        >
          <option value="all">All students</option>
          <option value="active">Active</option>
          <option value="inactive">Turned off</option>
        </select>
        <button type="submit" className="button">
          Search
        </button>
      </form>

      {error && (
        <p role="alert" className="alert">
          {error}
        </p>
      )}

      {resetting && (
        <ResetPinPanel
          key={resetting.id}
          user={resetting}
          onDone={(updated) => replaceUser(updated)}
          onClose={() => setResetting(null)}
        />
      )}

      {result && result.content.length === 0 && <p className="empty">No students found.</p>}

      {result && result.content.length > 0 && (
        <>
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Name</th>
                  <th>Mobile</th>
                  <th>School</th>
                  <th>Registered</th>
                  <th>Status</th>
                  <th>
                    <span className="visually-hidden">Actions</span>
                  </th>
                </tr>
              </thead>
              <tbody>
                {result.content.map((user) => (
                  <tr key={user.id}>
                    <td>{user.name}</td>
                    <td>{user.mobile}</td>
                    <td>{user.school ?? '—'}</td>
                    <td>{dateFormat.format(new Date(user.createdAt))}</td>
                    <td>
                      <span className={`badge ${statusOf(user).className}`}>{statusOf(user).label}</span>
                    </td>
                    <td>
                      <div className="actions">
                        <button
                          type="button"
                          className="button button--secondary button--small"
                          onClick={() => setResetting(user)}
                          aria-label={`Reset PIN for ${user.name}`}
                        >
                          Reset PIN
                        </button>
                        <button
                          type="button"
                          className="button button--secondary button--small"
                          disabled={busyId === user.id}
                          onClick={() => toggleActive(user)}
                          aria-label={`${user.active ? 'Turn off' : 'Turn on'} ${user.name}`}
                        >
                          {user.active ? 'Turn off' : 'Turn on'}
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <Pager page={result} noun="students" onChange={setPage} />
        </>
      )}

      <AddAdminForm />
    </main>
  )
}

function ResetPinPanel({
  user,
  onDone,
  onClose,
}: {
  user: UserSummary
  onDone: (updated: UserSummary) => void
  onClose: () => void
}) {
  const [pin, setPin] = useState('')
  const [confirmPin, setConfirmPin] = useState('')
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [error, setError] = useState<string | null>(null)
  const [done, setDone] = useState(false)
  const [busy, setBusy] = useState(false)

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    if (pin !== confirmPin) {
      setFieldErrors({ confirmPin: 'The two PINs are not the same' })
      return
    }
    setFieldErrors({})
    setBusy(true)
    try {
      onDone(await resetPin(user.id, pin))
      setDone(true)
    } catch (err) {
      if (err instanceof ApiError) {
        setFieldErrors(err.fieldErrors)
        if (!Object.keys(err.fieldErrors).length) setError(err.message)
      } else {
        setError('Could not reset the PIN.')
      }
    } finally {
      setBusy(false)
    }
  }

  return (
    <section className="panel" aria-labelledby="reset-pin-title">
      <h2 id="reset-pin-title">Reset PIN for {user.name}</h2>
      {done ? (
        <>
          <p role="status" className="notice">
            {user.name}&apos;s PIN has been changed. Tell them their new PIN.
          </p>
          <button type="button" className="button" onClick={onClose}>
            Done
          </button>
        </>
      ) : (
        <form onSubmit={handleSubmit} noValidate>
          <p className="field__hint">
            Choose the new PIN together with the student. Their old PIN will stop working and any lock is removed.
          </p>
          <Field
            label="New PIN"
            hint="4 to 6 numbers"
            type="password"
            inputMode="numeric"
            autoComplete="new-password"
            maxLength={6}
            autoFocus
            value={pin}
            onChange={(e) => setPin(digitsOnly(e.target.value))}
            error={fieldErrors.pin}
          />
          <Field
            label="Type the new PIN again"
            type="password"
            inputMode="numeric"
            autoComplete="new-password"
            maxLength={6}
            value={confirmPin}
            onChange={(e) => setConfirmPin(digitsOnly(e.target.value))}
            error={fieldErrors.confirmPin}
          />
          {error && (
            <p role="alert" className="alert">
              {error}
            </p>
          )}
          <div className="button-row">
            <button type="submit" className="button" disabled={busy}>
              Save new PIN
            </button>
            <button type="button" className="button button--secondary" onClick={onClose}>
              Cancel
            </button>
          </div>
        </form>
      )}
    </section>
  )
}

function AddAdminForm() {
  const [form, setForm] = useState({ name: '', mobile: '', pin: '' })
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [message, setMessage] = useState<{ ok: boolean; text: string } | null>(null)
  const [busy, setBusy] = useState(false)

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setBusy(true)
    setMessage(null)
    setFieldErrors({})
    try {
      const admin = await createAdmin({ ...form, mobile: digitsOnly(form.mobile) })
      setForm({ name: '', mobile: '', pin: '' })
      setMessage({ ok: true, text: `${admin.name} can now log in as an admin.` })
    } catch (err) {
      if (err instanceof ApiError) {
        setFieldErrors(err.fieldErrors)
        if (!Object.keys(err.fieldErrors).length) setMessage({ ok: false, text: err.message })
      } else {
        setMessage({ ok: false, text: 'Could not add the admin.' })
      }
    } finally {
      setBusy(false)
    }
  }

  return (
    <details className="panel">
      <summary>Add another admin</summary>
      <form onSubmit={handleSubmit} noValidate>
        <Field
          label="Name"
          value={form.name}
          maxLength={100}
          onChange={(e) => setForm({ ...form, name: e.target.value })}
          error={fieldErrors.name}
        />
        <Field
          label="Mobile number"
          type="tel"
          inputMode="numeric"
          maxLength={14}
          value={form.mobile}
          onChange={(e) => setForm({ ...form, mobile: e.target.value })}
          error={fieldErrors.mobile}
        />
        <Field
          label="PIN"
          hint="4 to 6 numbers"
          type="password"
          inputMode="numeric"
          autoComplete="new-password"
          maxLength={6}
          value={form.pin}
          onChange={(e) => setForm({ ...form, pin: digitsOnly(e.target.value) })}
          error={fieldErrors.pin}
        />
        {message && (
          <p role={message.ok ? 'status' : 'alert'} className={message.ok ? 'notice' : 'alert'}>
            {message.text}
          </p>
        )}
        <button type="submit" className="button" disabled={busy}>
          Add admin
        </button>
      </form>
    </details>
  )
}

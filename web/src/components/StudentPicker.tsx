import { useCallback, useState } from 'react'
import { listStudents, type UserSummary } from '../api/admin'
import { Pager } from './Pager'
import { useLoad } from './useLoad'

interface StudentPickerProps {
  /** Label of each row's button, e.g. "Add" or "Choose". */
  actionLabel: string
  /** Students for whom the button is disabled, with the text shown instead. */
  disabledIds?: Set<number>
  disabledLabel?: string
  onPick: (student: UserSummary) => void
}

/** Searches active students and offers a button per student. Not a <form>, so it can sit inside one. */
export function StudentPicker({ actionLabel, disabledIds, disabledLabel = 'Added', onPick }: StudentPickerProps) {
  const [query, setQuery] = useState('')
  const [search, setSearch] = useState({ q: '', page: 0 })
  const load = useCallback(
    () => listStudents({ q: search.q || undefined, active: true, page: search.page, size: 10 }),
    [search],
  )
  const { data: result, error } = useLoad(load, 'Could not load students.')
  const runSearch = () => setSearch({ q: query.trim(), page: 0 })

  return (
    <div className="picker">
      <div className="toolbar" role="search">
        <input
          aria-label="Search students by name or mobile number"
          placeholder="Search by name or mobile"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter') {
              e.preventDefault()
              runSearch()
            }
          }}
        />
        <button type="button" className="button" onClick={runSearch}>
          Search
        </button>
      </div>
      {error && (
        <p role="alert" className="alert">
          {error}
        </p>
      )}
      {result && result.content.length === 0 && <p className="empty">No students found.</p>}
      {result && result.content.length > 0 && (
        <>
          <ul className="bank">
            {result.content.map((student) => {
              const disabled = disabledIds?.has(student.id) ?? false
              return (
                <li key={student.id}>
                  <span className="chosen__text">
                    {student.name}
                    {student.school && <span className="muted"> · {student.school}</span>}
                  </span>
                  <span className="chosen__time">{student.mobile}</span>
                  <button
                    type="button"
                    className="button button--secondary button--small"
                    disabled={disabled}
                    onClick={() => onPick(student)}
                    aria-label={`${disabled ? disabledLabel : actionLabel}: ${student.name}`}
                  >
                    {disabled ? disabledLabel : actionLabel}
                  </button>
                </li>
              )
            })}
          </ul>
          <Pager page={result} noun="students" onChange={(page) => setSearch((s) => ({ ...s, page }))} />
        </>
      )}
    </div>
  )
}

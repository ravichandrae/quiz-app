import { useCallback, useState, type FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router'
import { listQuizzes } from '../api/quizzes'
import { downloadAttemptsCsv, listAttempts } from '../api/results'
import { errorMessage } from '../components/format'
import { Pager } from '../components/Pager'
import { useLoad } from '../components/useLoad'

const dateFormat = new Intl.DateTimeFormat('en-IN', { day: 'numeric', month: 'short', hour: 'numeric', minute: '2-digit' })

/** /admin/results?quizId=&q=&page= — who took which quiz and how they did. Filters live in the URL. */
export function ResultsPage() {
  const [params, setParams] = useSearchParams()
  const quizId = Number(params.get('quizId')) || undefined
  const q = params.get('q') ?? ''
  const page = Number(params.get('page')) || 0
  const [query, setQuery] = useState(q)
  const [downloadError, setDownloadError] = useState<string | null>(null)
  const [downloading, setDownloading] = useState(false)

  const quizzes = useLoad(useCallback(() => listQuizzes({ size: 100 }), []), 'Could not load quizzes.')
  const load = useCallback(() => listAttempts({ quizId, q: q || undefined, page }), [quizId, q, page])
  const { data: result, error } = useLoad(load, 'Could not load results.')

  function update(changes: Record<string, string | undefined>) {
    const next = new URLSearchParams(params)
    for (const [key, value] of Object.entries(changes)) {
      if (value) next.set(key, value)
      else next.delete(key)
    }
    setParams(next)
  }

  function handleSearch(e: FormEvent) {
    e.preventDefault()
    update({ q: query.trim() || undefined, page: undefined })
  }

  async function handleDownload() {
    setDownloadError(null)
    setDownloading(true)
    try {
      await downloadAttemptsCsv({ quizId, q: q || undefined })
    } catch (err) {
      setDownloadError(errorMessage(err, 'Could not download the results.'))
    } finally {
      setDownloading(false)
    }
  }

  return (
    <main className="wide">
      <div className="page-title">
        <h1>Results</h1>
        <button type="button" className="button button--secondary" onClick={handleDownload} disabled={downloading}>
          {downloading ? 'Preparing…' : 'Download CSV'}
        </button>
      </div>

      <form className="toolbar" onSubmit={handleSearch} role="search">
        <select
          aria-label="Quiz"
          value={quizId ?? ''}
          onChange={(e) => update({ quizId: e.target.value || undefined, page: undefined })}
        >
          <option value="">All quizzes</option>
          {quizzes.data?.content.map((quiz) => (
            <option key={quiz.id} value={quiz.id}>
              {quiz.title}
            </option>
          ))}
        </select>
        <input
          aria-label="Search by student name or mobile number"
          placeholder="Student name or mobile"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
        />
        <button type="submit" className="button">
          Search
        </button>
      </form>

      {(error || downloadError) && (
        <p role="alert" className="alert">
          {downloadError ?? error}
        </p>
      )}

      {result && result.content.length === 0 && <p className="empty">No one has taken a quiz that matches yet.</p>}

      {result && result.content.length > 0 && (
        <>
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Student</th>
                  <th>Quiz</th>
                  <th>Score</th>
                  <th>Finished</th>
                  <th>
                    <span className="visually-hidden">Actions</span>
                  </th>
                </tr>
              </thead>
              <tbody>
                {result.content.map((a) => (
                  <tr key={a.id}>
                    <td>
                      {a.studentName}
                      <br />
                      <span className="muted">{a.studentMobile}</span>
                    </td>
                    <td className="wrap">{a.quizTitle}</td>
                    <td>
                      {a.status === 'COMPLETED' ? (
                        <>
                          {a.score}/{a.questionCount} <span className="muted">({a.percentage}%)</span>
                        </>
                      ) : (
                        <span className="badge badge--off">Not finished</span>
                      )}
                    </td>
                    <td>
                      {a.finishedAt ? dateFormat.format(new Date(a.finishedAt)) : '—'}
                      {a.late && <span className="badge badge--off late-badge">Late</span>}
                    </td>
                    <td>
                      <Link
                        to={`/admin/results/${a.id}`}
                        className="button button--secondary button--small"
                        aria-label={`View ${a.studentName}'s answers for ${a.quizTitle}`}
                      >
                        View
                      </Link>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <Pager page={result} noun="results" onChange={(p) => update({ page: p ? String(p) : undefined })} />
        </>
      )}
    </main>
  )
}

import { useCallback, useState, type FormEvent } from 'react'
import { Link, useLocation } from 'react-router'
import { deleteQuiz, listQuizzes } from '../api/quizzes'
import { ConfirmButton } from '../components/ConfirmButton'
import { errorMessage, formatDuration } from '../components/format'
import { Pager } from '../components/Pager'
import { useLoad } from '../components/useLoad'

export function QuizzesPage() {
  const notice = (useLocation().state as { notice?: string } | null)?.notice
  const [query, setQuery] = useState('')
  const [search, setSearch] = useState({ q: '', page: 0 })
  const [actionError, setActionError] = useState<string | null>(null)

  const load = useCallback(() => listQuizzes({ q: search.q || undefined, page: search.page }), [search])
  const { data: result, error, reload } = useLoad(load, 'Could not load quizzes.')

  function handleSearch(e: FormEvent) {
    e.preventDefault()
    setSearch({ q: query.trim(), page: 0 })
  }

  async function handleDelete(id: number) {
    setActionError(null)
    try {
      await deleteQuiz(id)
      reload()
    } catch (err) {
      setActionError(errorMessage(err, 'Could not delete the quiz.'))
    }
  }

  return (
    <main className="wide">
      <div className="page-title">
        <h1>Quizzes</h1>
        <Link to="/admin/quizzes/new" className="button">
          Create quiz
        </Link>
      </div>

      {notice && (
        <p role="status" className="notice">
          {notice}
        </p>
      )}

      <form className="toolbar" onSubmit={handleSearch} role="search">
        <input
          aria-label="Search quizzes"
          placeholder="Search quizzes"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
        />
        <button type="submit" className="button">
          Search
        </button>
      </form>

      {(error || actionError) && (
        <p role="alert" className="alert">
          {actionError ?? error}
        </p>
      )}

      {result && result.content.length === 0 && (
        <p className="empty">{search.q ? 'No quizzes match your search.' : 'No quizzes yet. Create your first quiz.'}</p>
      )}

      {result && result.content.length > 0 && (
        <>
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Title</th>
                  <th>Questions</th>
                  <th>Time</th>
                  <th>Answers shown</th>
                  <th>
                    <span className="visually-hidden">Actions</span>
                  </th>
                </tr>
              </thead>
              <tbody>
                {result.content.map((quiz) => (
                  <tr key={quiz.id}>
                    <td className="wrap">{quiz.title}</td>
                    <td>{quiz.questionCount}</td>
                    <td>
                      {quiz.totalTimeLimitSeconds === null
                        ? `Up to ${formatDuration(quiz.questionTimeSeconds)}`
                        : formatDuration(quiz.totalTimeLimitSeconds)}
                    </td>
                    <td>{quiz.showAnswers ? 'Yes' : 'No'}</td>
                    <td>
                      <div className="actions">
                        <Link
                          to={`/admin/quizzes/${quiz.id}/assign`}
                          className="button button--small"
                          aria-label={`Give ${quiz.title} to students`}
                        >
                          Assign
                        </Link>
                        <Link
                          to={`/admin/results?quizId=${quiz.id}`}
                          className="button button--secondary button--small"
                          aria-label={`Results for ${quiz.title}`}
                        >
                          Results
                        </Link>
                        <Link
                          to={`/admin/quizzes/${quiz.id}`}
                          className="button button--secondary button--small"
                          aria-label={`Edit ${quiz.title}`}
                        >
                          Edit
                        </Link>
                        <ConfirmButton
                          label="Delete"
                          ariaLabel={`Delete ${quiz.title}`}
                          question="Delete this quiz?"
                          onConfirm={() => handleDelete(quiz.id)}
                        />
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <Pager page={result} noun="quizzes" onChange={(page) => setSearch((s) => ({ ...s, page }))} />
        </>
      )}
    </main>
  )
}

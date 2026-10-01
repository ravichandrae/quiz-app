import { useCallback, useState, type FormEvent } from 'react'
import { Link, useLocation } from 'react-router'
import { deleteQuestion, listQuestions, OPTION_LABELS } from '../api/questions'
import { ConfirmButton } from '../components/ConfirmButton'
import { errorMessage, formatDuration } from '../components/format'
import { Pager } from '../components/Pager'
import { useLoad } from '../components/useLoad'

export function QuestionsPage() {
  const notice = (useLocation().state as { notice?: string } | null)?.notice
  const [query, setQuery] = useState('')
  const [search, setSearch] = useState({ q: '', page: 0 })
  const [actionError, setActionError] = useState<string | null>(null)

  const load = useCallback(() => listQuestions({ q: search.q || undefined, page: search.page }), [search])
  const { data: result, error, reload } = useLoad(load, 'Could not load questions.')

  function handleSearch(e: FormEvent) {
    e.preventDefault()
    setSearch({ q: query.trim(), page: 0 })
  }

  async function handleDelete(id: number) {
    setActionError(null)
    try {
      await deleteQuestion(id)
      reload()
    } catch (err) {
      setActionError(errorMessage(err, 'Could not delete the question.'))
    }
  }

  return (
    <main className="wide">
      <div className="page-title">
        <h1>Question bank</h1>
        <Link to="/admin/questions/new" className="button">
          Add question
        </Link>
      </div>

      {notice && (
        <p role="status" className="notice">
          {notice}
        </p>
      )}

      <form className="toolbar" onSubmit={handleSearch} role="search">
        <input
          aria-label="Search questions"
          placeholder="Search questions"
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
        <p className="empty">{search.q ? 'No questions match your search.' : 'No questions yet. Add your first question.'}</p>
      )}

      {result && result.content.length > 0 && (
        <>
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Question</th>
                  <th>Correct answer</th>
                  <th>Time</th>
                  <th>
                    <span className="visually-hidden">Actions</span>
                  </th>
                </tr>
              </thead>
              <tbody>
                {result.content.map((question, index) => (
                  <tr key={question.id}>
                    <td className="wrap">{question.text}</td>
                    <td className="wrap">
                      {OPTION_LABELS[question.correctOption]}. {question.options[question.correctOption]}
                    </td>
                    <td>{formatDuration(question.timeLimitSeconds)}</td>
                    <td>
                      <div className="actions">
                        <Link
                          to={`/admin/questions/${question.id}`}
                          className="button button--secondary button--small"
                          aria-label={`Edit question ${search.page * result.size + index + 1}`}
                        >
                          Edit
                        </Link>
                        <ConfirmButton
                          label="Delete"
                          ariaLabel={`Delete question ${search.page * result.size + index + 1}`}
                          question="Delete this question?"
                          onConfirm={() => handleDelete(question.id)}
                        />
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <Pager page={result} noun="questions" onChange={(page) => setSearch((s) => ({ ...s, page }))} />
        </>
      )}
    </main>
  )
}

import { useCallback, useEffect, useId, useState, type FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { listQuestions, type Question } from '../api/questions'
import { createQuiz, getQuiz, updateQuiz } from '../api/quizzes'
import { Field } from '../components/Field'
import { errorMessage, formatDuration, toFormError } from '../components/format'
import { Pager } from '../components/Pager'
import { useLoad } from '../components/useLoad'

interface QuizForm {
  title: string
  hasTimeLimit: boolean
  /** Kept as text so the box can be emptied while typing. */
  timeLimitMinutes: string
  showAnswers: boolean
  questions: Question[]
}

const EMPTY: QuizForm = { title: '', hasTimeLimit: false, timeLimitMinutes: '10', showAnswers: true, questions: [] }

/** Creates a quiz (/admin/quizzes/new) or edits one (/admin/quizzes/:id). */
export function QuizFormPage() {
  const { id } = useParams()
  const quizId = id === undefined ? null : Number(id)
  const navigate = useNavigate()
  const ids = useId()
  const [form, setForm] = useState<QuizForm | null>(quizId === null ? EMPTY : null)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [message, setMessage] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    if (quizId === null) return
    let current = true
    getQuiz(quizId).then(
      (quiz) =>
        current &&
        setForm({
          title: quiz.title,
          hasTimeLimit: quiz.totalTimeLimitSeconds !== null,
          timeLimitMinutes: quiz.totalTimeLimitSeconds === null ? '10' : String(Math.round(quiz.totalTimeLimitSeconds / 60)),
          showAnswers: quiz.showAnswers,
          questions: quiz.questions,
        }),
      (err: unknown) => current && setLoadError(errorMessage(err, 'Could not load the quiz.')),
    )
    return () => {
      current = false
    }
  }, [quizId])

  if (loadError) {
    return (
      <main>
        <p role="alert" className="alert">
          {loadError}
        </p>
        <Link to="/admin/quizzes">Back to quizzes</Link>
      </main>
    )
  }
  if (!form) return <main aria-busy="true">Loading…</main>

  const update = (changes: Partial<QuizForm>) => setForm((f) => f && { ...f, ...changes })
  const questionSeconds = form.questions.reduce((sum, q) => sum + q.timeLimitSeconds, 0)
  const limitSeconds = Number(form.timeLimitMinutes) * 60

  function move(index: number, by: -1 | 1) {
    if (!form) return
    const next = [...form.questions]
    ;[next[index], next[index + by]] = [next[index + by], next[index]]
    update({ questions: next })
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    if (!form) return
    setMessage(null)
    setFieldErrors({})
    setBusy(true)
    const input = {
      title: form.title,
      totalTimeLimitSeconds: form.hasTimeLimit ? Math.round(Number(form.timeLimitMinutes) * 60) || 0 : null,
      showAnswers: form.showAnswers,
      questionIds: form.questions.map((q) => q.id),
    }
    try {
      if (quizId === null) await createQuiz(input)
      else await updateQuiz(quizId, input)
      navigate('/admin/quizzes', { state: { notice: quizId === null ? 'Quiz created.' : 'Quiz saved.' } })
    } catch (err) {
      const formError = toFormError(err, 'Could not save the quiz.')
      setFieldErrors(formError.fieldErrors)
      setMessage(formError.message ?? 'Please fix the highlighted fields.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <main className="wide">
      <h1>{quizId === null ? 'Create quiz' : 'Edit quiz'}</h1>
      <form onSubmit={handleSubmit} noValidate>
        <div className="narrow">
          <Field
            label="Quiz title"
            maxLength={150}
            value={form.title}
            onChange={(e) => update({ title: e.target.value })}
            error={fieldErrors.title}
          />

          <div className="checkbox">
            <input
              type="checkbox"
              id={`${ids}-has-limit`}
              checked={form.hasTimeLimit}
              onChange={(e) => update({ hasTimeLimit: e.target.checked })}
            />
            <label htmlFor={`${ids}-has-limit`}>Set a time limit for the whole quiz</label>
          </div>
          {form.hasTimeLimit && (
            <Field
              label="Minutes for the whole quiz"
              type="number"
              inputMode="numeric"
              min={1}
              max={180}
              value={form.timeLimitMinutes}
              onChange={(e) => update({ timeLimitMinutes: e.target.value })}
              error={fieldErrors.totalTimeLimitSeconds}
              hint={
                questionSeconds > limitSeconds && limitSeconds > 0
                  ? `The questions add up to ${formatDuration(questionSeconds)}, so slow students may not reach the last questions.`
                  : undefined
              }
            />
          )}

          <div className="checkbox">
            <input
              type="checkbox"
              id={`${ids}-show-answers`}
              checked={form.showAnswers}
              onChange={(e) => update({ showAnswers: e.target.checked })}
            />
            <label htmlFor={`${ids}-show-answers`}>Show students the correct answers after they finish</label>
          </div>
        </div>

        <section className="panel" aria-labelledby={`${ids}-chosen`}>
          <h2 id={`${ids}-chosen`}>Questions in this quiz</h2>
          <p className="field__hint">
            {form.questions.length === 0
              ? 'No questions yet. Add them from the question bank below.'
              : `${form.questions.length} ${form.questions.length === 1 ? 'question' : 'questions'} · up to ${formatDuration(questionSeconds)}`}
          </p>
          {fieldErrors.questionIds && <p className="field__error">{fieldErrors.questionIds}</p>}
          <ol className="chosen">
            {form.questions.map((q, i) => (
              <li key={q.id}>
                <span className="chosen__text">{q.text}</span>
                <span className="chosen__time">{formatDuration(q.timeLimitSeconds)}</span>
                <span className="actions">
                  <button
                    type="button"
                    className="button button--secondary button--small"
                    disabled={i === 0}
                    onClick={() => move(i, -1)}
                    aria-label={`Move question ${i + 1} up`}
                  >
                    ↑
                  </button>
                  <button
                    type="button"
                    className="button button--secondary button--small"
                    disabled={i === form.questions.length - 1}
                    onClick={() => move(i, 1)}
                    aria-label={`Move question ${i + 1} down`}
                  >
                    ↓
                  </button>
                  <button
                    type="button"
                    className="button button--secondary button--small"
                    onClick={() => update({ questions: form.questions.filter((x) => x.id !== q.id) })}
                    aria-label={`Remove question ${i + 1}`}
                  >
                    Remove
                  </button>
                </span>
              </li>
            ))}
          </ol>
        </section>

        <QuestionPicker
          chosenIds={new Set(form.questions.map((q) => q.id))}
          onAdd={(q) => update({ questions: [...form.questions, q] })}
        />

        {message && (
          <p role="alert" className="alert">
            {message}
          </p>
        )}
        <div className="button-row">
          <button type="submit" className="button" disabled={busy}>
            {busy ? 'Saving…' : 'Save quiz'}
          </button>
          <Link to="/admin/quizzes" className="button button--secondary">
            Cancel
          </Link>
        </div>
      </form>
    </main>
  )
}

function QuestionPicker({ chosenIds, onAdd }: { chosenIds: Set<number>; onAdd: (q: Question) => void }) {
  const ids = useId()
  const [query, setQuery] = useState('')
  const [search, setSearch] = useState({ q: '', page: 0 })
  const load = useCallback(() => listQuestions({ q: search.q || undefined, page: search.page, size: 10 }), [search])
  const { data: result, error } = useLoad(load, 'Could not load the question bank.')

  return (
    <section className="panel" aria-labelledby={`${ids}-bank`}>
      <h2 id={`${ids}-bank`}>Question bank</h2>
      {/* Not a nested <form>: pressing Enter here must search, not save the quiz. */}
      <div className="toolbar" role="search">
        <input
          aria-label="Search the question bank"
          placeholder="Search the question bank"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter') {
              e.preventDefault()
              setSearch({ q: query.trim(), page: 0 })
            }
          }}
        />
        <button type="button" className="button" onClick={() => setSearch({ q: query.trim(), page: 0 })}>
          Search
        </button>
      </div>
      {error && (
        <p role="alert" className="alert">
          {error}
        </p>
      )}
      {result && result.content.length === 0 && (
        <p className="empty">
          {search.q ? 'No questions match your search.' : (
            <>
              The question bank is empty. <Link to="/admin/questions/new">Add a question</Link> first.
            </>
          )}
        </p>
      )}
      {result && result.content.length > 0 && (
        <>
          <ul className="bank">
            {result.content.map((q) => (
              <li key={q.id}>
                <span className="chosen__text">{q.text}</span>
                <span className="chosen__time">{formatDuration(q.timeLimitSeconds)}</span>
                <button
                  type="button"
                  className="button button--secondary button--small"
                  disabled={chosenIds.has(q.id)}
                  onClick={() => onAdd(q)}
                  aria-label={chosenIds.has(q.id) ? `Already added: ${q.text}` : `Add: ${q.text}`}
                >
                  {chosenIds.has(q.id) ? 'Added' : 'Add'}
                </button>
              </li>
            ))}
          </ul>
          <Pager page={result} noun="questions" onChange={(page) => setSearch((s) => ({ ...s, page }))} />
        </>
      )}
    </section>
  )
}

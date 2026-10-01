import { useEffect, useId, useState, type FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import {
  createQuestion,
  getQuestion,
  OPTION_LABELS,
  TIME_LIMIT_CHOICES,
  updateQuestion,
  type QuestionInput,
} from '../api/questions'
import { errorMessage, formatDuration, toFormError } from '../components/format'

const MAX_TEXT = 500
const EMPTY: QuestionInput = { text: '', options: ['', '', '', ''], correctOption: -1, timeLimitSeconds: 60 }

/** Adds a new question (/admin/questions/new) or edits one (/admin/questions/:id). */
export function QuestionFormPage() {
  const { id } = useParams()
  const questionId = id === undefined ? null : Number(id)
  const navigate = useNavigate()
  const ids = useId()
  const [form, setForm] = useState<QuestionInput | null>(questionId === null ? EMPTY : null)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [message, setMessage] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    if (questionId === null) return
    let current = true
    getQuestion(questionId).then(
      (q) => current && setForm({ text: q.text, options: q.options, correctOption: q.correctOption, timeLimitSeconds: q.timeLimitSeconds }),
      (err: unknown) => current && setLoadError(errorMessage(err, 'Could not load the question.')),
    )
    return () => {
      current = false
    }
  }, [questionId])

  if (loadError) {
    return (
      <main>
        <p role="alert" className="alert">
          {loadError}
        </p>
        <Link to="/admin/questions">Back to the question bank</Link>
      </main>
    )
  }
  if (!form) return <main aria-busy="true">Loading…</main>

  function setOption(index: number, value: string) {
    setForm((f) => f && { ...f, options: f.options.map((o, i) => (i === index ? value : o)) })
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    if (!form) return
    setMessage(null)
    if (form.correctOption < 0) {
      setFieldErrors({ correctOption: 'Please choose the correct answer' })
      return
    }
    setFieldErrors({})
    setBusy(true)
    try {
      if (questionId === null) await createQuestion(form)
      else await updateQuestion(questionId, form)
      navigate('/admin/questions', { state: { notice: questionId === null ? 'Question added.' : 'Question saved.' } })
    } catch (err) {
      const formError = toFormError(err, 'Could not save the question.')
      setFieldErrors(formError.fieldErrors)
      setMessage(formError.message ?? (Object.keys(formError.fieldErrors).length ? 'Please fix the highlighted fields.' : null))
    } finally {
      setBusy(false)
    }
  }

  const optionsError = fieldErrors.options ?? fieldErrors.correctOption

  return (
    <main>
      <h1>{questionId === null ? 'Add question' : 'Edit question'}</h1>
      <form onSubmit={handleSubmit} noValidate>
        <div className="field">
          <label htmlFor={`${ids}-text`}>Question</label>
          <textarea
            id={`${ids}-text`}
            rows={3}
            maxLength={MAX_TEXT}
            value={form.text}
            onChange={(e) => setForm({ ...form, text: e.target.value })}
            aria-invalid={fieldErrors.text ? true : undefined}
            aria-describedby={`${ids}-text-help`}
          />
          <span id={`${ids}-text-help`} className={fieldErrors.text ? 'field__error' : 'field__hint'}>
            {fieldErrors.text ?? `${form.text.length} / ${MAX_TEXT} characters`}
          </span>
        </div>

        <fieldset className="options" aria-describedby={optionsError ? `${ids}-options-error` : undefined}>
          <legend>Answers — choose the correct one</legend>
          {form.options.map((option, i) => {
            const error = fieldErrors[`options[${i}]`]
            return (
              <div key={OPTION_LABELS[i]} className="option-row">
                <input
                  type="radio"
                  name="correct"
                  id={`${ids}-correct-${i}`}
                  checked={form.correctOption === i}
                  onChange={() => setForm({ ...form, correctOption: i })}
                  aria-label={`Answer ${OPTION_LABELS[i]} is correct`}
                />
                <label htmlFor={`${ids}-option-${i}`} className="option-row__letter">
                  {OPTION_LABELS[i]}
                </label>
                <div className="option-row__input">
                  <input
                    id={`${ids}-option-${i}`}
                    maxLength={200}
                    value={option}
                    onChange={(e) => setOption(i, e.target.value)}
                    aria-label={`Answer ${OPTION_LABELS[i]}`}
                    aria-invalid={error ? true : undefined}
                    aria-describedby={error ? `${ids}-option-${i}-error` : undefined}
                  />
                  {error && (
                    <span id={`${ids}-option-${i}-error`} className="field__error">
                      {error}
                    </span>
                  )}
                </div>
              </div>
            )
          })}
          {optionsError && (
            <span id={`${ids}-options-error`} className="field__error">
              {optionsError}
            </span>
          )}
        </fieldset>

        <div className="field">
          <label htmlFor={`${ids}-time`}>Time to answer</label>
          <select
            id={`${ids}-time`}
            value={form.timeLimitSeconds}
            onChange={(e) => setForm({ ...form, timeLimitSeconds: Number(e.target.value) })}
          >
            {TIME_LIMIT_CHOICES.map((seconds) => (
              <option key={seconds} value={seconds}>
                {formatDuration(seconds)}
              </option>
            ))}
          </select>
          {fieldErrors.timeLimitSeconds && <span className="field__error">{fieldErrors.timeLimitSeconds}</span>}
        </div>

        {message && (
          <p role="alert" className="alert">
            {message}
          </p>
        )}
        <div className="button-row">
          <button type="submit" className="button" disabled={busy}>
            {busy ? 'Saving…' : 'Save question'}
          </button>
          <Link to="/admin/questions" className="button button--secondary">
            Cancel
          </Link>
        </div>
      </form>
    </main>
  )
}

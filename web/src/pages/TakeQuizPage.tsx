import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react'
import { Link, useParams } from 'react-router'
import { ApiError } from '../api/client'
import {
  getAttempt,
  getMyQuiz,
  startAttempt,
  submitAnswer,
  type AttemptResult,
  type AttemptState,
  type MyQuiz,
} from '../api/attempts'
import { OPTION_LABELS } from '../api/questions'
import { errorMessage, quizTimeText } from '../components/format'

/** An attempt as received, with when its timers end on this device's clock. */
interface Running {
  state: AttemptState
  questionEndsAt: number | null
  quizEndsAt: number | null
}

function receive(state: AttemptState): Running {
  const now = Date.now()
  return {
    state,
    questionEndsAt: state.question ? now + state.question.secondsLeft * 1000 : null,
    quizEndsAt: state.quizSecondsLeft === null ? null : now + state.quizSecondsLeft * 1000,
  }
}

/** Re-renders every `intervalMs` while `active`, returning the current time. */
function useNow(intervalMs: number, active: boolean): number {
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => {
    if (!active) return
    const id = setInterval(() => setNow(Date.now()), intervalMs)
    return () => clearInterval(id)
  }, [intervalMs, active])
  return now
}

function secondsUntil(endsAt: number, now: number): number {
  return Math.max(0, Math.ceil((endsAt - now) / 1000))
}

function clock(seconds: number): string {
  return `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`
}

/** /student/quizzes/:quizId — the introduction, the questions one by one, then the score. */
export function TakeQuizPage() {
  const quizId = Number(useParams().quizId)
  const [quiz, setQuiz] = useState<MyQuiz | null>(null)
  const [pastDue, setPastDue] = useState(false)
  const [running, setRunning] = useState<Running | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [starting, setStarting] = useState(false)

  const start = useCallback(async () => {
    setStarting(true)
    setError(null)
    try {
      setRunning(receive(await startAttempt(quizId)))
    } catch (err) {
      setError(errorMessage(err, 'Could not start the quiz. Please try again.'))
    } finally {
      setStarting(false)
    }
  }, [quizId])

  useEffect(() => {
    let current = true
    getMyQuiz(quizId).then(
      (q) => {
        if (!current) return
        setQuiz(q)
        setPastDue(q.dueAt !== null && new Date(q.dueAt).getTime() < Date.now())
        // A quiz already under way carries on straight away; its timer is running.
        if (q.status === 'IN_PROGRESS') void start()
      },
      (err: unknown) => current && setError(errorMessage(err, 'Could not load the quiz.')),
    )
    return () => {
      current = false
    }
  }, [quizId, start])

  if (running?.state.status === 'COMPLETED' && running.state.result) {
    return <Finished title={running.state.quizTitle} result={running.state.result} />
  }
  if (running) {
    // Keyed by question so each one starts with nothing selected.
    return <QuestionScreen key={running.state.question?.position} running={running} onChange={setRunning} />
  }
  if (error) {
    return (
      <main>
        <p role="alert" className="alert">
          {error}
        </p>
        <Link to="/student" className="button button--secondary">
          Back to my quizzes
        </Link>
      </main>
    )
  }
  if (!quiz || quiz.status === 'IN_PROGRESS') return <main aria-busy="true">Loading…</main>
  if (quiz.status === 'COMPLETED') {
    return (
      <main>
        <h1>{quiz.title}</h1>
        <p className="notice">
          You have finished this quiz. Your score: {quiz.score} out of {quiz.questionCount}.
        </p>
        <Link to="/student" className="button">
          Back to my quizzes
        </Link>
      </main>
    )
  }

  return (
    <main className="take">
      <h1>{quiz.title}</h1>
      <ul className="rules">
        <li>
          <span aria-hidden="true">📝</span> {quiz.questionCount} {quiz.questionCount === 1 ? 'question' : 'questions'}
        </li>
        <li>
          <span aria-hidden="true">⏱</span> {quizTimeText(quiz)}
        </li>
        <li>
          <span aria-hidden="true">👉</span> Each question has its own timer. Choose an answer and tap{' '}
          <strong>Submit</strong> before the time runs out.
        </li>
        <li>
          <span aria-hidden="true">🚫</span> You cannot go back to a question.
        </li>
      </ul>
      {pastDue && <p className="alert">This quiz was due earlier. You can still take it.</p>}
      <button type="button" className="button button--big" onClick={start} disabled={starting}>
        {starting ? 'Starting…' : 'Start quiz'}
      </button>
    </main>
  )
}

function QuestionScreen({ running, onChange }: { running: Running; onChange: (r: Running) => void }) {
  const { state } = running
  const question = state.question!
  const [selected, setSelected] = useState<number | null>(null)
  const [sending, setSending] = useState(false)
  const [problem, setProblem] = useState<string | null>(null)
  const headingRef = useRef<HTMLHeadingElement>(null)
  const now = useNow(250, true)
  const left = running.questionEndsAt === null ? 0 : secondsUntil(running.questionEndsAt, now)
  const quizLeft = running.quizEndsAt === null ? null : secondsUntil(running.quizEndsAt, now)

  // Move focus to each new question so screen readers and keyboards start from it.
  useEffect(() => {
    headingRef.current?.focus()
  }, [question.position])

  // Guards against sending twice, e.g. a tap on Submit just as the time runs out.
  const sendingRef = useRef(false)
  const send = useCallback(
    async (option: number | null) => {
      if (sendingRef.current) return
      sendingRef.current = true
      setSending(true)
      setProblem(null)
      try {
        onChange(receive(await submitAnswer(state.attemptId, question.position, option)))
      } catch (err) {
        let failure = err
        if (err instanceof ApiError && err.status === 409) {
          // The server has moved on (e.g. the question was already closed); show where we are.
          try {
            onChange(receive(await getAttempt(state.attemptId)))
            return
          } catch (reloadErr) {
            failure = reloadErr
          }
        }
        setProblem(errorMessage(failure, 'Could not send your answer.'))
      } finally {
        sendingRef.current = false
        setSending(false)
      }
    },
    [state.attemptId, question.position, onChange],
  )

  // When time runs out, tell the server so the next question appears. Only a submitted answer counts.
  const { questionEndsAt } = running
  useEffect(() => {
    if (questionEndsAt === null) return
    const id = setTimeout(() => void send(null), Math.max(0, questionEndsAt - Date.now()))
    return () => clearTimeout(id)
  }, [questionEndsAt, send])
  const timedOut = left === 0

  function handleSubmit(e: FormEvent) {
    e.preventDefault()
    if (selected !== null) void send(selected)
  }

  const fraction = question.timeLimitSeconds > 0 ? left / question.timeLimitSeconds : 0
  return (
    <main className="take">
      <div className="take__bar">
        <span>
          Question {question.position} of {state.questionCount}
        </span>
        {quizLeft !== null && <span>Quiz time left: {clock(quizLeft)}</span>}
      </div>

      <div className={`timer${left <= 10 ? ' timer--low' : ''}`} role="timer" aria-label={`${left} seconds left`}>
        <div className="timer__track" aria-hidden="true">
          <div className="timer__fill" style={{ width: `${Math.min(1, fraction) * 100}%` }} />
        </div>
        <span className="timer__text" aria-hidden="true">
          {left}s
        </span>
      </div>
      <span className="visually-hidden" aria-live="polite">
        {left === 10 ? '10 seconds left' : ''}
      </span>

      <form onSubmit={handleSubmit}>
        <fieldset className="answers" disabled={sending || timedOut}>
          <legend>
            <h1 ref={headingRef} tabIndex={-1} className="take__question">
              {question.text}
            </h1>
          </legend>
          {question.options.map((option, i) => (
            <label key={i} className={`answer${selected === i ? ' answer--selected' : ''}`}>
              <input
                type="radio"
                name={`answer-${question.position}`}
                checked={selected === i}
                onChange={() => setSelected(i)}
              />
              <span className="answer__letter" aria-hidden="true">
                {OPTION_LABELS[i]}
              </span>
              <span className="answer__text">{option}</span>
            </label>
          ))}
        </fieldset>
        {problem && (
          <div role="alert" className="alert">
            <p>{problem}</p>
            <button type="button" className="button" onClick={() => void send(timedOut ? null : selected)}>
              Try again
            </button>
          </div>
        )}
        {timedOut && !problem && <p className="notice">Time is up! Getting the next question…</p>}
        <button type="submit" className="button button--big" disabled={selected === null || sending || timedOut}>
          {sending ? 'Sending…' : 'Submit'}
        </button>
      </form>
    </main>
  )
}

function Finished({ title, result }: { title: string; result: AttemptResult }) {
  const headingRef = useRef<HTMLHeadingElement>(null)
  useEffect(() => headingRef.current?.focus(), [])
  return (
    <main className="take take--done">
      <h1 ref={headingRef} tabIndex={-1}>
        {result.percentage >= 50 ? 'Well done!' : 'Quiz finished'}
      </h1>
      <p>{title}</p>
      <p className="score" aria-label={`You got ${result.score} out of ${result.questionCount}`}>
        <span className="score__number">{result.score}</span>
        <span className="score__total">/ {result.questionCount}</span>
      </p>
      <p className="score__percent">{result.percentage}%</p>
      {result.finishReason === 'TIME_UP' && <p className="field__hint">The time for the quiz ran out.</p>}
      <Link to="/student" className="button button--big">
        Back to my quizzes
      </Link>
    </main>
  )
}

import { useCallback } from 'react'
import { Link, useParams } from 'react-router'
import { getAttemptDetail } from '../api/results'
import { QuestionResult } from '../components/QuestionResult'
import { useLoad } from '../components/useLoad'

const dateFormat = new Intl.DateTimeFormat('en-IN', {
  day: 'numeric',
  month: 'short',
  year: 'numeric',
  hour: 'numeric',
  minute: '2-digit',
})

function when(iso: string | null): string {
  return iso ? dateFormat.format(new Date(iso)) : '—'
}

/** /admin/results/:attemptId — one student's answers to one quiz, with the time taken for each. */
export function AttemptDetailPage() {
  const attemptId = Number(useParams().attemptId)
  const { data: detail, error } = useLoad(
    useCallback(() => getAttemptDetail(attemptId), [attemptId]),
    'Could not load the result.',
  )

  if (error) {
    return (
      <main>
        <p role="alert" className="alert">
          {error}
        </p>
        <Link to="/admin/results">Back to results</Link>
      </main>
    )
  }
  if (!detail) return <main aria-busy="true">Loading…</main>

  const a = detail.attempt
  return (
    <main className="wide">
      <p>
        <Link to={`/admin/results?quizId=${a.quizId}`}>← Results for this quiz</Link>
      </p>
      <h1>
        {a.studentName} · {a.quizTitle}
      </h1>
      <dl className="facts">
        <div>
          <dt>Score</dt>
          <dd>{a.status === 'COMPLETED' ? `${a.score} / ${a.questionCount} (${a.percentage}%)` : 'Not finished'}</dd>
        </div>
        <div>
          <dt>Mobile</dt>
          <dd>{a.studentMobile}</dd>
        </div>
        {detail.studentSchool && (
          <div>
            <dt>School</dt>
            <dd>{detail.studentSchool}</dd>
          </div>
        )}
        <div>
          <dt>Started</dt>
          <dd>{when(a.startedAt)}</dd>
        </div>
        <div>
          <dt>Finished</dt>
          <dd>
            {when(a.finishedAt)}
            {detail.finishReason === 'TIME_UP' && ' (quiz time ran out)'}
          </dd>
        </div>
        {detail.dueAt && (
          <div>
            <dt>Due</dt>
            <dd>
              {when(detail.dueAt)}
              {a.late && <span className="badge badge--off late-badge">Started late</span>}
            </dd>
          </div>
        )}
      </dl>

      <ol className="result-questions" aria-label="Answers">
        {detail.questions.map((q) => (
          <QuestionResult
            key={q.position}
            question={q}
            time={{ secondsTaken: q.secondsTaken, timeLimitSeconds: q.timeLimitSeconds }}
          />
        ))}
      </ol>
    </main>
  )
}

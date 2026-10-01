import { useCallback } from 'react'
import { Link } from 'react-router'
import { listMyResults } from '../api/results'
import { useLoad } from '../components/useLoad'

const dateFormat = new Intl.DateTimeFormat('en-IN', { day: 'numeric', month: 'short', year: 'numeric' })

/** /student/results — every quiz the student has finished. */
export function MyResultsPage() {
  const { data: results, error } = useLoad(useCallback(() => listMyResults(), []), 'Could not load your scores.')

  return (
    <main>
      <h1>My scores</h1>
      {error && (
        <p role="alert" className="alert">
          {error}
        </p>
      )}
      {results && results.length === 0 && <p className="empty">You have not finished any quizzes yet.</p>}
      {results && results.length > 0 && (
        <ul className="quiz-cards">
          {results.map((r) => (
            <li key={r.attemptId} className="quiz-card quiz-card--done">
              <div className="quiz-card__title">
                <h2>{r.quizTitle}</h2>
                <span className="result-percent">{r.percentage}%</span>
              </div>
              <p className="quiz-card__facts">
                <span>
                  Score: {r.score} out of {r.questionCount}
                </span>
                <span>{dateFormat.format(new Date(r.finishedAt))}</span>
              </p>
              <Link
                to={`/student/results/${r.attemptId}`}
                className="button button--secondary quiz-card__action"
                aria-label={`See results for ${r.quizTitle}`}
              >
                See results
              </Link>
            </li>
          ))}
        </ul>
      )}
    </main>
  )
}

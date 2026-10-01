import { useCallback } from 'react'
import { Link, useParams } from 'react-router'
import { getMyReview } from '../api/results'
import { QuestionResult } from '../components/QuestionResult'
import { useLoad } from '../components/useLoad'

/** /student/results/:attemptId — the score, and each answer if the teacher allows it. */
export function ResultReviewPage() {
  const attemptId = Number(useParams().attemptId)
  const { data: review, error } = useLoad(
    useCallback(() => getMyReview(attemptId), [attemptId]),
    'Could not load your results.',
  )

  if (error) {
    return (
      <main>
        <p role="alert" className="alert">
          {error}
        </p>
        <Link to="/student/results">Back to my scores</Link>
      </main>
    )
  }
  if (!review) return <main aria-busy="true">Loading…</main>

  return (
    <main>
      <p>
        <Link to="/student/results">← My scores</Link>
      </p>
      <h1>{review.quizTitle}</h1>
      <p className="score" aria-label={`You got ${review.score} out of ${review.questionCount}`}>
        <span className="score__number">{review.score}</span>
        <span className="score__total">/ {review.questionCount}</span>
        <span className="result-percent"> {review.percentage}%</span>
      </p>
      {review.finishReason === 'TIME_UP' && <p className="field__hint">The time for the quiz ran out.</p>}

      {review.answersShown ? (
        <ol className="result-questions" aria-label="Your answers">
          {review.questions.map((q) => (
            <QuestionResult key={q.position} question={q} />
          ))}
        </ol>
      ) : (
        <p className="notice">Your teacher will go through the answers with you.</p>
      )}
    </main>
  )
}

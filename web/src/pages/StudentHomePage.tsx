import { useCallback } from 'react'
import { listMyQuizzes, type MyQuiz } from '../api/assignments'
import { useAuth } from '../auth/authContext'
import { formatDuration } from '../components/format'
import { useLoad } from '../components/useLoad'

const dateFormat = new Intl.DateTimeFormat('en-IN', { weekday: 'short', day: 'numeric', month: 'short' })

function timeText(quiz: MyQuiz): string {
  return quiz.totalTimeLimitSeconds === null
    ? `Up to ${formatDuration(quiz.questionTimeSeconds)}`
    : formatDuration(quiz.totalTimeLimitSeconds)
}

export function StudentHomePage() {
  const { user } = useAuth()
  const { data: quizzes, error } = useLoad(useCallback(() => listMyQuizzes(), []), 'Could not load your quizzes.')
  const newCount = quizzes?.filter((q) => q.status === 'NEW').length ?? 0

  return (
    <main>
      <h1>Hello, {user?.name}!</h1>

      {error && (
        <p role="alert" className="alert">
          {error}
        </p>
      )}

      {quizzes && quizzes.length === 0 && (
        <p className="empty">You have no quizzes yet. Your teacher will add them soon.</p>
      )}

      {newCount > 0 && (
        <p role="status" className="notice">
          You have {newCount} new {newCount === 1 ? 'quiz' : 'quizzes'}!
        </p>
      )}

      {quizzes && quizzes.length > 0 && (
        <ul className="quiz-cards" aria-label="Your quizzes">
          {quizzes.map((quiz) => (
            <li key={quiz.quizId} className="quiz-card">
              <div className="quiz-card__title">
                <h2>{quiz.title}</h2>
                {quiz.status === 'NEW' && <span className="badge badge--new">New</span>}
              </div>
              <p className="quiz-card__facts">
                <span>
                  <span aria-hidden="true">📝</span> {quiz.questionCount} {quiz.questionCount === 1 ? 'question' : 'questions'}
                </span>
                <span>
                  <span aria-hidden="true">⏱</span> {timeText(quiz)}
                </span>
                {quiz.dueAt && (
                  <span>
                    <span aria-hidden="true">📅</span> Finish by {dateFormat.format(new Date(quiz.dueAt))}
                  </span>
                )}
              </p>
            </li>
          ))}
        </ul>
      )}
    </main>
  )
}

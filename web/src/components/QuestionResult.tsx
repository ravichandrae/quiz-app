import { OPTION_LABELS } from '../api/questions'
import type { Outcome, ReviewQuestion } from '../api/results'
import { formatDuration } from './format'

const OUTCOME_TEXT: Record<Outcome, { label: string; icon: string; className: string }> = {
  CORRECT: { label: 'Right', icon: '✓', className: 'outcome--right' },
  WRONG: { label: 'Wrong', icon: '✗', className: 'outcome--wrong' },
  NO_ANSWER: { label: 'Time ran out', icon: '⏱', className: 'outcome--wrong' },
  NOT_REACHED: { label: 'Not reached', icon: '–', className: 'outcome--muted' },
}

interface QuestionResultProps {
  question: ReviewQuestion
  /** For teachers: how long the student took, out of the time allowed. */
  time?: { secondsTaken: number | null; timeLimitSeconds: number }
}

/** One question after a quiz: each option, which one was chosen, and which one was correct. */
export function QuestionResult({ question, time }: QuestionResultProps) {
  const outcome = OUTCOME_TEXT[question.outcome]
  return (
    <li className="result-question">
      <div className="result-question__head">
        <h3>
          <span className="muted">{question.position}.</span> {question.text}
        </h3>
        <span className={`outcome ${outcome.className}`}>
          <span aria-hidden="true">{outcome.icon}</span> {outcome.label}
        </span>
      </div>
      <ul className="result-options">
        {question.options.map((option, i) => {
          const chosen = question.selectedOption === i
          const correct = question.correctOption === i
          const notes = [chosen && 'your answer', correct && 'correct answer'].filter(Boolean).join(', ')
          return (
            <li
              key={i}
              className={`result-option${correct ? ' result-option--correct' : ''}${chosen && !correct ? ' result-option--chosen-wrong' : ''}`}
            >
              <span className="answer__letter" aria-hidden="true">
                {OPTION_LABELS[i]}
              </span>
              <span>{option}</span>
              {notes && <span className="result-option__note">({notes})</span>}
            </li>
          )
        })}
      </ul>
      {time && (
        <p className="field__hint">
          Time taken:{' '}
          {time.secondsTaken === null ? '—' : `${formatDuration(time.secondsTaken)} of ${formatDuration(time.timeLimitSeconds)}`}
        </p>
      )}
    </li>
  )
}

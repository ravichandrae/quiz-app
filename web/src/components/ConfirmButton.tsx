import { useState } from 'react'

interface ConfirmButtonProps {
  /** Button text, e.g. "Delete". */
  label: string
  /** Accessible name of the first button, e.g. "Delete Science quiz". */
  ariaLabel: string
  /** The question shown before acting, e.g. "Delete this quiz?". */
  question: string
  onConfirm: () => Promise<void> | void
}

/** A button that asks "Are you sure?" on the page itself before acting. */
export function ConfirmButton({ label, ariaLabel, question, onConfirm }: ConfirmButtonProps) {
  const [asking, setAsking] = useState(false)
  const [busy, setBusy] = useState(false)

  if (!asking) {
    return (
      <button
        type="button"
        className="button button--secondary button--small"
        aria-label={ariaLabel}
        onClick={() => setAsking(true)}
      >
        {label}
      </button>
    )
  }

  async function confirm() {
    setBusy(true)
    try {
      await onConfirm()
    } finally {
      setBusy(false)
      setAsking(false)
    }
  }

  return (
    <span className="confirm" role="group" aria-label={question}>
      <span>{question}</span>
      <button type="button" className="button button--danger button--small" disabled={busy} onClick={confirm}>
        Yes, {label.toLowerCase()}
      </button>
      <button type="button" className="button button--secondary button--small" onClick={() => setAsking(false)}>
        No
      </button>
    </span>
  )
}

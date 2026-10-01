import { useCallback, useEffect, useState } from 'react'
import { errorMessage } from './format'

interface LoadState<T> {
  data: T | null
  error: string | null
  /** Replaces the loaded data locally, e.g. after an update the server confirmed. */
  setData: (data: T) => void
  /** Loads again, e.g. after deleting an item from a page of results. */
  reload: () => void
}

/**
 * Runs `load` whenever it changes (wrap it in useCallback with its inputs) and keeps the latest
 * result. Responses to outdated calls are ignored, so a slow old search never replaces a newer one.
 */
export function useLoad<T>(load: () => Promise<T>, fallbackError: string): LoadState<T> {
  const [data, setData] = useState<T | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [version, setVersion] = useState(0)

  useEffect(() => {
    let current = true
    load().then(
      (next) => {
        if (!current) return
        setData(next)
        setError(null)
      },
      (err: unknown) => {
        if (current) setError(errorMessage(err, fallbackError))
      },
    )
    return () => {
      current = false
    }
  }, [load, version, fallbackError])

  const reload = useCallback(() => setVersion((v) => v + 1), [])
  return { data, error, setData, reload }
}

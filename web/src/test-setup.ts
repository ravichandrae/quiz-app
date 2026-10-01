import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterEach, vi } from 'vitest'
import { setSession } from './api/client'

afterEach(() => {
  cleanup()
  setSession(null)
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})

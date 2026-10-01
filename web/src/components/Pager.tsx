import type { Page } from '../api/admin'

interface PagerProps {
  page: Page<unknown>
  noun: string
  onChange: (page: number) => void
}

export function Pager({ page, noun, onChange }: PagerProps) {
  if (page.totalPages <= 1) return null
  return (
    <nav className="pager" aria-label="Pages">
      <button
        type="button"
        className="button button--secondary button--small"
        disabled={page.page === 0}
        onClick={() => onChange(page.page - 1)}
      >
        Previous
      </button>
      <span>
        Page {page.page + 1} of {page.totalPages} · {page.totalElements} {noun}
      </span>
      <button
        type="button"
        className="button button--secondary button--small"
        disabled={page.page + 1 >= page.totalPages}
        onClick={() => onChange(page.page + 1)}
      >
        Next
      </button>
    </nav>
  )
}

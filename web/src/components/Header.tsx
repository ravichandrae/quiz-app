import { NavLink, useNavigate } from 'react-router'
import { useAuth } from '../auth/authContext'

const ADMIN_LINKS = [
  { to: '/admin/users', label: 'Students' },
  { to: '/admin/questions', label: 'Questions' },
  { to: '/admin/quizzes', label: 'Quizzes' },
]

export function Header() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()

  async function handleLogout() {
    await logout()
    navigate('/login', { replace: true })
  }

  return (
    <header className="header">
      <span className="header__brand">Schoolmela Quiz</span>
      {user?.role === 'ADMIN' && (
        <nav className="header__nav" aria-label="Main">
          {ADMIN_LINKS.map((link) => (
            <NavLink key={link.to} to={link.to}>
              {link.label}
            </NavLink>
          ))}
        </nav>
      )}
      {user && (
        <span className="header__user">
          <span>{user.name}</span>
          <button type="button" className="button button--secondary" onClick={handleLogout}>
            Log out
          </button>
        </span>
      )}
    </header>
  )
}

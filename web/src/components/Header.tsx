import { NavLink, useNavigate } from 'react-router'
import { useAuth } from '../auth/authContext'

const ADMIN_LINKS = [
  { to: '/admin/users', label: 'Students' },
  { to: '/admin/groups', label: 'Groups' },
  { to: '/admin/questions', label: 'Questions' },
  { to: '/admin/quizzes', label: 'Quizzes' },
  { to: '/admin/results', label: 'Results' },
]

const STUDENT_LINKS = [
  { to: '/student', label: 'My quizzes' },
  { to: '/student/results', label: 'My scores' },
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
      {user && (
        <nav className="header__nav" aria-label="Main">
          {(user.role === 'ADMIN' ? ADMIN_LINKS : STUDENT_LINKS).map((link) => (
            // "end" stops "My quizzes" (/student) from looking active on /student/results.
            <NavLink key={link.to} to={link.to} end={link.to === '/student'}>
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

import { useNavigate } from 'react-router'
import { useAuth } from '../auth/authContext'

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

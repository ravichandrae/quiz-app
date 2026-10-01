import { useAuth } from '../auth/authContext'

export function StudentHomePage() {
  const { user } = useAuth()
  return (
    <main>
      <h1>Hello, {user?.name}!</h1>
      <p className="empty">You have no quizzes yet. Your teacher will add them soon.</p>
    </main>
  )
}

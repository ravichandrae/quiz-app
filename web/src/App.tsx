import type { ComponentType } from 'react'
import { Navigate, Route, Routes } from 'react-router'
import { homePathFor, useAuth } from './auth/authContext'
import { RequireRole } from './auth/RequireRole'
import { Header } from './components/Header'
import { AdminUsersPage } from './pages/AdminUsersPage'
import { GroupDetailPage } from './pages/GroupDetailPage'
import { GroupsPage } from './pages/GroupsPage'
import { LoginPage } from './pages/LoginPage'
import { QuestionFormPage } from './pages/QuestionFormPage'
import { QuestionsPage } from './pages/QuestionsPage'
import { QuizAssignPage } from './pages/QuizAssignPage'
import { QuizFormPage } from './pages/QuizFormPage'
import { QuizzesPage } from './pages/QuizzesPage'
import { RegisterPage } from './pages/RegisterPage'
import { StudentHomePage } from './pages/StudentHomePage'

const ADMIN_ROUTES: [string, ComponentType][] = [
  ['/admin/users', AdminUsersPage],
  ['/admin/questions', QuestionsPage],
  ['/admin/questions/new', QuestionFormPage],
  ['/admin/questions/:id', QuestionFormPage],
  ['/admin/quizzes', QuizzesPage],
  ['/admin/quizzes/new', QuizFormPage],
  ['/admin/quizzes/:id', QuizFormPage],
  ['/admin/quizzes/:id/assign', QuizAssignPage],
  ['/admin/groups', GroupsPage],
  ['/admin/groups/:id', GroupDetailPage],
]

function App() {
  const { user } = useAuth()
  const home = user ? homePathFor(user.role) : '/login'

  return (
    <>
      <Header />
      <Routes>
        <Route path="/login" element={user ? <Navigate to={home} replace /> : <LoginPage />} />
        <Route path="/register" element={user ? <Navigate to={home} replace /> : <RegisterPage />} />
        <Route
          path="/student"
          element={
            <RequireRole role="STUDENT">
              <StudentHomePage />
            </RequireRole>
          }
        />
        {ADMIN_ROUTES.map(([path, Page]) => (
          <Route
            key={path}
            path={path}
            element={
              <RequireRole role="ADMIN">
                <Page />
              </RequireRole>
            }
          />
        ))}
        <Route path="*" element={<Navigate to={home} replace />} />
      </Routes>
    </>
  )
}

export default App

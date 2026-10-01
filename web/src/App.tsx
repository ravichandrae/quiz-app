import { Navigate, Route, Routes } from 'react-router'
import { homePathFor, useAuth } from './auth/authContext'
import { RequireRole } from './auth/RequireRole'
import { Header } from './components/Header'
import { AdminUsersPage } from './pages/AdminUsersPage'
import { LoginPage } from './pages/LoginPage'
import { RegisterPage } from './pages/RegisterPage'
import { StudentHomePage } from './pages/StudentHomePage'

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
        <Route
          path="/admin/users"
          element={
            <RequireRole role="ADMIN">
              <AdminUsersPage />
            </RequireRole>
          }
        />
        <Route path="*" element={<Navigate to={home} replace />} />
      </Routes>
    </>
  )
}

export default App

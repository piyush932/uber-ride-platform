import { useEffect, useState } from 'react'
import { Navigate, Route, Routes, useNavigate } from 'react-router-dom'
import DriverAuth from './DriverAuth.jsx'
import DriverDashboard from './DriverDashboard.jsx'
import {
  clearDriverAuth,
  getDriverToken,
  getDriverUser,
} from './api.js'

export default function DriverApp() {
  const navigate = useNavigate()
  const [driver, setDriver] = useState(getDriverUser())
  const [checking, setChecking] = useState(Boolean(getDriverToken()))
  const [notice, setNotice] = useState('')

  useEffect(() => {
    let alive = true

    const expired = () => {
      setDriver(null)
      setNotice('Your driver session expired. Please log in again.')
    }

    window.addEventListener('driver-session-expired', expired)

    const token = getDriverToken()

    if (!token) {
      setChecking(false)
    } else {
      import('./api.js')
        .then(({ getDriverProfile }) => getDriverProfile())
        .then(profile => {
          if (alive) setDriver(profile)
        })
        .catch(() => {
          if (alive) {
            clearDriverAuth()
            setDriver(null)
            setNotice('Please log in again.')
          }
        })
        .finally(() => {
          if (alive) setChecking(false)
        })
    }

    return () => {
      alive = false
      window.removeEventListener('driver-session-expired', expired)
    }
  }, [])

  function protect(screen) {
    if (checking) {
      return <p className="content-card" role="status">Checking driver session…</p>
    }

    return driver ? screen : <Navigate to="/driver/login" replace />
  }

  return (
    <div className="app driver-app">
      <header className="topbar">
        <span className="brand">
          ride<span>.</span> driver
        </span>

        <nav aria-label="Driver navigation">
          <span>Driver portal</span>
        </nav>

        <div className="account">
          {driver ? (
            <>
              <span className="username">
                {driver.name || driver.email || `Driver #${driver.id}`}
              </span>
              <button
                className="secondary"
                onClick={() => {
                  clearDriverAuth()
                  setDriver(null)
                  navigate('/driver/login')
                }}
              >
                Log out
              </button>
            </>
          ) : (
            <span className="login-link">Not signed in</span>
          )}
        </div>
      </header>

      {notice && (
        <div className="notice" role="status">
          {notice}
        </div>
      )}

      <main>
        <Routes>
          <Route path="/" element={<Navigate to="/driver/login" replace />} />
          <Route
            path="/login"
            element={
              <DriverAuth
                onLogin={user => {
                  setDriver(user)
                  setNotice('')
                }}
              />
            }
          />
          <Route
            path="/dashboard"
            element={protect(<DriverDashboard driver={driver} onLogout={() => setDriver(null)} />)}
          />
          <Route path="*" element={<Navigate to="/driver/login" replace />} />
        </Routes>
      </main>

      <footer>Ride Platform · Driver portal · Stage 9B</footer>
    </div>
  )
}

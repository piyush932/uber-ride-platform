import { useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { driverLogin, driverSignup } from './api.js'

const errorText = e => {
  const data = e.response?.data
  return (
    data?.message ||
    data?.error ||
    e.message ||
    'Driver authentication failed. Please try again.'
  )
}

export default function DriverAuth({ onLogin }) {
  const navigate = useNavigate()
  const location = useLocation()
  const [mode, setMode] = useState('login')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  async function submit(event) {
    event.preventDefault()
    setBusy(true)
    setError('')

    const form = new FormData(event.currentTarget)
    const email = form.get('email')
    const password = form.get('password')
    const name = form.get('name')

    try {
      const user =
        mode === 'signup'
          ? await driverSignup(email, password, name)
          : await driverLogin(email, password)

      onLogin(user)
      navigate(location.state?.from || '/driver/dashboard', {
        replace: true,
      })
    } catch (e) {
      setError(errorText(e))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="auth-layout">
      <section className="intro">
        <p className="eyebrow">DRIVER PORTAL</p>
        <h1>
          Go online.
          <br />
          Accept your next ride.
        </h1>
        <p>
          Set your current location, stay available, and manage your driver
          session from one dashboard.
        </p>
        <div className="feature-tags">
          <span>Location heartbeat</span>
          <span>Online status</span>
          <span>Driver dashboard</span>
        </div>
      </section>

      <section className="card auth-card">
        <p className="eyebrow">DRIVER ACCOUNT</p>
        <h2>
          {mode === 'signup' ? 'Create driver account' : 'Driver log in'}
        </h2>
        <p className="muted">
          {mode === 'signup'
            ? 'Register as a driver to access the driver dashboard.'
            : 'Log in to update your location and availability.'}
        </p>

        <form onSubmit={submit}>
          {mode === 'signup' && (
            <label>
              Full name
              <input
                name="name"
                autoComplete="name"
                required
                maxLength={100}
              />
            </label>
          )}

          <label>
            Email address
            <input
              name="email"
              type="email"
              autoComplete="email"
              required
            />
          </label>

          <label>
            Password
            <input
              name="password"
              type="password"
              autoComplete={
                mode === 'signup' ? 'new-password' : 'current-password'
              }
              minLength={6}
              maxLength={72}
              required
            />
          </label>

          {error && (
            <p className="error" role="alert">
              {error}
            </p>
          )}

          <button disabled={busy}>
            {busy
              ? 'Please wait…'
              : mode === 'signup'
                ? 'Create driver account →'
                : 'Log in →'}
          </button>
        </form>

        <button
          className="text-button"
          disabled={busy}
          onClick={() => {
            setMode(mode === 'login' ? 'signup' : 'login')
            setError('')
          }}
        >
          {mode === 'login'
            ? 'New driver? Create an account'
            : 'Already registered? Log in'}
        </button>

        <p className="hint">
          Passenger? <Link to="/login">Use the passenger login</Link>
        </p>
      </section>
    </div>
  )
}

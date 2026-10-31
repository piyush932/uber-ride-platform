import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { login, signup } from '../api.js'

export default function Login() {
  const navigate = useNavigate()
  const [mode, setMode] = useState('login')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  const submit = async (event) => {
    event.preventDefault()
    setError('')
    setBusy(true)
    try {
      if (mode === 'signup') {
        await signup(email, password)
      }
      await login(email, password)
      navigate('/book')
    } catch (err) {
      setError(err.response?.data?.message || err.response?.data?.error || 'Something went wrong')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="card auth-card">
      <h1>{mode === 'login' ? 'Welcome back' : 'Create account'}</h1>
      <form onSubmit={submit}>
        <label htmlFor="email">Email</label>
        <input id="email" type="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
        <label htmlFor="password">Password</label>
        <input id="password" type="password" value={password} onChange={(e) => setPassword(e.target.value)} required minLength={6} />
        {error && <p className="error">{error}</p>}
        <button type="submit" disabled={busy}>{busy ? 'Please wait…' : mode === 'login' ? 'Log in' : 'Sign up'}</button>
      </form>
      <p className="hint">
        {mode === 'login' ? 'New here? ' : 'Already have an account? '}
        <button className="ghost" type="button" onClick={() => setMode(mode === 'login' ? 'signup' : 'login')}>
          {mode === 'login' ? 'Create one' : 'Log in'}
        </button>
      </p>
    </div>
  )
}

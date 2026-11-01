import { useEffect, useRef, useState } from 'react'
import { Link, Navigate, Route, Routes, useLocation, useNavigate, useParams } from 'react-router-dom'
import MapView from './components/MapView.jsx'
import usePlace from './usePlace.js'
import api, { getToken, clearAuth, login, signup, getBooking, myBookings, createBooking, cancelBooking, completeBooking } from './api.js'
const errorText = e => {
  const data = e.response?.data
  if (data?.message) return data.message
  if (e.response?.status === 409) return 'This request could not be completed. Check that a driver is online near your pickup.'
  return data?.error || e.message || 'Request failed. Please try again.'
}
const terminal = status => ['COMPLETED', 'CANCELLED'].includes(status)
const coords = p => p ? p.map(n => Number(n).toFixed(5)).join(', ') : 'Select on the map'
function Place({ point, empty = 'Select on the map' }) {
  const { place, loading } = usePlace(point)
  if (!point || point[0] == null || point[1] == null) return <>{empty}</>
  if (loading) return <>Finding address…</>
  return <span title={place?.full ? `${place.full} (${coords(point)})` : coords(point)}>{place?.label || coords(point)}</span>
}
function Auth({ onLogin }) {
  const location = useLocation(), navigate = useNavigate()
  const [mode, setMode] = useState('login'), [busy, setBusy] = useState(false), [error, setError] = useState('')
  async function submit(e) {
    e.preventDefault(); setBusy(true); setError('')
    const form = new FormData(e.currentTarget)
    try {
      if (mode === 'signup') await signup(form.get('email'), form.get('password'), form.get('name'))
      onLogin(await login(form.get('email'), form.get('password')))
      navigate(location.state?.from || '/book', { replace: true })
    } catch (e) { setError(errorText(e)) } finally { setBusy(false) }
  }
  return <div className="auth-layout"><section className="intro"><p className="eyebrow">YOUR CITY. YOUR NEXT CHAPTER.</p><h1>Good journeys<br />start here.</h1><p>Choose a pickup. Find your next destination. Keep every ride in one place.</p><div className="feature-tags"><span>Map-first booking</span><span>Ride history</span><span>Status updates</span></div></section><section className="card auth-card"><p className="eyebrow">PASSENGER ACCOUNT</p><h2>{mode === 'signup' ? 'Create your account' : 'Welcome back'}</h2><p className="muted">{mode === 'signup' ? 'A few details and you are ready to explore.' : 'Log in to book and manage your rides.'}</p><form onSubmit={submit}>
    {mode === 'signup' && <label>Full name<input name="name" autoComplete="name" required maxLength={100} /></label>}
    <label>Email address<input name="email" type="email" autoComplete="email" required /></label><label>Password<input name="password" type="password" autoComplete={mode === 'signup' ? 'new-password' : 'current-password'} minLength={6} maxLength={72} required /></label>
    {error && <p className="error" role="alert">{error}</p>}<button disabled={busy}>{busy ? 'Please wait…' : mode === 'signup' ? 'Create account →' : 'Log in →'}</button></form><button className="text-button" disabled={busy} onClick={() => { setMode(mode === 'login' ? 'signup' : 'login'); setError('') }}>{mode === 'login' ? 'New here? Create an account' : 'Already registered? Log in'}</button></section></div>
}
function Book({ user, checking }) {
  const navigate = useNavigate()
  const [pickup, setPickup] = useState(null), [dropoff, setDropoff] = useState(null), [selecting, setSelecting] = useState('pickup'), [busy, setBusy] = useState(false), [error, setError] = useState('')
  const key = useRef(null), inFlight = useRef(false)
  function choose(point) {
    if (busy) return
    if (selecting === 'pickup') { setPickup(point); setSelecting('dropoff') } else setDropoff(point)
    key.current = null; setError('')
  }
  async function book() {
    if (!user) { navigate('/login', { state: { from: '/book' } }); return }
    if (!pickup || !dropoff || inFlight.current) return
    inFlight.current = true; setBusy(true); setError('')
    try {
      key.current ||= crypto.randomUUID()
      const { data } = await createBooking({ passengerId: user.id, pickupLatitude: pickup[0], pickupLongitude: pickup[1], dropLatitude: dropoff[0], dropLongitude: dropoff[1] }, key.current)
      navigate(`/rides/${data.id}`)
    } catch (e) {
      // A real answer from the server is final, so the next attempt gets a new key. The key is only reused after a network failure or timeout.
      if (e.response) key.current = null
      setError(errorText(e))
    } finally { setBusy(false); inFlight.current = false }
  }
  return <div className="booking-layout"><aside className="booking-panel"><p className="eyebrow">LET’S GET YOU THERE</p><h1>Where to next?</h1><p className="muted">Your next ride is a couple of points away.</p><div className="selection"><button className={`point ${selecting === 'pickup' ? 'selected' : ''}`} disabled={busy} onClick={() => setSelecting('pickup')}><span className="point-icon">●</span><span><strong>Pickup location</strong><small><Place point={pickup} /></small></span></button><button className={`point ${selecting === 'dropoff' ? 'selected' : ''}`} disabled={busy} onClick={() => setSelecting('dropoff')}><span className="point-icon">■</span><span><strong>Destination</strong><small><Place point={dropoff} /></small></span></button></div><p className="hint">Click the map to set your {selecting === 'pickup' ? 'pickup' : 'destination'}. Select a field above to edit it.</p>{error && <p className="error" role="alert">{error}</p>}<button disabled={busy || checking || (!!user && (!pickup || !dropoff))} onClick={book}>{checking ? 'Checking session…' : busy ? 'Requesting ride…' : user ? 'Request a ride →' : 'Log in to book →'}</button>{!user && <p className="hint">New passenger? <Link to="/login">Create an account</Link></p>}<div className="info-box"><strong>Built for a simple journey</strong><p>Choose your locations, request a ride and follow its status.</p><small>The dotted line connects your selections. It is not a driving route. A nearby online driver is required. Place names © OpenStreetMap contributors.</small></div></aside><MapView pickup={pickup} dropoff={dropoff} onClick={choose} /></div>
}
function Ride() {
  const { id } = useParams()
  const [ride, setRide] = useState(null), [error, setError] = useState(''), [busy, setBusy] = useState(false)
  const generation = useRef(0)
  useEffect(() => {
    let alive = true, timer
    setRide(null); setError('')
    async function load() {
      const version = generation.current
      try {
        const { data } = await getBooking(id)
        if (!alive) return
        if (version === generation.current) { setRide(data); setError('') }
        if (!terminal(data.status)) timer = setTimeout(load, 3000)
      } catch (e) { if (alive) { setError(errorText(e)); if (e.response?.status !== 401) timer = setTimeout(load, 5000) } }
    }
    load(); return () => { alive = false; clearTimeout(timer) }
  }, [id])
  async function act(action) {
    setBusy(true); setError(''); generation.current++
    try { await action(id); const { data } = await getBooking(id); setRide(data) } catch (e) { setError(errorText(e)) } finally { setBusy(false) }
  }
  return <section className="content-card card"><Link to="/bookings">← My rides</Link><p className="eyebrow">YOUR JOURNEY</p><h1>Ride #{id}</h1>{error && <p className="error" role="alert">{error}</p>}{!ride ? <p role="status">Loading ride…</p> : <><span className={`chip ${ride.status}`}>{ride.status}</span><ol className="timeline">{['PENDING', 'CONFIRMED', 'COMPLETED'].map(s => <li key={s} className={s === ride.status ? 'active' : ''}>{s}</li>)}</ol>{ride.driverId && <p>Driver #{ride.driverId}</p>}{ride.cancelReason && <p>{ride.cancelReason}</p>}<p className="muted">Pickup: <Place point={[ride.pickupLatitude, ride.pickupLongitude]} empty="Not available" /></p><p className="muted">Destination: <Place point={[ride.dropLatitude, ride.dropLongitude]} empty="Not available" /></p><div className="actions">{!terminal(ride.status) && <button className="secondary" disabled={busy} onClick={() => act(cancelBooking)}>Cancel ride</button>}{ride.status === 'CONFIRMED' && <button disabled={busy} onClick={() => act(completeBooking)}>Complete ride</button>}</div><p className="hint">{terminal(ride.status) ? 'This journey has ended.' : 'Status updates automatically. Driver acceptance is handled outside this passenger app.'}</p></>}</section>
}
function History({ user }) {
  const [rides, setRides] = useState(null), [error, setError] = useState('')
  useEffect(() => { let alive = true; myBookings(user.id).then(({ data }) => { if (alive) setRides(data) }).catch(e => { if (alive) setError(errorText(e)) }); return () => { alive = false } }, [user.id])
  return <section className="content-card"><p className="eyebrow">YOUR TRAVEL JOURNAL</p><h1>My rides</h1>{error ? <p className="error" role="alert">{error}</p> : !rides ? <p role="status">Loading rides…</p> : rides.length === 0 ? <div className="card"><h2>Your next journey starts here</h2><p>No rides yet.</p><Link to="/book">Book your first ride →</Link></div> : <div className="booking-list">{rides.map(r => <Link className="booking-row" key={r.id} to={`/rides/${r.id}`}><span><strong>Ride #{r.id}</strong><small>View journey →</small></span><span className={`chip ${r.status}`}>{r.status}</span></Link>)}</div>}</section>
}
export default function App() {
  const [user, setUser] = useState(null), [checking, setChecking] = useState(true), [notice, setNotice] = useState('')
  const navigate = useNavigate()
  useEffect(() => {
    let alive = true
    const expired = () => { setUser(null); setNotice('Your session expired. Please log in again.') }
    window.addEventListener('session-expired', expired)
    if (!getToken()) setChecking(false)
    else api.get('/api/v1/auth/me').then(({ data }) => { if (alive) setUser(data) }).catch(e => { if (alive) setNotice(e.response?.status === 401 ? 'Please log in again.' : 'We could not verify your session. Please try logging in.') }).finally(() => { if (alive) setChecking(false) })
    return () => { alive = false; window.removeEventListener('session-expired', expired) }
  }, [])
  function protect(screen) { return checking ? <p className="content-card" role="status">Checking session…</p> : user ? screen : <Navigate to="/login" replace /> }
  return <div className="app"><header className="topbar"><Link className="brand" to="/book">ride<span>.</span></Link><nav aria-label="Main navigation"><Link to="/book">Book a ride</Link><Link to="/bookings">My rides</Link></nav><div className="account">{user ? <><span className="username">{user.name || user.email}</span><button className="secondary" onClick={() => { clearAuth(); setUser(null); navigate('/book') }}>Log out</button></> : <Link className="login-link" to="/login">Log in / Sign up</Link>}</div></header>{notice && <div className="notice" role="status">{notice}</div>}<main><Routes><Route path="/" element={<Navigate to="/book" replace />} /><Route path="/book" element={<Book user={user} checking={checking} />} /><Route path="/login" element={<Auth onLogin={u => { setUser(u); setNotice('') }} />} /><Route path="/bookings" element={protect(<History user={user} />)} /><Route path="/rides/:id" element={protect(<Ride />)} /><Route path="*" element={<Navigate to="/book" replace />} /></Routes></main><footer>Ride Platform · Passenger preview · Local development</footer></div>
}

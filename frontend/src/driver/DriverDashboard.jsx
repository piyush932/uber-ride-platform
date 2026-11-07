import { useCallback, useEffect, useRef, useState } from 'react'
import MapView from '../components/MapView.jsx'
import usePlace from '../usePlace.js'
import {
  acceptOffer,
  clearDriverAuth,
  completeRide,
  getDashboard,
  getDriverHistory,
  getDriverProfile,
  getDriverToken,
  rejectOffer,
  removeDriverLocation,
  startRide,
  updateDriverLocation,
} from './api.js'

const errorText = e => {
  const data = e.response?.data
  return data?.message || data?.error || e.message || 'Driver dashboard request failed.'
}

const coords = p => p.map(n => Number(n).toFixed(5)).join(', ')

function Place({ lat, lng }) {
  const missing = lat == null || lng == null
  const { place, loading } = usePlace(missing ? null : [lat, lng])
  if (missing) return <>Not available</>
  if (loading) return <>Finding address…</>
  return <span title={place?.full || ''}>{place?.label || coords([lat, lng])}</span>
}

export default function DriverDashboard({ driver, onLogout }) {
  const [profile, setProfile] = useState(driver)
  const [position, setPosition] = useState(null)
  const [online, setOnline] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [pollError, setPollError] = useState('')
  const [status, setStatus] = useState('Offline. Choose your location, then go online.')
  const [dash, setDash] = useState({ activeRide: null, offers: [], receivedAt: Date.now() })
  const [history, setHistory] = useState(null)
  const [showHistory, setShowHistory] = useState(false)
  const [, setTick] = useState(0)

  const positionRef = useRef(null)
  const onlineRef = useRef(false)
  const driverId = driver.id

  useEffect(() => {
    let alive = true
    getDriverProfile()
      .then(data => { if (alive) setProfile(data) })
      .catch(e => { if (alive) setError(errorText(e)) })
    return () => { alive = false }
  }, [])

  useEffect(() => { positionRef.current = position }, [position])

  const refresh = useCallback(async () => {
    const data = await getDashboard()
    setDash({ activeRide: data.activeRide || null, offers: data.offers || [], receivedAt: Date.now() })
  }, [])

  const loadHistory = useCallback(async () => {
    try {
      setHistory(await getDriverHistory())
    } catch (e) {
      setError(errorText(e))
    }
  }, [])

  // Dashboard poll every 3 seconds.
  useEffect(() => {
    let alive = true
    let timer
    async function poll() {
      try {
        await refresh()
        if (alive) setPollError('')
      } catch (e) {
        if (alive) setPollError(errorText(e))
      } finally {
        if (alive) timer = setTimeout(poll, 3000)
      }
    }
    poll()
    return () => { alive = false; clearTimeout(timer) }
  }, [refresh])

  // Location heartbeat every 5 seconds while online.
  useEffect(() => {
    onlineRef.current = online
    if (!online) return undefined
    const send = async () => {
      const point = positionRef.current
      if (!point) return
      try {
        await updateDriverLocation(driverId, { latitude: point[0], longitude: point[1] })
        setStatus(`Online · location sent at ${new Date().toLocaleTimeString()}`)
        setError('')
      } catch (e) {
        setError(errorText(e))
      }
    }
    const timer = setInterval(send, 5000)
    return () => clearInterval(timer)
  }, [online, driverId])

  // Offer countdown redraw once a second while there are offers.
  useEffect(() => {
    if (dash.offers.length === 0) return undefined
    const timer = setInterval(() => setTick(n => n + 1), 1000)
    return () => clearInterval(timer)
  }, [dash.offers.length])

  useEffect(() => { if (showHistory) loadHistory() }, [showHistory, loadHistory])

  // Leaving the page while online: remove the position so no offers go to a closed tab.
  useEffect(() => () => {
    if (onlineRef.current && getDriverToken()) removeDriverLocation(driverId).catch(() => {})
  }, [driverId])

  function chooseLocation(point) {
    setPosition(point)
    setError('')
    setStatus(onlineRef.current
      ? 'Location updated. The next heartbeat will send it.'
      : 'Location selected. Go online to start receiving ride requests.')
  }

  function useMyLocation() {
    if (!navigator.geolocation) {
      setError('Geolocation is not available in this browser. Click the map instead.')
      return
    }
    setStatus('Finding your current location…')
    navigator.geolocation.getCurrentPosition(
      result => chooseLocation([result.coords.latitude, result.coords.longitude]),
      () => setError('Could not get your current location. Click the map instead.'),
      { enableHighAccuracy: true, timeout: 10000 }
    )
  }

  async function toggleOnline() {
    setBusy(true)
    setError('')
    try {
      if (online) {
        setOnline(false)
        await removeDriverLocation(driverId)
        setStatus('Offline. You will not receive new ride requests.')
      } else {
        if (!position) {
          setError('Choose your location before going online.')
          return
        }
        await updateDriverLocation(driverId, { latitude: position[0], longitude: position[1] })
        setOnline(true)
        setStatus(`Online · location sent at ${new Date().toLocaleTimeString()}`)
      }
    } catch (e) {
      setError(errorText(e))
    } finally {
      setBusy(false)
    }
  }

  async function act(action) {
    setBusy(true)
    setError('')
    try {
      await action()
      await refresh()
      if (showHistory) await loadHistory()
    } catch (e) {
      setError(errorText(e))
      refresh().catch(() => {})
    } finally {
      setBusy(false)
    }
  }

  async function logout() {
    if (onlineRef.current) {
      try { await removeDriverLocation(driverId) } catch { /* the position expires on its own */ }
      onlineRef.current = false
    }
    clearDriverAuth()
    onLogout()
  }

  const active = dash.activeRide
  const elapsed = Math.floor((Date.now() - dash.receivedAt) / 1000)
  const mapPickup = active ? [active.pickupLatitude, active.pickupLongitude] : position
  const mapDropoff = active && active.dropLatitude != null ? [active.dropLatitude, active.dropLongitude] : null

  return (
    <div className="booking-layout">
      <aside className="booking-panel">
        <p className="eyebrow">DRIVER DASHBOARD</p>
        <h1>{profile?.name || `Driver #${driverId}`}</h1>
        <p className="muted">{profile?.email || driver.email}</p>

        <div className="selection">
          <button className={`point ${online ? 'selected' : ''}`} disabled={busy} onClick={toggleOnline}>
            <span className="point-icon">{online ? '●' : '○'}</span>
            <span>
              <strong>{online ? 'Online' : 'Offline'}</strong>
              <small>{online ? 'Sending location every 5 seconds. Tap to go offline.' : 'Tap to go online and receive ride requests.'}</small>
            </span>
          </button>
          <button className="point" disabled={busy} onClick={useMyLocation}>
            <span className="point-icon">◎</span>
            <span>
              <strong>Use my location</strong>
              <small>Browser geolocation</small>
            </span>
          </button>
        </div>

        <p className="hint">Click the map to set your position manually.</p>
        {error && <p className="error" role="alert">{error}</p>}
        {pollError && <p className="error" role="alert">Dashboard refresh failed: {pollError}</p>}
        <p className="hint" role="status">{status}</p>

        {dash.offers.map(offer => {
          const left = Math.max(0, offer.secondsRemaining - elapsed)
          return (
            <div key={offer.id} className="card" style={{ padding: 16, marginTop: 12 }}>
              <strong>New ride request</strong>
              <p className="muted">Pickup: <Place lat={offer.pickupLatitude} lng={offer.pickupLongitude} /></p>
              <p className="muted">Destination: <Place lat={offer.dropLatitude} lng={offer.dropLongitude} /></p>
              <p role="timer"><strong>{left}s</strong> left to respond</p>
              <div className="actions">
                <button disabled={busy || left === 0} onClick={() => act(() => acceptOffer(offer.id))}>Accept</button>
                <button className="secondary" disabled={busy || left === 0} onClick={() => act(() => rejectOffer(offer.id))}>Reject</button>
              </div>
            </div>
          )
        })}

        {active && (
          <div className="card" style={{ padding: 16, marginTop: 12 }}>
            <strong>Current ride #{active.id}</strong>{' '}
            <span className={`chip ${active.status}`}>{active.status}</span>
            <p className="muted">Pickup: <Place lat={active.pickupLatitude} lng={active.pickupLongitude} /></p>
            <p className="muted">Destination: <Place lat={active.dropLatitude} lng={active.dropLongitude} /></p>
            <div className="actions">
              {active.status === 'CONFIRMED' && (
                <button disabled={busy} onClick={() => act(() => startRide(active.id))}>Start trip</button>
              )}
              {active.status === 'IN_PROGRESS' && (
                <button disabled={busy} onClick={() => act(() => completeRide(active.id))}>Complete trip</button>
              )}
            </div>
          </div>
        )}

        {online && !active && dash.offers.length === 0 && (
          <p className="hint">Waiting for ride requests…</p>
        )}

        <div style={{ marginTop: 16 }}>
          <button className="secondary" onClick={() => setShowHistory(v => !v)}>
            {showHistory ? 'Hide ride history' : 'Show ride history'}
          </button>
          {showHistory && (
            history === null ? <p role="status">Loading history…</p>
            : history.length === 0 ? <p className="hint">No rides yet.</p>
            : (
              <ul className="booking-list" style={{ listStyle: 'none', padding: 0 }}>
                {history.map(ride => (
                  <li key={ride.id} className="booking-row">
                    <span><strong>Ride #{ride.id}</strong></span>
                    <span className={`chip ${ride.status}`}>{ride.status}</span>
                  </li>
                ))}
              </ul>
            )
          )}
        </div>

        <button className="secondary" style={{ marginTop: 16 }} onClick={logout}>Log out</button>
      </aside>

      <MapView
        pickup={mapPickup}
        dropoff={mapDropoff}
        pickupLabel={active ? 'Pickup' : 'You'}
        hideAvailability
        onClick={chooseLocation}
      />
    </div>
  )
}

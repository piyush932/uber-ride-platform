import { useCallback, useEffect, useRef, useState } from 'react'
import MapView from '../components/MapView.jsx'
import {
  clearDriverAuth,
  getDriverProfile,
  updateDriverLocation,
} from './api.js'

const errorText = e => {
  const data = e.response?.data
  return (
    data?.message ||
    data?.error ||
    e.message ||
    'Driver dashboard request failed.'
  )
}

export default function DriverDashboard({ driver, onLogout }) {
  const [profile, setProfile] = useState(driver)
  const [position, setPosition] = useState(null)
  const [online, setOnline] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [status, setStatus] = useState('Set your location to go online.')

  const positionRef = useRef(null)
  const onlineRef = useRef(false)
  const heartbeatRef = useRef(null)

  useEffect(() => {
    let alive = true

    getDriverProfile()
      .then(data => {
        if (alive) setProfile(data)
      })
      .catch(e => {
        if (alive) setError(errorText(e))
      })

    return () => {
      alive = false
    }
  }, [])

  const sendHeartbeat = useCallback(async () => {
    const current = positionRef.current
    if (!current || !onlineRef.current || !driver?.id) return

    try {
      await updateDriverLocation(driver.id, {
        latitude: current[0],
        longitude: current[1],
        available: true,
      })
      setStatus(`Online · location sent at ${new Date().toLocaleTimeString()}`)
      setError('')
    } catch (e) {
      setError(errorText(e))
    }
  }, [driver?.id])

  useEffect(() => {
    positionRef.current = position
  }, [position])

  useEffect(() => {
    onlineRef.current = online

    if (!online) {
      clearInterval(heartbeatRef.current)
      heartbeatRef.current = null
      setStatus('Offline.')
      return
    }

    if (!positionRef.current) {
      setStatus('Choose your location or use my location before going online.')
      return
    }

    sendHeartbeat()
    heartbeatRef.current = setInterval(sendHeartbeat, 5000)

    return () => {
      clearInterval(heartbeatRef.current)
      heartbeatRef.current = null
    }
  }, [online, sendHeartbeat])

  function chooseLocation(point) {
    setPosition(point)
    setError('')

    if (onlineRef.current) {
      setStatus('Location updated. The next heartbeat will send it.')
    } else {
      setStatus('Location selected. Turn online to begin heartbeats.')
    }
  }

  function useMyLocation() {
    if (!navigator.geolocation) {
      setError('Geolocation is not available in this browser.')
      return
    }

    setStatus('Finding your current location…')

    navigator.geolocation.getCurrentPosition(
      result => {
        chooseLocation([
          result.coords.latitude,
          result.coords.longitude,
        ])
      },
      () => {
        setError('Could not get your current location. Click the map instead.')
      },
      { enableHighAccuracy: true, timeout: 10000 }
    )
  }

  async function toggleOnline() {
    setBusy(true)
    setError('')

    try {
      if (online) {
        setOnline(false)
      } else {
        if (!position) {
          setError('Select your location before going online.')
          return
        }

        await updateDriverLocation(driver.id, {
          latitude: position[0],
          longitude: position[1],
          available: true,
        })

        setOnline(true)
      }
    } catch (e) {
      setError(errorText(e))
    } finally {
      setBusy(false)
    }
  }

  function logout() {
    clearDriverAuth()
    onLogout()
  }

  return (
    <div className="booking-layout">
      <aside className="booking-panel">
        <p className="eyebrow">DRIVER DASHBOARD</p>
        <h1>{profile?.name || `Driver #${driver.id}`}</h1>
        <p className="muted">{profile?.email || driver.email}</p>

        <div className="selection">
          <button
            className={`point ${online ? 'selected' : ''}`}
            disabled={busy}
            onClick={toggleOnline}
          >
            <span className="point-icon">{online ? '●' : '○'}</span>
            <span>
              <strong>{online ? 'Online' : 'Offline'}</strong>
              <small>
                {online
                  ? 'Sending location every 5 seconds'
                  : 'Go online to receive ride offers later'}
              </small>
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

        <p className="hint">
          Click the map to set your current position manually. The selected
          location is sent only while you are online.
        </p>

        {error && (
          <p className="error" role="alert">
            {error}
          </p>
        )}

        {status && (
          <p className="hint" role="status">
            {status}
          </p>
        )}

        <div className="info-box">
          <strong>Stage 9B</strong>
          <p>
            Driver authentication, location selection, availability status, and
            location heartbeat are active.
          </p>
          <small>
            Ride offers, accept/reject actions, expiry, reassignment, and trip
            lifecycle arrive in Stages 9C–9E.
          </small>
        </div>

        <button className="secondary" onClick={logout}>
          Log out
        </button>
      </aside>

      <MapView
        pickup={position}
        onClick={chooseLocation}
      />
    </div>
  )
}

import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import MapView from '../components/MapView.jsx'
import { createBooking, getStoredUser } from '../api.js'

export default function BookRide() {
  const navigate = useNavigate()
  const user = getStoredUser()
  const [pickup, setPickup] = useState(null)
  const [dropoff, setDropoff] = useState(null)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  const handleMapClick = (latlng) => {
    setError('')
    if (!pickup || (pickup && dropoff)) {
      setPickup(latlng)
      setDropoff(null)
    } else {
      setDropoff(latlng)
    }
  }

  const book = async () => {
    if (!pickup || !dropoff) {
      setError('Tap the map to choose a pickup and a dropoff.')
      return
    }
    setBusy(true)
    setError('')
    try {
      const idempotencyKey = crypto.randomUUID()
      const { data } = await createBooking({
        passengerId: user?.id,
        pickupLatitude: pickup[0],
        pickupLongitude: pickup[1],
        dropLatitude: dropoff[0],
        dropLongitude: dropoff[1],
      }, idempotencyKey)
      navigate(`/rides/${data.id}`)
    } catch (err) {
      setError(err.response?.data?.message || err.response?.data?.error || 'Could not create the booking')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="booking-grid">
      <MapView pickup={pickup} dropoff={dropoff} onClick={handleMapClick} />
      <div className="card">
        <h1>Book a ride</h1>
        <p className="hint">Tap the map: first tap is pickup, second is dropoff.</p>
        <div className="point">
          <span className="dot pickup" />
          <span>{pickup ? `${pickup[0].toFixed(5)}, ${pickup[1].toFixed(5)}` : 'Pickup not set'}</span>
        </div>
        <div className="point">
          <span className="dot dropoff" />
          <span>{dropoff ? `${dropoff[0].toFixed(5)}, ${dropoff[1].toFixed(5)}` : 'Dropoff not set'}</span>
        </div>
        {error && <p className="error">{error}</p>}
        <button onClick={book} disabled={busy || !pickup || !dropoff}>
          {busy ? 'Booking…' : 'Book ride'}
        </button>
        <button className="ghost" type="button" onClick={() => { setPickup(null); setDropoff(null); setError('') }}>
          Reset points
        </button>
      </div>
    </div>
  )
}

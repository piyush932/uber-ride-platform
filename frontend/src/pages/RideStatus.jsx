import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { cancelBooking, completeBooking, getBooking } from '../api.js'

const STEPS = ['PENDING', 'CONFIRMED', 'COMPLETED']

export default function RideStatus() {
  const { id } = useParams()
  const [booking, setBooking] = useState(null)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    let alive = true
    const load = async () => {
      try {
        const { data } = await getBooking(id)
        if (alive) setBooking(data)
      } catch (err) {
        if (alive) setError(err.response?.data?.message || 'Could not load the booking')
      }
    }
    load()
    const timer = setInterval(load, 3000)
    return () => { alive = false; clearInterval(timer) }
  }, [id])

  const act = async (action) => {
    setBusy(true)
    setError('')
    try {
      await action(id)
      const { data } = await getBooking(id)
      setBooking(data)
    } catch (err) {
      setError(err.response?.data?.message || err.response?.data?.error || 'Action failed')
    } finally {
      setBusy(false)
    }
  }

  if (error && !booking) return <div className="card"><p className="error">{error}</p></div>
  if (!booking) return <div className="card"><p className="hint">Loading…</p></div>

  const activeStep = booking.status === 'CANCELLED' ? -1 : STEPS.indexOf(booking.status)

  return (
    <div className="card">
      <h1>Ride #{booking.id}</h1>
      <span className={`chip ${booking.status}`}>{booking.status}</span>
      {booking.driverId && <p className="hint">Driver #{booking.driverId} is on this ride.</p>}
      {booking.cancelReason && <p className="error">Cancelled: {booking.cancelReason}</p>}

      <div className="timeline">
        {STEPS.map((step, index) => (
          <div key={step} className={index <= activeStep ? 'active' : ''}>
            {index <= activeStep ? '●' : '○'} {step}
          </div>
        ))}
      </div>

      <p className="hint">
        Pickup: {booking.pickupLatitude?.toFixed(5)}, {booking.pickupLongitude?.toFixed(5)}<br />
        Dropoff: {booking.dropLatitude?.toFixed(5)}, {booking.dropLongitude?.toFixed(5)}
      </p>

      {error && <p className="error">{error}</p>}

      <div className="actions">
        {(booking.status === 'PENDING' || booking.status === 'CONFIRMED') && (
          <button className="danger" disabled={busy} onClick={() => act(cancelBooking)}>Cancel ride</button>
        )}
        {booking.status === 'CONFIRMED' && (
          <button className="success" disabled={busy} onClick={() => act(completeBooking)}>Complete ride</button>
        )}
      </div>
      <p className="hint">This page refreshes every 3 seconds.</p>
    </div>
  )
}

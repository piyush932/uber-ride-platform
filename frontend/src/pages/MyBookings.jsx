import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { getStoredUser, myBookings } from '../api.js'

export default function MyBookings() {
  const user = getStoredUser()
  const [bookings, setBookings] = useState([])
  const [error, setError] = useState('')

  useEffect(() => {
    if (!user?.id) {
      setError('Could not read your passenger id. Log in again.')
      return
    }
    myBookings(user.id)
      .then(({ data }) => setBookings(data))
      .catch((err) => setError(err.response?.data?.message || 'Could not load your rides'))
  }, [user?.id])

  return (
    <div>
      <h1>My rides</h1>
      {error && <p className="error">{error}</p>}
      {!error && bookings.length === 0 && <p className="hint">No rides yet. Book one from the map.</p>}
      <div className="booking-list">
        {bookings.map((b) => (
          <Link key={b.id} className="booking-row" to={`/rides/${b.id}`}>
            <span>Ride #{b.id}</span>
            <span className={`chip ${b.status}`}>{b.status}</span>
          </Link>
        ))}
      </div>
    </div>
  )
}

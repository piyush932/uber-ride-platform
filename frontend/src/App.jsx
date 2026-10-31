import { Navigate, Route, Routes } from 'react-router-dom'
import Login from './pages/Login.jsx'
import BookRide from './pages/BookRide.jsx'
import RideStatus from './pages/RideStatus.jsx'
import MyBookings from './pages/MyBookings.jsx'
import { getToken } from './api.js'

function RequireAuth({ children }) {
  return getToken() ? children : <Navigate to="/login" replace />
}

export default function App() {
  return (
    <div className="app">
      <header className="topbar">
        <span className="brand">Uber Ride</span>
        <nav>
          <a href="/book">Book</a>
          <a href="/bookings">My rides</a>
        </nav>
      </header>
      <main className="page">
        <Routes>
          <Route path="/" element={<Navigate to="/book" replace />} />
          <Route path="/login" element={<Login />} />
          <Route path="/book" element={<RequireAuth><BookRide /></RequireAuth>} />
          <Route path="/rides/:id" element={<RequireAuth><RideStatus /></RequireAuth>} />
          <Route path="/bookings" element={<RequireAuth><MyBookings /></RequireAuth>} />
          <Route path="*" element={<Navigate to="/book" replace />} />
        </Routes>
      </main>
    </div>
  )
}

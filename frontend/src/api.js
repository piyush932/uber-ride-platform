import axios from 'axios'

const api = axios.create({
  baseURL: import.meta.env.VITE_API_URL || 'http://localhost:8080',
})

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

export const getToken = () => localStorage.getItem('token')

export const setAuth = (token, user) => {
  localStorage.setItem('token', token)
  localStorage.setItem('user', JSON.stringify(user))
}

export const clearAuth = () => {
  localStorage.removeItem('token')
  localStorage.removeItem('user')
}

export const getStoredUser = () => {
  try {
    return JSON.parse(localStorage.getItem('user'))
  } catch {
    return null
  }
}

export const signup = (email, password) =>
  api.post('/api/v1/auth/signup', { email, password })

export const login = async (email, password) => {
  const { data } = await api.post('/api/v1/auth/login', { email, password })
  const token = data.token || data.accessToken
  const me = await api.get('/api/v1/auth/me')
  setAuth(token, me.data)
  return me.data
}

export const getBooking = (id) => api.get(`/api/v1/bookings/${id}`)

export const myBookings = (passengerId) =>
  api.get('/api/v1/bookings', { params: { passengerId } })

export const createBooking = (body, idempotencyKey) =>
  api.post('/api/v1/bookings', body, { headers: { 'Idempotency-Key': idempotencyKey } })

export const cancelBooking = (id) =>
  api.post(`/api/v1/bookings/${id}/cancel?reason=CANCELLED_BY_PASSENGER`)

export const completeBooking = (id) => api.post(`/api/v1/bookings/${id}/complete`)

export default api

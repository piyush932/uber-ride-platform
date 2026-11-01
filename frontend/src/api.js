import axios from 'axios'
const api = axios.create({ baseURL: import.meta.env.VITE_API_URL || '', timeout: 15000 })
export const getToken = () => localStorage.getItem('token')
export const getStoredUser = () => { try { return JSON.parse(localStorage.getItem('user')) } catch { return null } }
export const setAuth = (token, user) => { localStorage.setItem('token', token); localStorage.setItem('user', JSON.stringify(user)) }
export const clearAuth = () => { localStorage.removeItem('token'); localStorage.removeItem('user') }
api.interceptors.request.use(config => {
  if (getToken() && !config.headers.Authorization) config.headers.Authorization = `Bearer ${getToken()}`
  return config
})
api.interceptors.response.use(response => response, error => {
  const path = error.config?.url || ''
  if (error.response?.status === 401 && !/\/(login|signup)$/.test(path)) {
    clearAuth(); window.dispatchEvent(new Event('session-expired'))
  }
  return Promise.reject(error)
})
export const signup = (email, password, name) => api.post('/api/v1/auth/signup', { name, email, password })
export const login = async (email, password) => {
  const { data } = await api.post('/api/v1/auth/login', { email, password })
  if (!data.token) throw new Error('Login response did not contain a token')
  const me = await api.get('/api/v1/auth/me', { headers: { Authorization: `Bearer ${data.token}` } })
  setAuth(data.token, me.data); return me.data
}
export const getBooking = id => api.get(`/api/v1/bookings/${id}`)
export const myBookings = passengerId => api.get('/api/v1/bookings', { params: { passengerId } })
export const createBooking = (body, key) => api.post('/api/v1/bookings', body, { headers: { 'Idempotency-Key': key } })
export const cancelBooking = id =>
  api.post(
    `/api/v1/bookings/${id}/cancel`,
    { reason: 'CANCELLED_BY_PASSENGER' },
    { headers: { 'Content-Type': 'application/json' } },
  )
export const completeBooking = id => api.post(`/api/v1/bookings/${id}/complete`)
export default api

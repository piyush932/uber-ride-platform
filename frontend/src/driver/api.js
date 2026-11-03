import axios from 'axios'

const DRIVER_TOKEN_KEY = 'driver_token'
const DRIVER_USER_KEY = 'driver_user'
const isAuthRequest = url => /\/auth\/(login|signup)\/driver$/.test(url || '')

const driverApi = axios.create({
  baseURL: import.meta.env.VITE_API_URL || '',
  timeout: 15000,
})

driverApi.interceptors.request.use(config => {
  const token = getDriverToken()
  if (token && !isAuthRequest(config.url) && !config.headers.Authorization) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

driverApi.interceptors.response.use(
  response => response,
  error => {
    if (error.response?.status === 401 && !isAuthRequest(error.config?.url)) {
      clearDriverAuth()
      window.dispatchEvent(new Event('driver-session-expired'))
    }
    return Promise.reject(error)
  }
)

export function getDriverToken() {
  return localStorage.getItem(DRIVER_TOKEN_KEY)
}

export function getDriverUser() {
  try {
    const raw = localStorage.getItem(DRIVER_USER_KEY)
    return raw ? JSON.parse(raw) : null
  } catch {
    return null
  }
}

export function saveDriverAuth({ token, user }) {
  localStorage.setItem(DRIVER_TOKEN_KEY, token)
  localStorage.setItem(DRIVER_USER_KEY, JSON.stringify(user))
}

export function clearDriverAuth() {
  localStorage.removeItem(DRIVER_TOKEN_KEY)
  localStorage.removeItem(DRIVER_USER_KEY)
}

export async function driverLogin(email, password) {
  const { data } = await driverApi.post('/api/v1/auth/login/driver', {
    email,
    password,
  })
  if (!data.token) throw new Error('Login response did not contain a token')

  const { data: user } = await driverApi.get('/api/v1/auth/driver/me', {
    headers: { Authorization: `Bearer ${data.token}` },
  })
  saveDriverAuth({ token: data.token, user })
  return user
}

export async function driverSignup(email, password, name) {
  await driverApi.post('/api/v1/auth/signup/driver', {
    email,
    password,
    name,
  })
  return driverLogin(email, password)
}

export async function getDriverProfile() {
  const { data } = await driverApi.get('/api/v1/auth/driver/me')
  return data
}

export async function updateDriverLocation(driverId, payload) {
  const { data } = await driverApi.put(
    `/api/v1/locations/drivers/${driverId}`,
    payload
  )
  return data
}

import axios from 'axios'

const DRIVER_TOKEN_KEY = 'driver_token'
const DRIVER_USER_KEY = 'driver_user'

const driverApi = axios.create({
  baseURL: '/api',
})

driverApi.interceptors.request.use(config => {
  const token = localStorage.getItem(DRIVER_TOKEN_KEY)
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

driverApi.interceptors.response.use(
  response => response,
  error => {
    if (error.response?.status === 401) {
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
  const { data } = await driverApi.post('/v1/auth/driver/login', {
    email,
    password,
  })

  const token = data.token || data.accessToken || data.jwt
  if (!token) throw new Error('Login response did not contain a token')

  const user = data.driver || data.user || {
    id: data.userId || data.id,
    name: data.name,
    email: data.email,
  }

  saveDriverAuth({ token, user })
  return user
}

export async function driverSignup(email, password, name) {
  await driverApi.post('/v1/auth/driver/signup', {
    email,
    password,
    name,
  })
  return driverLogin(email, password)
}

export async function getDriverProfile() {
  const { data } = await driverApi.get('/v1/auth/driver/me')
  return data
}

export async function updateDriverLocation(driverId, payload) {
  const { data } = await driverApi.put(
    `/v1/locations/drivers/${driverId}`,
    payload
  )
  return data
}

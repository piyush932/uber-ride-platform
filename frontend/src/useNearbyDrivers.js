import { useEffect, useState } from 'react'
import api, { getToken } from './api.js'

export default function useNearbyDrivers(pickup) {
  const lat = pickup?.[0], lng = pickup?.[1]
  const [state, setState] = useState({ key: null, drivers: [], status: 'idle' })
  const [expired, setExpired] = useState(false)
  const token = getToken()
  const key = lat == null || lng == null ? null : `${lat},${lng}`
  useEffect(() => {
    const stop = () => setExpired(true)
    window.addEventListener('session-expired', stop)
    return () => window.removeEventListener('session-expired', stop)
  }, [])
  useEffect(() => {
    if (!key || !token || expired) return undefined
    let active = true, timer
    const controller = new AbortController()
    setState({ key, drivers: [], status: 'loading' })
    async function load() {
      let retry = true
      try {
        const { data } = await api.get('/api/v1/locations/nearby', {
          params: { lat, lng, radiusKm: 5, limit: 50 }, signal: controller.signal,
        })
        if (!Array.isArray(data)) throw new Error('Invalid nearby response')
        const valid = data.every(d => d.driverId != null && Number.isFinite(d.latitude) && Number.isFinite(d.longitude) && Math.abs(d.latitude) <= 90 && Math.abs(d.longitude) <= 180)
        if (!valid) throw new Error('Invalid driver coordinates')
        if (active) setState({ key, drivers: data, status: 'ready' })
      } catch (error) {
        if (!active || controller.signal.aborted) return
        retry = error.response?.status !== 401
        setState({ key, drivers: [], status: retry ? 'error' : 'signed-out' })
      } finally {
        if (active && retry && !controller.signal.aborted) timer = setTimeout(load, 10000)
      }
    }
    timer = setTimeout(load, 400)
    return () => { active = false; clearTimeout(timer); controller.abort() }
  }, [key, lat, lng, token, expired])
  if (!key) return { drivers: [], status: 'idle' }
  if (!token || expired) return { drivers: [], status: 'signed-out' }
  if (state.key !== key) return { drivers: [], status: 'loading' }
  return state
}

const STORAGE_KEY = 'place-cache-v1'
const MAX_ENTRIES = 200
const MIN_GAP_MS = 1100
const pending = new Map()
let store = null
let chain = Promise.resolve()
let lastCall = 0

function load() {
  if (store) return store
  try { store = JSON.parse(localStorage.getItem(STORAGE_KEY)) || {} } catch { store = {} }
  return store
}

function save() {
  try {
    const keys = Object.keys(store)
    if (keys.length > MAX_ENTRIES) keys.slice(0, keys.length - MAX_ENTRIES).forEach(k => delete store[k])
    localStorage.setItem(STORAGE_KEY, JSON.stringify(store))
  } catch { /* storage full or blocked: the in-memory copy still works */ }
}

const keyOf = ([lat, lng]) => `${Number(lat).toFixed(4)},${Number(lng).toFixed(4)}`

export const cachedPlace = point => load()[keyOf(point)] || null

// The public Nominatim server allows at most one request per second, so every lookup waits its turn.
function schedule(task) {
  const run = chain.then(async () => {
    const wait = Math.max(0, lastCall + MIN_GAP_MS - Date.now())
    if (wait) await new Promise(resolve => setTimeout(resolve, wait))
    lastCall = Date.now()
    return task()
  })
  chain = run.catch(() => {})
  return run
}

function shorten(data) {
  if (!data || data.error) return null
  const a = data.address || {}
  const primary = data.name || a.road || a.neighbourhood || a.suburb
  const area = a.suburb || a.neighbourhood || a.city_district
  const city = a.city || a.town || a.village || a.state_district
  const parts = [...new Set([primary, area, city].filter(Boolean))].slice(0, 3)
  const full = data.display_name || ''
  const label = parts.join(', ') || full.split(',').slice(0, 3).join(',').trim()
  return label ? { label, full } : null
}

// Resolves to { label, full }, or null when the name cannot be found. Failures are not cached.
export function reverseGeocode(point) {
  const key = keyOf(point)
  const hit = load()[key]
  if (hit) return Promise.resolve(hit)
  if (pending.has(key)) return pending.get(key)
  const request = schedule(async () => {
    const [lat, lng] = point
    const url = `https://nominatim.openstreetmap.org/reverse?format=jsonv2&addressdetails=1&zoom=18&accept-language=en&lat=${lat}&lon=${lng}`
    const response = await fetch(url, { signal: AbortSignal.timeout(8000) })
    if (!response.ok) throw new Error(`Lookup failed (${response.status})`)
    const place = shorten(await response.json())
    if (place) { load()[key] = place; save() }
    return place
  }).catch(() => null).finally(() => pending.delete(key))
  pending.set(key, request)
  return request
}

import { useEffect, useState } from 'react'
import { cachedPlace, reverseGeocode } from './geocode.js'

// Looks up a readable name for [lat, lng]. Waits 600 ms after the point stops changing,
// so clicking around the map does not queue a request for every click.
export default function usePlace(point) {
  const lat = point?.[0]
  const lng = point?.[1]
  const [state, setState] = useState({ place: null, loading: false })

  useEffect(() => {
    if (lat == null || lng == null) { setState({ place: null, loading: false }); return undefined }
    const hit = cachedPlace([lat, lng])
    if (hit) { setState({ place: hit, loading: false }); return undefined }
    let alive = true
    setState({ place: null, loading: true })
    const timer = setTimeout(() => {
      reverseGeocode([lat, lng]).then(place => { if (alive) setState({ place, loading: false }) })
    }, 600)
    return () => { alive = false; clearTimeout(timer) }
  }, [lat, lng])

  return state
}

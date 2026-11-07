import { useEffect, useState } from 'react'
import { divIcon } from 'leaflet'
import { MapContainer, TileLayer, CircleMarker, Polyline, Tooltip, Marker, Popup, useMap, useMapEvents } from 'react-leaflet'
import useNearbyDrivers from '../useNearbyDrivers.js'

const carIcon = divIcon({
  className: 'nearby-driver-icon',
  html: '<span style="display:grid;place-items:center;width:36px;height:36px;background:white;border:2px solid #17211e;border-radius:50%;box-shadow:0 2px 8px #0003;font-size:21px" aria-hidden="true">🚗</span>',
  iconSize: [36, 36], iconAnchor: [18, 18], popupAnchor: [0, -20],
})
const badgeStyle = { position: 'absolute', top: 16, right: 16, zIndex: 500, maxWidth: 'calc(100% - 80px)', padding: '12px 16px', background: 'white', color: '#17211e', borderRadius: 12, boxShadow: '0 3px 14px #0002', fontSize: 13 }
function Controls({ pickup, dropoff, center, onClick }) {
  const map = useMap()
  const pLat = pickup?.[0], pLng = pickup?.[1], dLat = dropoff?.[0], dLng = dropoff?.[1]
  useMapEvents({ click: e => onClick?.([e.latlng.lat, e.latlng.lng]) })
  useEffect(() => {
    if (pLat != null && pLng != null && dLat != null && dLng != null) map.fitBounds([[pLat, pLng], [dLat, dLng]], { padding: [50, 50], maxZoom: 15 })
    else if (pLat != null && pLng != null) map.setView([pLat, pLng], 14)
  }, [map, pLat, pLng, dLat, dLng])
  useEffect(() => { if (center) map.setView(center, 15) }, [map, center])
  return null
}
// pickupLabel and hideAvailability are optional. The passenger app uses the defaults; the driver portal
// labels its own position and hides the passenger-only nearby-driver badge.
export default function MapView({ pickup, dropoff, onClick, pickupLabel = 'Pickup', hideAvailability = false }) {
  const [center, setCenter] = useState(null)
  const [message, setMessage] = useState('')
  const [locating, setLocating] = useState(false)
  const { drivers, status } = useNearbyDrivers(hideAvailability ? null : pickup)
  const count = drivers.length
  const availability = status === 'idle' ? 'Select a pickup to find drivers'
    : status === 'signed-out' ? 'Log in to see nearby drivers'
    : status === 'loading' ? 'Checking nearby drivers…'
    : status === 'error' ? 'Driver availability unavailable'
    : count === 0 ? 'No drivers nearby'
    : `${count === 50 ? '50+' : count} driver${count === 1 ? '' : 's'} nearby`
  function locate() {
    if (!navigator.geolocation) { setMessage('Location is not supported. Choose a point on the map.'); return }
    setLocating(true); setMessage('')
    navigator.geolocation.getCurrentPosition(p => {
      setCenter([p.coords.latitude, p.coords.longitude]); setLocating(false)
    }, () => { setLocating(false); setMessage('Location unavailable. You can still select points on the map.') }, { timeout: 10000 })
  }
  return <section className="map-wrap" aria-label="Pickup, destination and nearby drivers map">
    <MapContainer center={[12.9716, 77.5946]} zoom={13} scrollWheelZoom>
      <TileLayer attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors' url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png" />
      <Controls pickup={pickup} dropoff={dropoff} center={center} onClick={onClick} />
      {drivers.map(driver => <Marker key={driver.driverId} position={[driver.latitude, driver.longitude]} icon={carIcon} title={`Driver ${driver.driverId}`} bubblingMouseEvents={false}>
        <Popup><strong>Driver #{driver.driverId}</strong><br />{Number.isFinite(driver.distanceKm) ? `${driver.distanceKm.toFixed(2)} km from pickup` : 'Nearby reported position'}<br /><small>Reported location, not a guaranteed available ride.</small></Popup>
      </Marker>)}
      {pickup && <CircleMarker center={pickup} radius={10} pathOptions={{ color: '#fff', fillColor: '#087f5b', fillOpacity: 1 }}><Tooltip permanent>{pickupLabel}</Tooltip></CircleMarker>}
      {dropoff && <CircleMarker center={dropoff} radius={10} pathOptions={{ color: '#fff', fillColor: '#222', fillOpacity: 1 }}><Tooltip permanent>Destination</Tooltip></CircleMarker>}
      {pickup && dropoff && <Polyline positions={[pickup, dropoff]} pathOptions={{ color: '#087f5b', dashArray: '6 8' }} />}
    </MapContainer>
    {!hideAvailability && <div style={badgeStyle} role="status" aria-live="polite"><strong>{availability}</strong>{status === 'ready' && <small style={{ display: 'block', marginTop: 4 }}>Within 5 km · refreshes about every 10 s</small>}</div>}
    <div className="map-tools"><button className="secondary" onClick={locate} disabled={locating}>{locating ? 'Locating…' : '◎ Use my location'}</button>{message && <p role="status">{message}</p>}</div>
  </section>
}

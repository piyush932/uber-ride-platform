import { useEffect, useState } from 'react'
import { MapContainer, TileLayer, CircleMarker, Polyline, Tooltip, useMap, useMapEvents } from 'react-leaflet'
function Controls({ pickup, dropoff, center, onClick }) {
  const map = useMap()
  useMapEvents({ click: e => onClick?.([e.latlng.lat, e.latlng.lng]) })
  useEffect(() => {
    if (pickup && dropoff) map.fitBounds([pickup, dropoff], { padding: [50, 50], maxZoom: 15 })
    else if (pickup) map.setView(pickup, 14)
  }, [map, pickup, dropoff])
  useEffect(() => { if (center) map.setView(center, 15) }, [map, center])
  return null
}
export default function MapView({ pickup, dropoff, onClick }) {
  const [center, setCenter] = useState(null)
  const [message, setMessage] = useState('')
  const [locating, setLocating] = useState(false)
  function locate() {
    if (!navigator.geolocation) { setMessage('Location is not supported. Choose a point on the map.'); return }
    setLocating(true); setMessage('')
    navigator.geolocation.getCurrentPosition(p => {
      setCenter([p.coords.latitude, p.coords.longitude]); setLocating(false)
    }, () => { setLocating(false); setMessage('Location unavailable. You can still select points on the map.') }, { timeout: 10000 })
  }
  return <section className="map-wrap" aria-label="Pickup and destination map">
    <MapContainer center={[12.9716, 77.5946]} zoom={13} scrollWheelZoom>
      <TileLayer attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors' url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png" />
      <Controls pickup={pickup} dropoff={dropoff} center={center} onClick={onClick} />
      {pickup && <CircleMarker center={pickup} radius={10} pathOptions={{ color: '#fff', fillColor: '#087f5b', fillOpacity: 1 }}><Tooltip permanent>Pickup</Tooltip></CircleMarker>}
      {dropoff && <CircleMarker center={dropoff} radius={10} pathOptions={{ color: '#fff', fillColor: '#222', fillOpacity: 1 }}><Tooltip permanent>Destination</Tooltip></CircleMarker>}
      {pickup && dropoff && <Polyline positions={[pickup, dropoff]} pathOptions={{ color: '#087f5b', dashArray: '6 8' }} />}
    </MapContainer>
    <div className="map-tools"><button className="secondary" onClick={locate} disabled={locating}>{locating ? 'Locating…' : '◎ Use my location'}</button>{message && <p role="status">{message}</p>}</div>
  </section>
}

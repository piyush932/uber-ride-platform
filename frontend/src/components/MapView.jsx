import { MapContainer, TileLayer, CircleMarker, Polyline, useMapEvents } from 'react-leaflet'

function ClickHandler({ onClick }) {
  useMapEvents({
    click: (event) => onClick([event.latlng.lat, event.latlng.lng]),
  })
  return null
}

export default function MapView({ pickup, dropoff, onClick }) {
  const center = pickup || dropoff || [12.9716, 77.5946]

  return (
    <div className="map-wrap">
      <MapContainer center={center} zoom={13} scrollWheelZoom>
        <TileLayer
          attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
          url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
        />
        <ClickHandler onClick={onClick} />
        {pickup && (
          <CircleMarker center={pickup} radius={9} pathOptions={{ color: '#2ecc71', fillColor: '#2ecc71', fillOpacity: 1 }} />
        )}
        {dropoff && (
          <CircleMarker center={dropoff} radius={9} pathOptions={{ color: '#ff5c5c', fillColor: '#ff5c5c', fillOpacity: 1 }} />
        )}
        {pickup && dropoff && (
          <Polyline positions={[pickup, dropoff]} pathOptions={{ color: '#4f8cff', dashArray: '6 8' }} />
        )}
      </MapContainer>
    </div>
  )
}

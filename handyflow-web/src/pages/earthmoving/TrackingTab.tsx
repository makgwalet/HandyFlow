// src/pages/earthmoving/TrackingTab.tsx
//
// Where each machine was last seen. No tracking provider is chosen yet, so this works from device mappings (ready for an adapter) and from readings
// typed in by hand. It says "last seen", never "live", and keeps provider and manual readings apart: neither changes the machine's own meter.
import { useState } from "react"
import { MapPin } from "lucide-react"
import { apiMessage, fmtDateTime } from "./shared/format"
import { Overlay, ModalHead, ModalFoot, ErrBanner, lbl, inputStyle } from "./shared/Modal"
import { useEarthPerms } from "./shared/useEarthPerms"
import { useAssets, useTrackingStatus, useTelematicsDevices, useTelematicsProviders, useMapTelematicsDevice, useUnmapTelematicsDevice, useRecordManualReading } from "./shared/hooks"
import type { TrackingSource, TrackingStatus } from "./shared/types"

const btn = (primary = false): React.CSSProperties => ({
  padding: "6px 12px", borderRadius: 8, fontSize: 13, fontWeight: 600, cursor: "pointer",
  border: primary ? "none" : "1px solid var(--hf-border)",
  background: primary ? "var(--hf-primary)" : "var(--hf-surface)",
  color: primary ? "var(--hf-text-on-solid)" : "var(--hf-text-secondary)",
})
const card: React.CSSProperties = { border: "1px solid var(--hf-border)", borderRadius: 10, padding: "12px 16px", background: "var(--hf-surface)" }
const FRESH_LABEL: Record<string, string> = { NEVER: "Never seen", FRESH: "Seen in the last 30 minutes", STALE: "Seen in the last day", OFFLINE: "Not seen for over a day" }
const FRESH_COLOR: Record<string, string> = { NEVER: "var(--hf-text-muted)", FRESH: "var(--hf-success)", STALE: "var(--hf-warning)", OFFLINE: "var(--hf-danger)" }
const unitWord = (u: string | null) => (u === "KILOMETRES" ? "km" : "hrs")

function SourceLine({ title, s }: { title: string; s: TrackingSource }) {
  return (
    <div style={{ fontSize: 13 }}>
      <strong>{title}: </strong>
      <span style={{ color: FRESH_COLOR[s.freshness], fontWeight: 600 }}>{FRESH_LABEL[s.freshness]}</span>
      {s.latitude != null && s.longitude != null && <div>Position {s.latitude.toFixed(5)}, {s.longitude.toFixed(5)} · {s.positionAt ? fmtDateTime(s.positionAt) : ""}</div>}
      {s.meterValue != null && <div>Meter {s.meterValue} {unitWord(s.meterUnit)} · {s.meterAt ? fmtDateTime(s.meterAt) : ""}</div>}
    </div>
  )
}

function MapDialog({ onClose }: { onClose: () => void }) {
  const assets = useAssets()
  const providers = useTelematicsProviders()
  const map = useMapTelematicsDevice()
  const [assetId, setAssetId] = useState("")
  const [providerCode, setProviderCode] = useState("")
  const [externalId, setExternalId] = useState("")
  const [label, setLabel] = useState("")
  const [err, setErr] = useState("")
  const submit = () => {
    if (!assetId) { setErr("Choose the machine."); return }
    if (!/^[A-Za-z0-9_]{2,40}$/.test(providerCode.trim())) { setErr("The provider code is 2 to 40 letters, digits or underscores."); return }
    if (!externalId.trim()) { setErr("Give the device's id."); return }
    setErr("")
    map.mutate({ assetId, providerCode: providerCode.trim(), externalId: externalId.trim(), label: label.trim() || null },
      { onSuccess: onClose, onError: (e: unknown) => setErr(apiMessage(e, "Could not map the device")) })
  }
  const installed = providers.data ?? []
  return (
    <Overlay onClose={onClose} width={480}>
      <ModalHead title="Map a tracking device" subtitle="A device belongs to one machine at a time." onClose={onClose} />
      {err && <ErrBanner msg={err} />}
      {installed.length === 0 && <p style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>No tracking provider is connected yet, so a mapped device will not send readings until one is.</p>}
      <label style={lbl} htmlFor="td-asset">Machine</label>
      <select id="td-asset" style={inputStyle()} value={assetId} onChange={e => setAssetId(e.target.value)}>
        <option value="">Choose…</option>
        {(assets.data ?? []).filter(a => a.status !== "RETIRED").map(a => <option key={a.id} value={a.id}>{a.fleetNumber ? `${a.fleetNumber} · ` : ""}{a.name}</option>)}
      </select>
      <label style={{ ...lbl, marginTop: 12 }} htmlFor="td-provider">Provider code</label>
      <input id="td-provider" style={inputStyle()} value={providerCode} list="td-providers" onChange={e => setProviderCode(e.target.value)} />
      <datalist id="td-providers">{installed.map(p => <option key={p} value={p} />)}</datalist>
      <label style={{ ...lbl, marginTop: 12 }} htmlFor="td-ext">Device id</label>
      <input id="td-ext" style={inputStyle()} value={externalId} onChange={e => setExternalId(e.target.value)} />
      <label style={{ ...lbl, marginTop: 12 }} htmlFor="td-label">Label (optional)</label>
      <input id="td-label" style={inputStyle()} value={label} onChange={e => setLabel(e.target.value)} />
      <ModalFoot onCancel={onClose} onSubmit={submit} loading={map.isPending} label="Map device" />
    </Overlay>
  )
}

function ReadingDialog({ row, onClose }: { row: TrackingStatus; onClose: () => void }) {
  const record = useRecordManualReading()
  const [lat, setLat] = useState("")
  const [lon, setLon] = useState("")
  const [meter, setMeter] = useState("")
  const [note, setNote] = useState("")
  const [err, setErr] = useState("")
  const submit = () => {
    const hasPos = lat.trim() !== "" || lon.trim() !== ""
    if (!hasPos && meter.trim() === "") { setErr("Give a position, a meter value, or both."); return }
    if (hasPos && (lat.trim() === "" || lon.trim() === "")) { setErr("A position needs both latitude and longitude."); return }
    if (hasPos && (Math.abs(Number(lat)) > 90 || Math.abs(Number(lon)) > 180 || Number.isNaN(Number(lat)) || Number.isNaN(Number(lon)))) { setErr("Latitude is -90 to 90 and longitude -180 to 180."); return }
    if (meter.trim() !== "" && (Number.isNaN(Number(meter)) || Number(meter) < 0)) { setErr("The meter value cannot be negative."); return }
    setErr("")
    record.mutate({ assetId: row.assetId, latitude: hasPos ? Number(lat) : null, longitude: hasPos ? Number(lon) : null,
      meterValue: meter.trim() === "" ? null : Number(meter), meterUnit: meter.trim() === "" ? null : row.meterUnit, note: note.trim() || null },
      { onSuccess: onClose, onError: (e: unknown) => setErr(apiMessage(e, "Could not save the reading")) })
  }
  return (
    <Overlay onClose={onClose} width={460}>
      <ModalHead title={`Record a reading: ${row.assetName}`} subtitle="Kept apart from tracker readings. It does not change the machine's own meter." onClose={onClose} />
      {err && <ErrBanner msg={err} />}
      <label style={lbl} htmlFor="mr-lat">Latitude</label>
      <input id="mr-lat" style={inputStyle()} value={lat} onChange={e => setLat(e.target.value)} />
      <label style={{ ...lbl, marginTop: 12 }} htmlFor="mr-lon">Longitude</label>
      <input id="mr-lon" style={inputStyle()} value={lon} onChange={e => setLon(e.target.value)} />
      <label style={{ ...lbl, marginTop: 12 }} htmlFor="mr-meter">Meter ({row.meterUnit === "KILOMETRES" ? "kilometres" : "hours"})</label>
      <input id="mr-meter" type="number" min="0" style={inputStyle()} value={meter} onChange={e => setMeter(e.target.value)} />
      <label style={{ ...lbl, marginTop: 12 }} htmlFor="mr-note">Note (optional)</label>
      <input id="mr-note" style={inputStyle()} value={note} onChange={e => setNote(e.target.value)} />
      <ModalFoot onCancel={onClose} onSubmit={submit} loading={record.isPending} label="Save reading" />
    </Overlay>
  )
}

function UnmapDialog({ id, name, onClose }: { id: string; name: string; onClose: () => void }) {
  const unmap = useUnmapTelematicsDevice()
  const [reason, setReason] = useState("")
  const [err, setErr] = useState("")
  const submit = () => {
    if (reason.trim().length < 5) { setErr("Give a reason of at least 5 characters."); return }
    setErr("")
    unmap.mutate({ id, reason: reason.trim() }, { onSuccess: onClose, onError: (e: unknown) => setErr(apiMessage(e, "Could not unmap the device")) })
  }
  return (
    <Overlay onClose={onClose} width={440}>
      <ModalHead title={`Unmap ${name}`} subtitle="Its past readings are kept." onClose={onClose} />
      {err && <ErrBanner msg={err} />}
      <label style={lbl} htmlFor="um-reason">Reason</label>
      <input id="um-reason" style={inputStyle()} value={reason} onChange={e => setReason(e.target.value)} />
      <ModalFoot onCancel={onClose} onSubmit={submit} loading={unmap.isPending} label="Unmap" />
    </Overlay>
  )
}

export default function TrackingTab() {
  const { canWrite, canAdmin } = useEarthPerms()
  const status = useTrackingStatus()
  const devices = useTelematicsDevices(false)
  const [dialog, setDialog] = useState<{ type: "map" | "reading" | "unmap"; row?: TrackingStatus; device?: { id: string; name: string } } | null>(null)

  const rows = status.data ?? []
  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 16 }}>
      <div style={{ display: "flex", alignItems: "center", gap: 8, justifyContent: "space-between", flexWrap: "wrap" }}>
        <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
          <MapPin size={18} aria-hidden />
          <h2 style={{ margin: 0, fontSize: 18 }}>Tracking</h2>
        </div>
        {canAdmin && <button style={btn(true)} onClick={() => setDialog({ type: "map" })}>Map a device</button>}
      </div>
      <p style={{ margin: 0, fontSize: 13, color: "var(--hf-text-muted)" }}>
        Shows where each machine was last seen, not where it is now. Tracker readings and readings typed in by hand are kept apart, and neither changes the machine's own meter.
      </p>

      {status.isLoading ? <p>Loading…</p> : rows.length === 0 ? (
        <p style={{ color: "var(--hf-text-muted)" }}>No machines yet.</p>
      ) : (
        <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
          {rows.map(r => (
            <div key={r.assetId} style={card}>
              <div style={{ display: "flex", justifyContent: "space-between", gap: 12, flexWrap: "wrap" }}>
                <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
                  <div>
                    <strong>{r.fleetNumber ? `${r.fleetNumber} · ` : ""}{r.assetName}</strong>{" "}
                    <span style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>
                      Machine meter {r.machineMeter ?? 0} {unitWord(r.meterUnit)} · {r.activeDevices} device{r.activeDevices === 1 ? "" : "s"}
                    </span>
                  </div>
                  <SourceLine title="Tracker" s={r.provider} />
                  {r.providerMeterDifference != null && (
                    <div style={{ fontSize: 12, color: Math.abs(r.providerMeterDifference) > 0 ? "var(--hf-warning)" : "var(--hf-text-muted)" }}>
                      Tracker meter reads {Math.abs(r.providerMeterDifference)} {unitWord(r.meterUnit)} {r.providerMeterDifference >= 0 ? "above" : "below"} the machine's meter.
                    </div>
                  )}
                  <SourceLine title="Typed in" s={r.manual} />
                </div>
                {canWrite && <div><button style={btn()} onClick={() => setDialog({ type: "reading", row: r })}>Record a reading</button></div>}
              </div>
            </div>
          ))}
        </div>
      )}

      <section style={card} aria-label="Mapped devices">
        <strong style={{ fontSize: 14 }}>Mapped devices</strong>
        {(devices.data ?? []).length === 0 ? (
          <p style={{ margin: "6px 0 0", fontSize: 13, color: "var(--hf-text-muted)" }}>No devices are mapped yet.</p>
        ) : (
          <ul style={{ margin: "8px 0 0", paddingLeft: 18, fontSize: 13 }}>
            {(devices.data ?? []).map(d => (
              <li key={d.id}>
                {d.providerCode} · {d.externalId}{d.label ? ` (${d.label})` : ""} → {d.fleetNumber ? `${d.fleetNumber} · ` : ""}{d.assetName}{" "}
                {canAdmin && <button style={{ ...btn(), padding: "1px 8px", fontSize: 12 }} onClick={() => setDialog({ type: "unmap", device: { id: d.id, name: d.externalId } })}>Unmap</button>}
              </li>
            ))}
          </ul>
        )}
      </section>

      {dialog?.type === "map" && <MapDialog onClose={() => setDialog(null)} />}
      {dialog?.type === "reading" && dialog.row && <ReadingDialog row={dialog.row} onClose={() => setDialog(null)} />}
      {dialog?.type === "unmap" && dialog.device && <UnmapDialog id={dialog.device.id} name={dialog.device.name} onClose={() => setDialog(null)} />}
    </div>
  )
}

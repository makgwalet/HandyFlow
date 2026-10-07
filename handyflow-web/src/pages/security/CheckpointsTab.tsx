// src/pages/security/CheckpointsTab.tsx
//
// Checkpoints: every checkpoint across sites with its QR sticker, scan methods, scans in the last 30 days, last scan and the
// patrol routes that use it. An administrator can print a QR, rotate a compromised code, edit the details or switch a
// checkpoint off. Real records only; counts come from recorded scans.
import { useState } from "react"
import { Link } from "react-router-dom"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { AlertTriangle, Pencil, Power, Printer, QrCode, RefreshCw, Route as RouteIcon } from "lucide-react"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import Chip from "../../components/ui/Chip"
import StatTile from "../../components/ui/StatTile"
import { checkpointState } from "./site.logic"
import { deactivateWarning, filterCheckpoints, groupBySite, methods, summarise, type CheckpointRow } from "./checkpoint.logic"

const card: React.CSSProperties = { background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 12, marginBottom: 14, overflow: "hidden" }
const input: React.CSSProperties = { padding: "8px 10px", border: "1px solid var(--hf-border)", borderRadius: 8, fontSize: 13, background: "var(--hf-surface)", color: "var(--hf-text-primary)", width: "100%", boxSizing: "border-box" }
const btn: React.CSSProperties = { display: "inline-flex", alignItems: "center", gap: 5, padding: "5px 9px", border: "1px solid var(--hf-border)", borderRadius: 8, background: "var(--hf-surface)", color: "var(--hf-text-secondary)", fontSize: 12, fontWeight: 600, cursor: "pointer" }
const fmtT = (d: string) => new Date(d).toLocaleString("en-ZA", { day: "numeric", month: "short", hour: "2-digit", minute: "2-digit", timeZone: "Africa/Johannesburg" })
const unwrap = (r: any) => { const p = r.data?.data ?? r.data; return Array.isArray(p) ? p : p?.content ?? [] }
const errText = (e: any) => e?.response?.data?.message ?? "That did not work. Please try again."
const base = (c: CheckpointRow) => `/api/v1/security/sites/${c.siteId}/checkpoints/${c.id}`

async function openPdf(url: string) {
  const res = await apiClient.get(url, { responseType: "blob" })
  window.open(URL.createObjectURL(new Blob([res.data], { type: "application/pdf" })), "_blank")
}

// The QR image comes from our own backend as an authenticated blob; the signed payload never goes to a third party.
function QrPreview({ c }: { c: CheckpointRow }) {
  const { data: url, isError } = useQuery({
    queryKey: ["checkpoint-qr-image", c.id],
    queryFn: async () => URL.createObjectURL(new Blob([(await apiClient.get(`${base(c)}/qr-image`, { responseType: "blob" })).data], { type: "image/png" })),
    staleTime: Infinity, enabled: c.active,
  })
  const [broken, setBroken] = useState(false)
  const box: React.CSSProperties = { width: 56, height: 56, borderRadius: 8, border: "1px solid var(--hf-border)", display: "flex", alignItems: "center", justifyContent: "center", color: "var(--hf-text-faint)", background: "var(--hf-surface-sunken)" }
  if (!c.active || isError || !url || broken) return <div style={box} title={isError || broken ? "The QR could not be loaded" : undefined}><QrCode size={20} /></div>
  return <img src={url} alt={`QR code for ${c.name}`} width={56} height={56} onError={() => setBroken(true)} style={{ borderRadius: 8, border: "1px solid var(--hf-border)" }} />
}

function EditDialog({ c, onClose, onSaved }: { c: CheckpointRow; onClose: () => void; onSaved: () => void }) {
  const [name, setName] = useState(c.name)
  const [description, setDescription] = useState(c.description ?? "")
  const [nfc, setNfc] = useState("")
  const [ble, setBle] = useState("")
  const [clearNfc, setClearNfc] = useState(false)
  const [clearBle, setClearBle] = useState(false)
  const [err, setErr] = useState("")
  const save = useMutation({
    // The identifiers are never sent to this screen. Leaving a field empty keeps what is set (the field is left out of the
    // request); a ticked "remove" box sends a blank, which clears it; a typed value replaces it.
    mutationFn: () => apiClient.patch(`/api/v1/security/checkpoints/${c.id}`, {
      name: name.trim(), description: description.trim() || null, active: c.active,
      ...(clearNfc ? { nfcTagUid: "" } : nfc.trim() ? { nfcTagUid: nfc.trim() } : {}),
      ...(clearBle ? { bleBeaconId: "" } : ble.trim() ? { bleBeaconId: ble.trim() } : {}),
    }),
    onSuccess: onSaved, onError: e => setErr(errText(e)),
  })
  const submit = () => { if (!name.trim()) setErr("A checkpoint needs a name."); else { setErr(""); save.mutate() } }
  return (
    <div role="dialog" aria-label={`Edit ${c.name}`} style={{ position: "fixed", inset: 0, background: "rgba(0,0,0,0.35)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 50 }}>
      <div style={{ ...card, padding: 18, width: 400, display: "grid", gap: 10, marginBottom: 0 }}>
        <div style={{ fontWeight: 700 }}>Edit {c.name}</div>
        <label style={{ fontSize: 12 }}>Name<input value={name} onChange={e => setName(e.target.value)} style={input} /></label>
        <label style={{ fontSize: 12 }}>Description<input value={description} onChange={e => setDescription(e.target.value)} style={input} /></label>
        <label style={{ fontSize: 12 }}>NFC tag ID {c.hasNfc ? "(one is set; type a new one to replace it)" : "(optional)"}<input value={nfc} disabled={clearNfc} onChange={e => setNfc(e.target.value)} style={input} /></label>
        {c.hasNfc && <label style={{ fontSize: 12, display: "flex", gap: 6, alignItems: "center" }}><input type="checkbox" checked={clearNfc} onChange={e => setClearNfc(e.target.checked)} />Remove the NFC tag</label>}
        <label style={{ fontSize: 12 }}>Bluetooth beacon ID {c.hasBle ? "(one is set; type a new one to replace it)" : "(optional)"}<input value={ble} disabled={clearBle} onChange={e => setBle(e.target.value)} style={input} /></label>
        {c.hasBle && <label style={{ fontSize: 12, display: "flex", gap: 6, alignItems: "center" }}><input type="checkbox" checked={clearBle} onChange={e => setClearBle(e.target.checked)} />Remove the Bluetooth beacon</label>}
        {err && <div role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 13 }}>{err}</div>}
        <div style={{ display: "flex", gap: 8, justifyContent: "flex-end" }}>
          <button style={btn} onClick={onClose}>Cancel</button>
          <button style={{ ...btn, background: "var(--hf-accent)", color: "var(--hf-text-on-solid)", border: "none" }} disabled={save.isPending} onClick={submit}>{save.isPending ? "Saving…" : "Save"}</button>
        </div>
      </div>
    </div>
  )
}

export default function CheckpointsTab() {
  const qc = useQueryClient()
  const canManage = usePermission("SECURITY_MANAGE")
  const [siteId, setSiteId] = useState("")
  const [search, setSearch] = useState("")
  const [quietOnly, setQuietOnly] = useState(false)
  const [showInactive, setShowInactive] = useState(false)
  const [editing, setEditing] = useState<CheckpointRow | null>(null)
  const [msg, setMsg] = useState("")

  const { data: sites = [] } = useQuery<{ id: string; name: string }[]>({ queryKey: ["checkpoint-sites"], queryFn: async () => unwrap(await apiClient.get("/api/v1/security/sites?size=200")) })
  const { data, isLoading, error } = useQuery<CheckpointRow[]>({
    queryKey: ["checkpoints", siteId, showInactive],
    queryFn: async () => unwrap(await apiClient.get(`/api/v1/security/checkpoints?includeInactive=${showInactive}${siteId ? `&siteId=${siteId}` : ""}`)),
  })
  const refresh = () => qc.invalidateQueries({ queryKey: ["checkpoints"] })
  const toggle = useMutation({
    mutationFn: (c: CheckpointRow) => apiClient.patch(`/api/v1/security/checkpoints/${c.id}`, { name: c.name, description: c.description, active: !c.active }),
    onSuccess: refresh, onError: e => setMsg(errText(e)),
  })
  const rotate = useMutation({
    mutationFn: (c: CheckpointRow) => apiClient.post(`${base(c)}/qr-secret/regenerate`),
    onSuccess: (_r, c) => { qc.invalidateQueries({ queryKey: ["checkpoint-qr-image", c.id] }); setMsg(`New QR code created for ${c.name}. Print it and replace the old sticker; the old one no longer works.`) },
    onError: e => setMsg(errText(e)),
  })

  const all = data ?? []
  const sum = summarise(all)
  const groups = groupBySite(filterCheckpoints(all, { search, quietOnly }))

  return (
    <div>
      <div style={{ display: "flex", gap: 10, flexWrap: "wrap", alignItems: "center", marginBottom: 14 }}>
        <select aria-label="Site" value={siteId} onChange={e => setSiteId(e.target.value)} style={{ ...input, width: "auto" }}><option value="">All sites</option>{sites.map(s => <option key={s.id} value={s.id}>{s.name}</option>)}</select>
        <input aria-label="Search" placeholder="Search checkpoint or site" value={search} onChange={e => setSearch(e.target.value)} style={{ ...input, width: 220 }} />
        <label style={{ fontSize: 13, display: "flex", gap: 6, alignItems: "center" }}><input type="checkbox" checked={quietOnly} onChange={e => setQuietOnly(e.target.checked)} />No scans in 30 days</label>
        <label style={{ fontSize: 13, display: "flex", gap: 6, alignItems: "center" }}><input type="checkbox" checked={showInactive} onChange={e => setShowInactive(e.target.checked)} />Show switched-off</label>
      </div>

      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(170px, 1fr))", gap: 12, marginBottom: 14 }}>
        <StatTile label="Active checkpoints" value={sum.active} icon={<QrCode size={18} />} />
        <StatTile label="No scans in 30 days" value={sum.quiet} icon={<AlertTriangle size={18} />} tone={sum.quiet ? "warn" : "neutral"} />
        <StatTile label="On patrol routes" value={sum.onRoutes} icon={<RouteIcon size={18} />} />
        <StatTile label="Switched off" value={sum.inactive} hint={showInactive ? undefined : "tick Show switched-off to list"} icon={<Power size={18} />} />
      </div>

      {msg && <div role="status" style={{ ...card, padding: 12, fontSize: 13 }}>{msg}</div>}
      {error && <div role="alert" style={{ color: "var(--hf-danger-text)", marginBottom: 10 }}>The checkpoints could not be loaded.</div>}
      {isLoading && <div style={{ color: "var(--hf-text-muted)" }}>Loading…</div>}
      {!isLoading && groups.length === 0 && <div style={{ color: "var(--hf-text-muted)", fontSize: 13 }}>{all.length === 0 ? "No checkpoints yet. Add them to a site on the Sites screen." : "No checkpoints match these filters."}</div>}

      {groups.map(g => (
        <div key={g.siteId} style={card}>
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", padding: "10px 14px", borderBottom: "1px solid var(--hf-border)" }}>
            <Link to={`/security/sites/${g.siteId}`} style={{ fontWeight: 700, color: "var(--hf-text)", textDecoration: "none" }}>{g.siteName}</Link>
            <button style={btn} onClick={() => openPdf(`/api/v1/security/sites/${g.siteId}/checkpoints/qr-sheet`).catch(e => setMsg(errText(e)))}><Printer size={13} />Print all QR codes</button>
          </div>
          {g.items.map(c => {
            const st = checkpointState(c)
            return (
              <div key={c.id} style={{ display: "flex", gap: 14, alignItems: "center", padding: "10px 14px", borderTop: "1px solid var(--hf-border)", opacity: c.active ? 1 : 0.6, flexWrap: "wrap" }}>
                <QrPreview c={c} />
                <div style={{ flex: "1 1 220px" }}>
                  <div style={{ fontWeight: 600 }}>{c.name}</div>
                  {c.description && <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>{c.description}</div>}
                  <div style={{ display: "flex", gap: 6, marginTop: 4, flexWrap: "wrap" }}>
                    {methods(c).map(m => <Chip key={m} tone="neutral">{m}</Chip>)}
                    {c.activeRoutes > 0 && <Chip tone="info">{c.activeRoutes} route{c.activeRoutes === 1 ? "" : "s"}</Chip>}
                    {!c.active && <Chip tone="neutral">Switched off</Chip>}
                  </div>
                </div>
                <div style={{ flex: "1 1 170px", fontSize: 13 }}>
                  {c.active ? <Chip tone={st.tone}>{st.label}</Chip> : <span style={{ color: "var(--hf-text-muted)" }}>Not scannable</span>}
                  {c.lastScanAt && <div style={{ fontSize: 12, color: "var(--hf-text-muted)", marginTop: 3 }}>last scan {fmtT(c.lastScanAt)}</div>}
                </div>
                {canManage && (
                  <div style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>
                    {c.active && <button style={btn} aria-label={`Print QR for ${c.name}`} onClick={() => openPdf(`${base(c)}/qr-pdf`).catch(e => setMsg(errText(e)))}><Printer size={12} />Print</button>}
                    {c.active && <button style={btn} aria-label={`New QR for ${c.name}`} onClick={() => { if (window.confirm(`Create a new QR code for ${c.name}? The sticker on site stops working immediately and must be replaced.`)) rotate.mutate(c) }}><RefreshCw size={12} />New QR</button>}
                    <button style={btn} aria-label={`Edit ${c.name}`} onClick={() => setEditing(c)}><Pencil size={12} />Edit</button>
                    <button style={btn} aria-label={`${c.active ? "Switch off" : "Switch on"} ${c.name}`} onClick={() => { const w = deactivateWarning(c); if (!w || window.confirm(w)) toggle.mutate(c) }}><Power size={12} />{c.active ? "Switch off" : "Switch on"}</button>
                  </div>
                )}
              </div>
            )
          })}
        </div>
      ))}
      <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>
        A switched-off checkpoint cannot be scanned but keeps its history. Scan counts are recorded scans in the last 30 days. Times are South African time.
      </div>

      {editing && <EditDialog c={editing} onClose={() => setEditing(null)} onSaved={() => { setEditing(null); refresh() }} />}
    </div>
  )
}

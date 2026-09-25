// src/pages/security/CctvTab.tsx
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { Camera, Plus, Key, Wifi, WifiOff, Clock } from "lucide-react"

interface CameraRecord {
  id: string
  siteId: string
  siteName: string
  name: string
  provider: string
  status: "ACTIVE" | "OFFLINE" | "DECOMMISSIONED"
  lastEventAt: string | null
  notes: string | null
  createdAt: string
}

interface Site { id: string; name: string }

const STATUS_CONFIG = {
  ACTIVE:          { label: "Active",          color: "var(--hf-success-text-strong)", bg: "var(--hf-success-soft-strong)", Icon: Wifi },
  OFFLINE:         { label: "Offline",         color: "var(--hf-danger-text-strong)", bg: "var(--hf-danger-soft)", Icon: WifiOff },
  DECOMMISSIONED:  { label: "Decommissioned",  color: "var(--hf-text-faint)", bg: "var(--hf-surface-sunken)", Icon: WifiOff },
}

const fmtDate = (d: string | null) => d ? new Date(d).toLocaleTimeString("en-ZA", { hour: "2-digit", minute: "2-digit", day: "numeric", month: "short" }) : "Never"

export default function CctvTab() {
  const qc = useQueryClient()
  const [showAdd,    setShowAdd]    = useState(false)
  const [secret,     setSecret]     = useState<{ cameraId: string; webhookSecret: string } | null>(null)
  const [form,       setForm]       = useState({ siteId: "", name: "", provider: "NONE", notes: "" })
  const [apiError,   setApiError]   = useState("")

  const { data: cameras = [], isLoading } = useQuery<CameraRecord[]>({
    queryKey: ["cameras"],
    queryFn: async () => {
      // Fetch all sites then cameras per site — or use a tenant-wide list if available
      const r = await apiClient.get("/api/v1/security/cameras/site/all?size=100").catch(() =>
        apiClient.get("/api/v1/security/cameras?size=100"))
      const p = r.data?.data ?? r.data
      return (p?.content ?? p ?? []) as CameraRecord[]
    },
  })

  const { data: sites = [] } = useQuery<Site[]>({
    queryKey: ["sites-list"],
    queryFn: async () => {
      const r = await apiClient.get("/api/v1/security/sites?size=100")
      const p = r.data?.data ?? r.data
      return (p?.content ?? p) as Site[]
    },
  })

  const register = useMutation({
    mutationFn: (body: any) => apiClient.post("/api/v1/security/cameras", body),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["cameras"] }); setShowAdd(false); setForm({ siteId: "", name: "", provider: "NONE", notes: "" }) },
    onError: (e: any) => setApiError(e.response?.data?.message ?? "Registration failed"),
  })

  const genSecret = useMutation({
    mutationFn: (id: string) => apiClient.post(`/api/v1/security/cameras/${id}/webhook-secret`),
    onSuccess: (r, id) => {
      const d = r.data?.data ?? r.data
      setSecret({ cameraId: id, webhookSecret: d.webhookSecret })
    },
  })

  const markOffline = useMutation({
    mutationFn: (id: string) => apiClient.post(`/api/v1/security/cameras/${id}/offline`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["cameras"] }),
  })

  const markActive = useMutation({
    mutationFn: (id: string) => apiClient.post(`/api/v1/security/cameras/${id}/activate`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["cameras"] }),
  })

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 20 }}>
        <div>
          <h2 style={{ margin: 0, fontSize: 16, fontWeight: 700, color: "var(--hf-text)" }}>CCTV Registry</h2>
          <p style={{ margin: "2px 0 0", fontSize: 12, color: "var(--hf-text-muted)" }}>
            {cameras.filter(c => c.status === "ACTIVE").length} active · {cameras.filter(c => c.status === "OFFLINE").length} offline
          </p>
        </div>
        <button onClick={() => setShowAdd(true)}
          style={{ display: "flex", alignItems: "center", gap: 6, padding: "9px 16px", borderRadius: 8, border: "none", background: "var(--hf-accent)", color: "var(--hf-text-on-solid)", fontSize: 12, fontWeight: 600, cursor: "pointer" }}>
          <Plus size={14} /> Add Camera
        </button>
      </div>

      {/* Secret reveal */}
      {secret && (
        <div style={{ background: "var(--hf-success-soft)", border: "1px solid var(--hf-success-border)", borderRadius: 10, padding: "14px 16px", marginBottom: 20 }}>
          <p style={{ margin: "0 0 6px", fontWeight: 700, fontSize: 12, color: "var(--hf-success-text-strong)" }}>Webhook Secret — copy now, it won't be shown again</p>
          <code style={{ fontSize: 11, background: "var(--hf-success-soft-strong)", padding: "6px 10px", borderRadius: 6, display: "block", wordBreak: "break-all" as const }}>
            {secret.webhookSecret}
          </code>
          <button onClick={() => setSecret(null)} style={{ marginTop: 10, fontSize: 11, padding: "4px 10px", border: "1px solid var(--hf-success)", borderRadius: 6, background: "var(--hf-surface)", color: "var(--hf-success-text-strong)", cursor: "pointer" }}>
            Done, I've copied it
          </button>
        </div>
      )}

      {isLoading ? (
        <p style={{ color: "var(--hf-text-faint)", fontSize: 13 }}>Loading cameras…</p>
      ) : cameras.length === 0 ? (
        <div style={{ textAlign: "center", padding: "48px 0", color: "var(--hf-text-disabled)" }}>
          <Camera size={32} strokeWidth={1.5} style={{ display: "block", margin: "0 auto 8px" }} />
          <p style={{ margin: 0, fontWeight: 500 }}>No cameras registered</p>
          <p style={{ margin: "4px 0 0", fontSize: 12 }}>Add a camera and generate a webhook secret to start receiving motion events</p>
        </div>
      ) : (
        <div style={{ display: "grid", gap: 8 }}>
          {cameras.map(c => {
            const sc = STATUS_CONFIG[c.status]
            const Icon = sc.Icon
            return (
              <div key={c.id} style={{ display: "flex", alignItems: "center", gap: 14, padding: "14px 16px", border: "1px solid var(--hf-border)", borderRadius: 10, background: "var(--hf-surface)" }}>
                <Icon size={18} style={{ color: sc.color }} />
                <div style={{ flex: 1 }}>
                  <div style={{ display: "flex", gap: 8, alignItems: "center", marginBottom: 2 }}>
                    <span style={{ fontWeight: 700, fontSize: 13, color: "var(--hf-text)" }}>{c.name}</span>
                    <span style={{ fontSize: 10, fontWeight: 700, padding: "2px 8px", borderRadius: 4, color: sc.color, background: sc.bg }}>{sc.label}</span>
                    <span style={{ fontSize: 10, color: "var(--hf-text-faint)", padding: "2px 6px", border: "1px solid var(--hf-border)", borderRadius: 4 }}>{c.provider}</span>
                  </div>
                  <p style={{ margin: 0, fontSize: 11, color: "var(--hf-text-muted)" }}>
                    {c.siteName} · <Clock size={10} style={{ verticalAlign: "middle" }} /> Last event: {fmtDate(c.lastEventAt)}
                  </p>
                </div>
                <div style={{ display: "flex", gap: 6 }}>
                  <button onClick={() => genSecret.mutate(c.id)}
                    style={{ display: "flex", alignItems: "center", gap: 5, padding: "6px 10px", borderRadius: 7, border: "1px solid var(--hf-border)", background: "var(--hf-surface-muted)", color: "var(--hf-text-secondary)", fontSize: 11, cursor: "pointer" }}>
                    <Key size={11} /> Secret
                  </button>
                  {c.status === "ACTIVE" && (
                    <button onClick={() => markOffline.mutate(c.id)}
                      style={{ padding: "6px 10px", borderRadius: 7, border: "1px solid var(--hf-danger-border)", background: "var(--hf-danger-soft)", color: "var(--hf-danger-text-strong)", fontSize: 11, cursor: "pointer" }}>
                      Offline
                    </button>
                  )}
                  {c.status === "OFFLINE" && (
                    <button onClick={() => markActive.mutate(c.id)}
                      style={{ padding: "6px 10px", borderRadius: 7, border: "1px solid var(--hf-success-border)", background: "var(--hf-success-soft)", color: "var(--hf-success-text-strong)", fontSize: 11, cursor: "pointer" }}>
                      Activate
                    </button>
                  )}
                </div>
              </div>
            )
          })}
        </div>
      )}

      {/* Add camera modal */}
      {showAdd && (
        <div style={modalOverlay}>
          <div style={modalBox}>
            <h3 style={{ margin: "0 0 16px", fontSize: 15, fontWeight: 700 }}>Register Camera</h3>
            {apiError && <p style={{ color: "var(--hf-danger-text)", fontSize: 12, marginBottom: 12 }}>{apiError}</p>}
            <div style={{ display: "flex", flexDirection: "column", gap: 12 }}>
              <div>
                <label style={labelStyle}>Site *</label>
                <select value={form.siteId} onChange={e => setForm(p => ({ ...p, siteId: e.target.value }))} style={inputStyle}>
                  <option value="">Select site…</option>
                  {sites.map((s: any) => <option key={s.id} value={s.id}>{s.name}</option>)}
                </select>
              </div>
              <div>
                <label style={labelStyle}>Camera Name *</label>
                <input value={form.name} onChange={e => setForm(p => ({ ...p, name: e.target.value }))} placeholder="e.g. Main Entrance" style={inputStyle} />
              </div>
              <div>
                <label style={labelStyle}>Provider</label>
                <select value={form.provider} onChange={e => setForm(p => ({ ...p, provider: e.target.value }))} style={inputStyle}>
                  <option value="NONE">Not configured</option>
                  <option value="HIKVISION_CLOUD">Hikvision Cloud</option>
                  <option value="DAHUA_CLOUD">Dahua Cloud</option>
                  <option value="ONVIF">ONVIF</option>
                  <option value="RTSP_GENERIC">RTSP Generic</option>
                  <option value="OTHER">Other</option>
                </select>
              </div>
            </div>
            <div style={{ display: "flex", gap: 10, justifyContent: "flex-end", marginTop: 20 }}>
              <button onClick={() => setShowAdd(false)} style={secondaryBtn}>Cancel</button>
              <button onClick={() => register.mutate(form)} style={primaryBtn}>Register</button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

const labelStyle = { display: "block", fontSize: 11, fontWeight: 600, color: "var(--hf-text-secondary)", marginBottom: 4 } as const
const inputStyle = { width: "100%", padding: "9px 12px", border: "1px solid var(--hf-border)", borderRadius: 8, fontSize: 13, background: "var(--hf-surface)", boxSizing: "border-box" as const } as const
const primaryBtn = { padding: "9px 18px", borderRadius: 8, border: "none", background: "var(--hf-accent)", color: "var(--hf-text-on-solid)", fontSize: 13, fontWeight: 600, cursor: "pointer" } as const
const secondaryBtn = { padding: "9px 18px", borderRadius: 8, border: "1px solid var(--hf-border)", background: "var(--hf-surface)", color: "var(--hf-text-secondary)", fontSize: 13, cursor: "pointer" } as const
const modalOverlay = { position: "fixed" as const, inset: 0, background: "rgba(0,0,0,0.4)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000 } as const
const modalBox = { background: "var(--hf-surface)", borderRadius: 14, padding: 24, width: 440, boxShadow: "0 20px 60px rgba(0,0,0,0.2)" } as const

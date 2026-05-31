
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import {
  Plus, Truck, AlertTriangle, ChevronDown, ChevronUp, X,
  Edit2, Eye, Clock, MapPin, Wrench, CheckCircle, AlertCircle,
} from "lucide-react"

// ── Types ──────────────────────────────────────────────────────────────────────

interface Asset {
  id: string
  name: string
  fleetNumber: string | null
  assetType: string
  make: string | null
  model: string | null
  year: number | null
  serialNumber: string | null
  registration: string | null
  ownershipType: string     // OWN | HIRED_IN | HIRED_OUT
  hireSupplier: string | null
  hireStartDate: string | null
  hireEndDate: string | null
  status: string
  currentSite: string | null
  currentClient: string | null
  dailyRate: number | null
  hourlyRate: number | null
  currentHours: number
  lastServiceHours: number
  serviceIntervalHours: number
  dueForService: boolean
  notes: string | null
  createdAt: string
}

// ── Constants ──────────────────────────────────────────────────────────────────

const STATUS_CFG: Record<string, { color: string; bg: string; border: string; label: string; icon: React.ElementType }> = {
  AVAILABLE:   { color: "#166534", bg: "#DCFCE7", border: "#86EFAC",  label: "Available",    icon: CheckCircle  },
  DEPLOYED:    { color: "#1D4ED8", bg: "#EFF6FF", border: "#BFDBFE",  label: "Deployed",     icon: MapPin       },
  MAINTENANCE: { color: "#D97706", bg: "#FFFBEB", border: "#FDE68A",  label: "Maintenance",  icon: Wrench       },
  BREAKDOWN:   { color: "#DC2626", bg: "#FEF2F2", border: "#FECACA",  label: "Breakdown",    icon: AlertTriangle },
  HIRED_OUT:   { color: "#7C3AED", bg: "#F5F3FF", border: "#DDD6FE",  label: "Hired Out",    icon: Truck        },
  RETIRED:     { color: "#94A3B8", bg: "#F8FAFC", border: "#E2E8F0",  label: "Retired",      icon: Clock        },
}

const OWN_TYPE_CFG: Record<string, { color: string; bg: string; label: string }> = {
  OWN:       { color: "#1B3A6B", bg: "#EFF6FF", label: "Owned"     },
  HIRED_IN:  { color: "#7C3AED", bg: "#F5F3FF", label: "Hired In"  },
  HIRED_OUT: { color: "#D97706", bg: "#FFFBEB", label: "Hired Out" },
}

const ASSET_TYPES = [
  "DOZER","EXCAVATOR","GRADER","LOADER","DUMPER","CRANE",
  "ROLLER","SCRAPER","COMPACTOR","DRILL","OTHER",
]

const STATUSES = ["AVAILABLE","DEPLOYED","MAINTENANCE","BREAKDOWN","HIRED_OUT","RETIRED"]

const EMOJI: Record<string, string> = {
  DOZER:"🚜", EXCAVATOR:"⛏️", GRADER:"🛣️", LOADER:"🏗️",
  DUMPER:"🚛", CRANE:"🏗️", ROLLER:"🛞", SCRAPER:"🚜",
  COMPACTOR:"🛞", DRILL:"⛏️", OTHER:"🚧",
}

const STATUS_DESCRIPTIONS: Record<string, string> = {
  AVAILABLE:   "Machine is in the yard, ready to deploy",
  DEPLOYED:    "Machine is active on a site",
  MAINTENANCE: "Machine is undergoing scheduled maintenance",
  BREAKDOWN:   "Machine is unserviceable due to breakdown or accident",
  HIRED_OUT:   "Machine is hired out to a third party",
  RETIRED:     "Machine has been permanently decommissioned",
}

const unwrap = (r: any) => { const p = r.data?.data ?? r.data; return p?.content ?? p ?? [] }

const EMPTY_FORM = {
  name: "", fleetNumber: "", assetType: "DOZER", make: "", model: "",
  year: "", serialNumber: "", registration: "",
  ownershipType: "OWN", hireSupplier: "", hireStartDate: "", hireEndDate: "",
  dailyRate: "", hourlyRate: "", notes: "",
}

// ── Main component ─────────────────────────────────────────────────────────────

export default function AssetsTab() {
  const qc = useQueryClient()

  const [showAdd, setShowAdd]         = useState(false)
  const [showStatus, setShowStatus]   = useState<Asset | null>(null)
  const [showHours, setShowHours]     = useState<Asset | null>(null)
  const [viewing, setViewing]         = useState<Asset | null>(null)
  const [expanded, setExpanded]       = useState<string | null>(null)
  const [filterStatus, setFilterStatus] = useState("ALL")
  const [filterType, setFilterType]   = useState("ALL")
  const [form, setForm]               = useState(EMPTY_FORM)
  const [newStatus, setNewStatus]     = useState("")
  const [statusNote, setStatusNote]   = useState("")
  const [newHours, setNewHours]       = useState("")
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [apiError, setApiError]       = useState("")

  // ── Queries & mutations ────────────────────────────────────────────────────

  const { data: assets = [], isLoading } = useQuery<Asset[]>({
    queryKey: ["em-assets"],
    queryFn: async () => unwrap(await apiClient.get("/api/v1/earthmoving/assets?size=200")),
  })

  const createAsset = useMutation({
    mutationFn: (body: any) => apiClient.post("/api/v1/earthmoving/assets", body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["em-assets"] })
      setShowAdd(false); setForm(EMPTY_FORM); setFieldErrors({}); setApiError("")
    },
    onError: (e: any) => {
      const d = e.response?.data
      if (d?.errors) setFieldErrors(d.errors)
      else setApiError(d?.message ?? "Failed to register asset")
    },
  })

  const updateStatus = useMutation({
    // PUT not PATCH — avoids CORS preflight failures
    mutationFn: ({ id, status, note }: { id: string; status: string; note: string }) =>
      apiClient.put(`/api/v1/earthmoving/assets/${id}/status`, { status, note }),
    onSuccess: (res) => {
      qc.invalidateQueries({ queryKey: ["em-assets"] })
      setShowStatus(null); setNewStatus(""); setStatusNote(""); setApiError("")
      const updated = res.data?.data ?? res.data
      if (updated?.id) setViewing(updated)
    },
    onError: (e: any) => setApiError(e.response?.data?.message ?? "Failed to update status"),
  })

  const updateHours = useMutation({
    mutationFn: ({ id, hours }: { id: string; hours: number }) =>
      apiClient.put(`/api/v1/earthmoving/assets/${id}/hours`, { currentHours: hours }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["em-assets"] })
      setShowHours(null); setNewHours(""); setApiError("")
    },
    onError: (e: any) => setApiError(e.response?.data?.message ?? "Failed to update hours"),
  })

  // ── Helpers ────────────────────────────────────────────────────────────────

  const validate = () => {
    const errs: Record<string, string> = {}
    if (!form.name.trim()) errs.name = "Asset name is required"
    if (form.year && (isNaN(Number(form.year)) || Number(form.year) < 1950 || Number(form.year) > new Date().getFullYear() + 1))
      errs.year = "Enter a valid year"
    if (form.dailyRate && isNaN(Number(form.dailyRate))) errs.dailyRate = "Must be a number"
    setFieldErrors(errs)
    return Object.keys(errs).length === 0
  }

  const filtered = (assets as Asset[]).filter(a => {
    if (filterStatus !== "ALL" && a.status !== filterStatus) return false
    if (filterType   !== "ALL" && a.assetType !== filterType) return false
    return true
  })

  const fmtR = (n: number | null | undefined) => n != null ? `R ${Number(n).toLocaleString("en-ZA")}` : "—"

  const stats = [
    { label: "Total fleet",   value: assets.length,                                                 color: "#1B3A6B" },
    { label: "Available",     value: assets.filter(a => a.status === "AVAILABLE").length,            color: "#166534" },
    { label: "Deployed",      value: assets.filter(a => a.status === "DEPLOYED").length,             color: "#1D4ED8" },
    { label: "Service due",   value: assets.filter(a => a.dueForService).length,                     color: "#D97706" },
    { label: "Breakdowns",    value: assets.filter(a => a.status === "BREAKDOWN").length,            color: "#DC2626" },
  ]

  const inp = (k: string): React.CSSProperties => ({
    width: "100%", padding: "9px 12px", boxSizing: "border-box" as const,
    border: `1.5px solid ${fieldErrors[k] ? "#DC2626" : "#E2E8F0"}`,
    borderRadius: 8, fontSize: 14,
    background: fieldErrors[k] ? "#FFF5F5" : "#fff", outline: "none",
  })

  const FErr = ({ k }: { k: string }) => fieldErrors[k] ? (
    <div style={{ display: "flex", alignItems: "center", gap: 4, fontSize: 12, color: "#DC2626", marginTop: 4 }}>
      <AlertCircle size={12} />{fieldErrors[k]}
    </div>
  ) : null

  const StatusBadge = ({ status }: { status: string }) => {
    const cfg = STATUS_CFG[status] ?? STATUS_CFG.AVAILABLE
    const Icon = cfg.icon
    return (
      <span style={{ display: "inline-flex", alignItems: "center", gap: 4, background: cfg.bg, color: cfg.color, padding: "3px 10px", borderRadius: 20, fontSize: 11, fontWeight: 700, border: `1px solid ${cfg.border}` }}>
        <Icon size={10} />{cfg.label}
      </span>
    )
  }

  // ── Render ────────────────────────────────────────────────────────────────

  return (
    <div>

      {/* Stats row */}
      <div style={{ display: "flex", gap: 12, marginBottom: 22 }}>
        {stats.map(s => (
          <div key={s.label} style={{ flex: 1, background: "#F8FAFC", border: "1px solid #E2E8F0", borderRadius: 10, padding: "12px 16px" }}>
            <div style={{ fontSize: 22, fontWeight: 700, color: s.color }}>{s.value}</div>
            <div style={{ fontSize: 11, color: "#64748B", marginTop: 2 }}>{s.label}</div>
          </div>
        ))}
      </div>

      {/* Breakdown alert banner */}
      {assets.filter(a => a.status === "BREAKDOWN").length > 0 && (
        <div style={{ marginBottom: 16, padding: "12px 16px", background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 10, display: "flex", alignItems: "center", gap: 10 }}>
          <AlertTriangle size={17} color="#DC2626" style={{ flexShrink: 0 }} />
          <div>
            <div style={{ fontWeight: 700, fontSize: 13, color: "#DC2626" }}>Active Breakdowns</div>
            <div style={{ fontSize: 12, color: "#B91C1C" }}>
              {assets.filter(a => a.status === "BREAKDOWN").map(a => a.fleetNumber ?? a.name).join(", ")} — currently unserviceable
            </div>
          </div>
        </div>
      )}

      {/* Service due banner */}
      {assets.filter(a => a.dueForService).length > 0 && (
        <div style={{ marginBottom: 16, padding: "12px 16px", background: "#FFFBEB", border: "1px solid #FDE68A", borderRadius: 10, display: "flex", alignItems: "center", gap: 10 }}>
          <AlertTriangle size={17} color="#D97706" style={{ flexShrink: 0 }} />
          <div>
            <div style={{ fontWeight: 700, fontSize: 13, color: "#D97706" }}>Service Due</div>
            <div style={{ fontSize: 12, color: "#92400E" }}>
              {assets.filter(a => a.dueForService).map(a => a.fleetNumber ?? a.name).join(", ")} — schedule maintenance
            </div>
          </div>
        </div>
      )}

      {/* Toolbar */}
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 16, flexWrap: "wrap", gap: 10 }}>
        <div style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>
          {["ALL", ...STATUSES].map(s => (
            <button key={s} onClick={() => setFilterStatus(s)}
              style={{ padding: "5px 12px", borderRadius: 20, fontSize: 12, cursor: "pointer", border: "none", fontWeight: filterStatus === s ? 600 : 400,
                background: filterStatus === s ? (s === "ALL" ? "#1B3A6B" : STATUS_CFG[s]?.color ?? "#1B3A6B") : "#F1F5F9",
                color: filterStatus === s ? "#fff" : "#64748B" }}>
              {s === "ALL" ? "All" : STATUS_CFG[s]?.label ?? s}
            </button>
          ))}
        </div>
        <button onClick={() => { setShowAdd(true); setForm(EMPTY_FORM); setFieldErrors({}); setApiError("") }}
          style={{ display: "flex", alignItems: "center", gap: 7, background: "#1B3A6B", color: "#fff", border: "none", borderRadius: 8, padding: "9px 18px", fontSize: 14, fontWeight: 600, cursor: "pointer" }}>
          <Plus size={15} /> Register Equipment
        </button>
      </div>

      {/* Type filter chips */}
      <div style={{ display: "flex", gap: 6, flexWrap: "wrap", marginBottom: 18 }}>
        {["ALL", ...ASSET_TYPES].map(t => (
          <button key={t} onClick={() => setFilterType(t)}
            style={{ padding: "4px 10px", borderRadius: 20, fontSize: 11, cursor: "pointer", border: "1px solid",
              borderColor: filterType === t ? "#D97706" : "#E2E8F0",
              background: filterType === t ? "#FFFBEB" : "#fff",
              color: filterType === t ? "#D97706" : "#64748B", fontWeight: filterType === t ? 600 : 400 }}>
            {t === "ALL" ? "All types" : `${EMOJI[t] ?? "🚧"} ${t}`}
          </button>
        ))}
      </div>

      {/* Asset list */}
      {isLoading ? (
        <div style={{ textAlign: "center", padding: 40, color: "#94A3B8" }}>Loading fleet...</div>
      ) : filtered.length === 0 ? (
        <div style={{ textAlign: "center", padding: "60px 20px", color: "#94A3B8" }}>
          <Truck size={40} style={{ marginBottom: 12, opacity: 0.4 }} />
          <div style={{ fontWeight: 600, color: "#475569" }}>No assets found</div>
        </div>
      ) : (
        <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
          {filtered.map(asset => {
            const cfg = STATUS_CFG[asset.status] ?? STATUS_CFG.AVAILABLE
            const isOpen = expanded === asset.id
            const hoursUsed = (asset.currentHours ?? 0) - (asset.lastServiceHours ?? 0)
            const svcPct = Math.min(100, (hoursUsed / (asset.serviceIntervalHours || 250)) * 100)

            return (
              <div key={asset.id} style={{ border: `1px solid ${asset.status === "BREAKDOWN" ? "#FECACA" : "#E2E8F0"}`, borderRadius: 12, overflow: "hidden" }}>

                {/* Main row */}
                <div style={{ padding: "16px 20px", background: "#fff", display: "flex", alignItems: "center", justifyContent: "space-between", gap: 14 }}>
                  <div style={{ display: "flex", alignItems: "center", gap: 14, flex: 1, minWidth: 0 }}>
                    <div style={{ width: 48, height: 48, borderRadius: 12, background: "#F8FAFC", border: "1px solid #E2E8F0", display: "flex", alignItems: "center", justifyContent: "center", fontSize: 24, flexShrink: 0 }}>
                      {EMOJI[asset.assetType] ?? "🚧"}
                    </div>
                    <div style={{ minWidth: 0 }}>
                      <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 3, flexWrap: "wrap" }}>
                        {asset.fleetNumber && (
                          <span style={{ fontWeight: 800, fontSize: 13, color: "#D97706", background: "#FFFBEB", border: "1px solid #FDE68A", padding: "1px 8px", borderRadius: 6, flexShrink: 0 }}>
                            {asset.fleetNumber}
                          </span>
                        )}
                        <span style={{ fontWeight: 700, fontSize: 15, color: "#0F172A" }}>{asset.name}</span>
                        {asset.dueForService && (
                          <span style={{ display: "flex", alignItems: "center", gap: 3, background: "#FEF3C7", color: "#D97706", padding: "1px 7px", borderRadius: 20, fontSize: 10, fontWeight: 700, border: "1px solid #FDE68A", flexShrink: 0 }}>
                            <AlertTriangle size={9} /> SVC DUE
                          </span>
                        )}
                        {asset.ownershipType !== "OWN" && (
                          <span style={{ fontSize: 10, fontWeight: 700, background: OWN_TYPE_CFG[asset.ownershipType]?.bg ?? "#F1F5F9", color: OWN_TYPE_CFG[asset.ownershipType]?.color ?? "#64748B", padding: "1px 7px", borderRadius: 20, flexShrink: 0 }}>
                            {OWN_TYPE_CFG[asset.ownershipType]?.label}
                          </span>
                        )}
                      </div>
                      <div style={{ fontSize: 12, color: "#94A3B8" }}>
                        {[asset.make, asset.model, asset.year].filter(Boolean).join(" · ")}
                        {asset.registration && ` · ${asset.registration}`}
                        {asset.currentSite && ` · 📍 ${asset.currentSite}`}
                      </div>
                    </div>
                  </div>

                  <div style={{ display: "flex", alignItems: "center", gap: 10, flexShrink: 0 }}>
                    <div style={{ textAlign: "right" }}>
                      <div style={{ fontSize: 13, fontWeight: 600, color: "#0F172A" }}>{Number(asset.currentHours ?? 0).toLocaleString()} hrs</div>
                      <div style={{ fontSize: 11, color: "#94A3B8" }}>{fmtR(asset.dailyRate)}/day</div>
                    </div>
                    <StatusBadge status={asset.status} />
                    <div style={{ display: "flex", gap: 5 }}>
                      <button onClick={() => setViewing(asset)} title="View profile"
                        style={{ background: "#EFF6FF", border: "none", borderRadius: 6, padding: "6px 8px", cursor: "pointer", color: "#1D4ED8" }}>
                        <Eye size={13} />
                      </button>
                      <button onClick={() => { setShowHours(asset); setNewHours(String(asset.currentHours ?? 0)) }} title="Update hour meter"
                        style={{ background: "#F0FDF4", border: "none", borderRadius: 6, padding: "6px 8px", cursor: "pointer", color: "#166534" }}>
                        <Clock size={13} />
                      </button>
                      <button onClick={() => { setShowStatus(asset); setNewStatus(asset.status); setStatusNote(""); setApiError("") }} title="Change status"
                        style={{ background: "#FEF3C7", border: "none", borderRadius: 6, padding: "6px 8px", cursor: "pointer", color: "#D97706" }}>
                        <Edit2 size={13} />
                      </button>
                    </div>
                    <button onClick={() => setExpanded(isOpen ? null : asset.id)} style={{ background: "none", border: "none", cursor: "pointer", color: "#94A3B8" }}>
                      {isOpen ? <ChevronUp size={18} /> : <ChevronDown size={18} />}
                    </button>
                  </div>
                </div>

                {/* Service progress bar */}
                <div style={{ padding: "0 20px 12px", background: "#fff" }}>
                  <div style={{ display: "flex", justifyContent: "space-between", fontSize: 10, color: "#94A3B8", marginBottom: 3 }}>
                    <span>Service interval</span>
                    <span>{hoursUsed.toFixed(0)} / {asset.serviceIntervalHours || 250} hrs since last service</span>
                  </div>
                  <div style={{ height: 5, background: "#F1F5F9", borderRadius: 99, overflow: "hidden" }}>
                    <div style={{ height: "100%", width: `${svcPct}%`, borderRadius: 99, transition: "width 0.4s",
                      background: svcPct >= 100 ? "#DC2626" : svcPct >= 80 ? "#D97706" : "#0D9488" }} />
                  </div>
                </div>

                {/* Expanded detail */}
                {isOpen && (
                  <div style={{ borderTop: "1px solid #F1F5F9", padding: "16px 20px", background: "#F8FAFC" }}>
                    <div style={{ display: "grid", gridTemplateColumns: "repeat(4,1fr)", gap: 14 }}>
                      {[
                        { l: "Fleet No.",    v: asset.fleetNumber || "—" },
                        { l: "Equipment",    v: asset.assetType },
                        { l: "Serial No.",   v: asset.serialNumber || "—" },
                        { l: "Ownership",    v: OWN_TYPE_CFG[asset.ownershipType]?.label ?? asset.ownershipType },
                        { l: "Current Site", v: asset.currentSite || "—" },
                        { l: "Client",       v: asset.currentClient || "—" },
                        { l: "Daily Rate",   v: fmtR(asset.dailyRate) },
                        { l: "Hourly Rate",  v: fmtR(asset.hourlyRate) },
                      ].map(item => (
                        <div key={item.l}>
                          <div style={{ fontSize: 10, color: "#94A3B8", fontWeight: 700, textTransform: "uppercase" as const, letterSpacing: "0.06em", marginBottom: 3 }}>{item.l}</div>
                          <div style={{ fontSize: 14, fontWeight: 600, color: "#0F172A" }}>{item.v}</div>
                        </div>
                      ))}
                    </div>
                    {asset.ownershipType === "HIRED_IN" && asset.hireSupplier && (
                      <div style={{ marginTop: 12, padding: "8px 12px", background: "#F5F3FF", border: "1px solid #DDD6FE", borderRadius: 8, fontSize: 12, color: "#5B21B6" }}>
                        <strong>Hired from:</strong> {asset.hireSupplier}
                        {asset.hireEndDate && ` · Hire ends ${new Date(asset.hireEndDate).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric" })}`}
                      </div>
                    )}
                    {asset.notes && (
                      <div style={{ marginTop: 10, padding: "8px 12px", background: "#FFFBEB", border: "1px solid #FDE68A", borderRadius: 8, fontSize: 13, color: "#78350F" }}>
                        {asset.notes}
                      </div>
                    )}
                  </div>
                )}
              </div>
            )
          })}
        </div>
      )}

      {/* ── Register Asset Modal ──────────────────────────────────────────────── */}
      {showAdd && (
        <Overlay onClose={() => setShowAdd(false)}>
          <ModalHead title="Register Heavy Equipment" onClose={() => setShowAdd(false)} />

          <Sect title="Identification">
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14 }}>
              <div>
                <label style={lbl}>Asset Name *</label>
                <input autoFocus value={form.name}
                  onChange={e => { setForm(f => ({ ...f, name: e.target.value })); setFieldErrors(f => omit(f, "name")) }}
                  placeholder="CAT D9T Dozer" style={inp("name")} />
                <FErr k="name" />
              </div>
              <div>
                <label style={lbl}>Fleet / Unit Number</label>
                <input value={form.fleetNumber} onChange={e => setForm(f => ({ ...f, fleetNumber: e.target.value }))}
                  placeholder="D9-001" style={inp("fleetNumber")} />
                <div style={{ fontSize: 11, color: "#94A3B8", marginTop: 3 }}>
                  Identifies this machine in a fleet of similar units (e.g. D9-001, D9-002)
                </div>
              </div>
              <div>
                <label style={lbl}>Equipment Type *</label>
                <select value={form.assetType} onChange={e => setForm(f => ({ ...f, assetType: e.target.value }))} style={{ ...inp("assetType"), background: "#fff" }}>
                  {ASSET_TYPES.map(t => <option key={t} value={t}>{EMOJI[t] ?? "🚧"} {t}</option>)}
                </select>
              </div>
              <div>
                <label style={lbl}>Ownership</label>
                <select value={form.ownershipType} onChange={e => setForm(f => ({ ...f, ownershipType: e.target.value }))} style={{ ...inp("ownershipType"), background: "#fff" }}>
                  <option value="OWN">Own asset</option>
                  <option value="HIRED_IN">Hired in (from external supplier)</option>
                </select>
              </div>
            </div>
          </Sect>

          {form.ownershipType === "HIRED_IN" && (
            <Sect title="Hire Details">
              <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14 }}>
                <div style={{ gridColumn: "1 / -1" }}>
                  <label style={lbl}>Supplier / Owner Name</label>
                  <input value={form.hireSupplier} onChange={e => setForm(f => ({ ...f, hireSupplier: e.target.value }))}
                    placeholder="Barloworld Equipment" style={inp("hireSupplier")} />
                </div>
                <div>
                  <label style={lbl}>Hire Start Date</label>
                  <input type="date" value={form.hireStartDate} onChange={e => setForm(f => ({ ...f, hireStartDate: e.target.value }))} style={inp("hireStartDate")} />
                </div>
                <div>
                  <label style={lbl}>Hire End Date</label>
                  <input type="date" value={form.hireEndDate} onChange={e => setForm(f => ({ ...f, hireEndDate: e.target.value }))} style={inp("hireEndDate")} />
                </div>
              </div>
            </Sect>
          )}

          <Sect title="Machine Details">
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14 }}>
              <div>
                <label style={lbl}>Make</label>
                <input value={form.make} onChange={e => setForm(f => ({ ...f, make: e.target.value }))} placeholder="Caterpillar" style={inp("make")} />
              </div>
              <div>
                <label style={lbl}>Model</label>
                <input value={form.model} onChange={e => setForm(f => ({ ...f, model: e.target.value }))} placeholder="D9T" style={inp("model")} />
              </div>
              <div>
                <label style={lbl}>Year</label>
                <input type="number" value={form.year}
                  onChange={e => { setForm(f => ({ ...f, year: e.target.value })); setFieldErrors(f => omit(f,"year")) }}
                  placeholder="2021" style={inp("year")} />
                <FErr k="year" />
              </div>
              <div>
                <label style={lbl}>Registration</label>
                <input value={form.registration} onChange={e => setForm(f => ({ ...f, registration: e.target.value }))} placeholder="GP-CAT-001" style={inp("registration")} />
              </div>
              <div style={{ gridColumn: "1 / -1" }}>
                <label style={lbl}>Serial / VIN Number</label>
                <input value={form.serialNumber} onChange={e => setForm(f => ({ ...f, serialNumber: e.target.value }))} placeholder="CAT-D9T-2021-00123" style={inp("serialNumber")} />
              </div>
            </div>
          </Sect>

          <Sect title="Rates">
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14 }}>
              <div>
                <label style={lbl}>Daily Rate (R)</label>
                <input type="number" value={form.dailyRate}
                  onChange={e => { setForm(f => ({ ...f, dailyRate: e.target.value })); setFieldErrors(f => omit(f,"dailyRate")) }}
                  placeholder="18500" style={inp("dailyRate")} />
                <FErr k="dailyRate" />
              </div>
              <div>
                <label style={lbl}>Hourly Rate (R)</label>
                <input type="number" value={form.hourlyRate} onChange={e => setForm(f => ({ ...f, hourlyRate: e.target.value }))} placeholder="2200" style={inp("hourlyRate")} />
              </div>
            </div>
          </Sect>

          <div>
            <label style={lbl}>Notes</label>
            <textarea value={form.notes} onChange={e => setForm(f => ({ ...f, notes: e.target.value }))} rows={2} style={{ ...inp("notes"), resize: "vertical" as const }} placeholder="Condition notes, attachments, history..." />
          </div>

          {apiError && <ErrBanner msg={apiError} />}

          <ModalFoot
            onCancel={() => setShowAdd(false)}
            onSubmit={() => {
              if (validate()) createAsset.mutate({
                name: form.name, fleetNumber: form.fleetNumber || null, assetType: form.assetType,
                make: form.make || null, model: form.model || null,
                year: form.year ? Number(form.year) : null,
                serialNumber: form.serialNumber || null, registration: form.registration || null,
                ownershipType: form.ownershipType,
                hireSupplier: form.hireSupplier || null,
                hireStartDate: form.hireStartDate || null, hireEndDate: form.hireEndDate || null,
                dailyRate: form.dailyRate ? Number(form.dailyRate) : null,
                hourlyRate: form.hourlyRate ? Number(form.hourlyRate) : null,
                notes: form.notes || null,
              })
            }}
            loading={createAsset.isPending}
            label="Register Equipment"
          />
        </Overlay>
      )}

      {/* ── Update Status Modal ───────────────────────────────────────────────── */}
      {showStatus && (
        <Overlay onClose={() => { setShowStatus(null); setApiError("") }}>
          <ModalHead title={`Update Status — ${showStatus.fleetNumber ?? showStatus.name}`} onClose={() => { setShowStatus(null); setApiError("") }} />
          <div style={{ display: "flex", flexDirection: "column", gap: 8, marginBottom: 16 }}>
            {STATUSES.map(s => {
              const cfg = STATUS_CFG[s]
              const Icon = cfg.icon
              const sel = newStatus === s
              return (
                <button key={s} onClick={() => setNewStatus(s)}
                  style={{ display: "flex", alignItems: "center", gap: 12, padding: "12px 16px", border: `2px solid ${sel ? cfg.color : "#E2E8F0"}`, borderRadius: 10, cursor: "pointer", background: sel ? cfg.bg : "#fff", textAlign: "left" as const, width: "100%" }}>
                  <div style={{ width: 32, height: 32, borderRadius: "50%", background: `${cfg.color}18`, display: "flex", alignItems: "center", justifyContent: "center", flexShrink: 0 }}>
                    <Icon size={15} color={cfg.color} />
                  </div>
                  <div style={{ flex: 1 }}>
                    <div style={{ fontWeight: 600, fontSize: 14, color: sel ? cfg.color : "#0F172A" }}>{cfg.label}</div>
                    <div style={{ fontSize: 11, color: "#94A3B8" }}>{STATUS_DESCRIPTIONS[s]}</div>
                  </div>
                  {sel && <CheckCircle size={16} color={cfg.color} />}
                </button>
              )
            })}
          </div>
          <div style={{ marginBottom: 14 }}>
            <label style={lbl}>Note <span style={{ fontWeight: 400, color: "#94A3B8" }}>(optional)</span></label>
            <input value={statusNote} onChange={e => setStatusNote(e.target.value)}
              placeholder={newStatus === "BREAKDOWN" ? "Describe the breakdown..." : newStatus === "DEPLOYED" ? "Site name and client..." : ""}
              style={{ ...inp("_"), width: "100%" }} />
          </div>
          {apiError && <ErrBanner msg={apiError} />}
          <ModalFoot
            onCancel={() => { setShowStatus(null); setApiError("") }}
            onSubmit={() => updateStatus.mutate({ id: showStatus.id, status: newStatus, note: statusNote })}
            loading={updateStatus.isPending}
            label="Update Status"
            disabled={!newStatus || newStatus === showStatus.status}
          />
        </Overlay>
      )}

      {/* ── Hour Meter Modal ──────────────────────────────────────────────────── */}
      {showHours && (
        <Overlay onClose={() => { setShowHours(null); setApiError("") }}>
          <ModalHead title={`Hour Meter — ${showHours.fleetNumber ?? showHours.name}`} onClose={() => { setShowHours(null); setApiError("") }} />
          <div style={{ marginBottom: 20, padding: "16px 18px", background: "#F8FAFC", border: "1px solid #E2E8F0", borderRadius: 10 }}>
            <div style={{ fontSize: 11, fontWeight: 700, color: "#94A3B8", textTransform: "uppercase" as const, marginBottom: 4 }}>Current reading</div>
            <div style={{ fontSize: 30, fontWeight: 800, color: "#1B3A6B" }}>{Number(showHours.currentHours ?? 0).toLocaleString()} hrs</div>
          </div>
          <div style={{ marginBottom: 14 }}>
            <label style={lbl}>New Hour Meter Reading *</label>
            <input type="number" value={newHours} onChange={e => setNewHours(e.target.value)} autoFocus
              placeholder="Enter current meter reading"
              style={{ ...inp("_"), width: "100%", fontSize: 20, fontWeight: 700 }} />
            {Number(newHours) < Number(showHours.currentHours ?? 0) && newHours !== "" && (
              <div style={{ marginTop: 8, padding: "8px 12px", background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 7, fontSize: 12, color: "#DC2626", display: "flex", alignItems: "center", gap: 6 }}>
                <AlertCircle size={12} /> New reading is lower than current — check this is correct.
              </div>
            )}
          </div>
          <div style={{ padding: "10px 14px", background: "#F0F9FF", border: "1px solid #BAE6FD", borderRadius: 8, fontSize: 12, color: "#0369A1", marginBottom: 16 }}>
            ℹ️ Update when the operator returns the machine or at the end of each shift. This drives service interval calculations.
          </div>
          {apiError && <ErrBanner msg={apiError} />}
          <ModalFoot
            onCancel={() => { setShowHours(null); setApiError("") }}
            onSubmit={() => { if (newHours) updateHours.mutate({ id: showHours.id, hours: Number(newHours) }) }}
            loading={updateHours.isPending}
            label="Update Hours"
          />
        </Overlay>
      )}

      {/* ── View Asset Modal ──────────────────────────────────────────────────── */}
      {viewing && (
        <Overlay onClose={() => setViewing(null)}>
          <div style={{ background: "linear-gradient(135deg, #1B3A6B 0%, #0F2A52 100%)", margin: "-28px -28px 24px", padding: "24px 28px", borderRadius: "16px 16px 0 0" }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start" }}>
              <div style={{ display: "flex", alignItems: "center", gap: 14 }}>
                <div style={{ width: 56, height: 56, borderRadius: 14, background: "rgba(255,255,255,0.15)", display: "flex", alignItems: "center", justifyContent: "center", fontSize: 28 }}>
                  {EMOJI[viewing.assetType] ?? "🚧"}
                </div>
                <div>
                  <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 4 }}>
                    {viewing.fleetNumber && (
                      <span style={{ background: "#D97706", color: "#fff", padding: "2px 10px", borderRadius: 6, fontSize: 13, fontWeight: 800 }}>{viewing.fleetNumber}</span>
                    )}
                    <h3 style={{ margin: 0, fontSize: 18, fontWeight: 800, color: "#fff" }}>{viewing.name}</h3>
                  </div>
                  <div style={{ fontSize: 13, color: "rgba(255,255,255,0.7)" }}>
                    {[viewing.make, viewing.model, viewing.year].filter(Boolean).join(" · ")}
                    {viewing.registration && ` · ${viewing.registration}`}
                  </div>
                </div>
              </div>
              <button onClick={() => setViewing(null)} style={{ background: "rgba(255,255,255,0.15)", border: "none", borderRadius: 8, cursor: "pointer", color: "#fff", padding: 6, display: "flex" }}><X size={18} /></button>
            </div>
            <div style={{ display: "flex", gap: 10, marginTop: 14, flexWrap: "wrap" }}>
              <StatusBadge status={viewing.status} />
              {viewing.ownershipType !== "OWN" && (
                <span style={{ background: "rgba(255,255,255,0.2)", color: "#fff", padding: "3px 10px", borderRadius: 20, fontSize: 11, fontWeight: 700 }}>
                  {OWN_TYPE_CFG[viewing.ownershipType]?.label}
                </span>
              )}
              {viewing.dueForService && (
                <span style={{ background: "#FEF3C7", color: "#D97706", padding: "3px 10px", borderRadius: 20, fontSize: 11, fontWeight: 700, border: "1px solid #FDE68A" }}>⚠ Service Due</span>
              )}
            </div>
          </div>

          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 10, marginBottom: 16 }}>
            {[
              { l: "Current Hours",  v: `${Number(viewing.currentHours ?? 0).toLocaleString()} hrs` },
              { l: "Last Service",   v: `${Number(viewing.lastServiceHours ?? 0).toLocaleString()} hrs` },
              { l: "Serial Number",  v: viewing.serialNumber || "—" },
              { l: "Daily Rate",     v: fmtR(viewing.dailyRate) },
              { l: "Current Site",   v: viewing.currentSite || "—" },
              { l: "Current Client", v: viewing.currentClient || "—" },
            ].map(item => (
              <div key={item.l} style={{ padding: "10px 14px", background: "#F8FAFC", borderRadius: 8 }}>
                <div style={{ fontSize: 10, fontWeight: 700, color: "#94A3B8", textTransform: "uppercase" as const, letterSpacing: "0.06em", marginBottom: 3 }}>{item.l}</div>
                <div style={{ fontSize: 14, fontWeight: 600, color: "#0F172A" }}>{item.v}</div>
              </div>
            ))}
          </div>

          {/* Service bar */}
          <div style={{ marginBottom: 16 }}>
            <div style={{ display: "flex", justifyContent: "space-between", fontSize: 12, color: "#64748B", marginBottom: 6 }}>
              <span>Service interval progress</span>
              <span>{(Number(viewing.currentHours ?? 0) - Number(viewing.lastServiceHours ?? 0)).toFixed(0)} / {viewing.serviceIntervalHours || 250} hrs</span>
            </div>
            <div style={{ height: 8, background: "#F1F5F9", borderRadius: 99, overflow: "hidden" }}>
              {(() => {
                const pct = Math.min(100, (Number(viewing.currentHours ?? 0) - Number(viewing.lastServiceHours ?? 0)) / (viewing.serviceIntervalHours || 250) * 100)
                return <div style={{ height: "100%", width: `${pct}%`, background: pct >= 100 ? "#DC2626" : pct >= 80 ? "#D97706" : "#0D9488", borderRadius: 99 }} />
              })()}
            </div>
          </div>

          {viewing.ownershipType === "HIRED_IN" && viewing.hireSupplier && (
            <div style={{ marginBottom: 14, padding: "10px 14px", background: "#F5F3FF", border: "1px solid #DDD6FE", borderRadius: 8, fontSize: 13, color: "#5B21B6" }}>
              <strong>Hired from:</strong> {viewing.hireSupplier}
              {viewing.hireEndDate && ` · Hire ends ${new Date(viewing.hireEndDate).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric" })}`}
            </div>
          )}

          {viewing.notes && (
            <div style={{ marginBottom: 14, padding: "10px 14px", background: "#FFFBEB", border: "1px solid #FDE68A", borderRadius: 8, fontSize: 13, color: "#78350F" }}>
              <div style={{ fontSize: 10, fontWeight: 700, marginBottom: 3, textTransform: "uppercase" as const, letterSpacing: "0.06em" }}>Notes</div>
              {viewing.notes}
            </div>
          )}

          <div style={{ display: "flex", gap: 8 }}>
            <button onClick={() => { setViewing(null); setShowHours(viewing); setNewHours(String(viewing.currentHours ?? 0)) }}
              style={{ flex: 1, padding: "10px", background: "#F0FDF4", color: "#166534", border: "1px solid #86EFAC", borderRadius: 9, fontSize: 13, fontWeight: 600, cursor: "pointer" }}>
              Update Hours
            </button>
            <button onClick={() => { setViewing(null); setShowStatus(viewing); setNewStatus(viewing.status); setStatusNote("") }}
              style={{ flex: 1, padding: "10px", background: "#FFFBEB", color: "#D97706", border: "1px solid #FDE68A", borderRadius: 9, fontSize: 13, fontWeight: 600, cursor: "pointer" }}>
              Change Status
            </button>
            <button onClick={() => setViewing(null)} style={{ padding: "10px 16px", border: "1px solid #E2E8F0", borderRadius: 9, background: "#fff", fontSize: 13, cursor: "pointer", color: "#374151" }}>Close</button>
          </div>
        </Overlay>
      )}
    </div>
  )
}

// ── Sub-components ─────────────────────────────────────────────────────────────

function Overlay({ onClose, children }: { onClose: () => void; children: React.ReactNode }) {
  return (
    <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000, backdropFilter: "blur(2px)" }}>
      <div style={{ background: "#fff", borderRadius: 16, padding: 28, width: 620, maxHeight: "92vh", overflowY: "auto", boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }}>
        {children}
      </div>
    </div>
  )
}

function ModalHead({ title, onClose }: { title: string; onClose: () => void }) {
  return (
    <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 22 }}>
      <h3 style={{ margin: 0, fontSize: 17, fontWeight: 700, color: "#0F172A" }}>{title}</h3>
      <button onClick={onClose} style={{ background: "none", border: "none", cursor: "pointer", color: "#94A3B8", display: "flex" }}><X size={20} /></button>
    </div>
  )
}

function ModalFoot({ onCancel, onSubmit, loading, label, disabled = false }: { onCancel: () => void; onSubmit: () => void; loading: boolean; label: string; disabled?: boolean }) {
  return (
    <div style={{ display: "flex", gap: 10, justifyContent: "flex-end", marginTop: 20 }}>
      <button onClick={onCancel} style={{ padding: "9px 18px", border: "1px solid #E2E8F0", borderRadius: 9, background: "#fff", fontSize: 14, cursor: "pointer", color: "#374151" }}>Cancel</button>
      <button onClick={onSubmit} disabled={loading || disabled}
        style={{ padding: "9px 22px", background: loading || disabled ? "#94A3B8" : "#1B3A6B", color: "#fff", border: "none", borderRadius: 9, fontSize: 14, fontWeight: 700, cursor: loading || disabled ? "not-allowed" : "pointer" }}>
        {loading ? "Saving..." : label}
      </button>
    </div>
  )
}

function Sect({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div style={{ marginBottom: 20 }}>
      <div style={{ fontSize: 10, fontWeight: 700, color: "#94A3B8", letterSpacing: "0.07em", textTransform: "uppercase" as const, marginBottom: 12, paddingBottom: 8, borderBottom: "1px solid #F1F5F9" }}>{title}</div>
      {children}
    </div>
  )
}

function ErrBanner({ msg }: { msg: string }) {
  return (
    <div style={{ marginTop: 14, padding: "10px 12px", background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 8, fontSize: 13, color: "#DC2626", display: "flex", alignItems: "center", gap: 8 }}>
      <AlertCircle size={14} />{msg}
    </div>
  )
}

const omit = (obj: Record<string, string>, key: string) => { const n = { ...obj }; delete n[key]; return n }
const lbl: React.CSSProperties = { display: "block", fontSize: 13, fontWeight: 600, color: "#374151", marginBottom: 5 }
const inp = (k: string): React.CSSProperties => ({
  width: "100%", padding: "9px 12px", boxSizing: "border-box" as const,
  border: "1.5px solid #E2E8F0", borderRadius: 8, fontSize: 14,
  background: "#fff", outline: "none",
})

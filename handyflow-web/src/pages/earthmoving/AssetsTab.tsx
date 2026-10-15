// src/pages/earthmoving/AssetsTab.tsx
import { useState } from "react"
import {
  Plus, Truck, AlertTriangle, ChevronDown, ChevronUp,
  Edit2, Eye, Clock, CheckCircle, AlertCircle, RefreshCw,
} from "lucide-react"
import type { Asset } from "./shared/types"
import {
  STATUS_CFG, STATUS_DESCRIPTIONS, OWN_TYPE_CFG, ASSET_TYPES, STATUSES, EMOJI,
  STATUS_TRANSITIONS, authorityForStatusChange, isRelease, needsReason,
} from "./shared/constants"
import { useEarthPerms } from "./shared/useEarthPerms"
import { fmtCurrency, apiMessage, meterUnitOf, meterWordOf, inspectionAgo } from "./shared/format"
import { Overlay, ModalHead, ModalFoot, Sect, ErrBanner, lbl, inputStyle } from "./shared/Modal"
import StatusHistoryDialog from "./StatusHistoryDialog"
import MeterDialog from "./MeterDialog"
import AssetDetail from "./AssetDetail"
import { searchAssets, sortAssets, pageOf, type SortKey, type SortDir } from "./shared/fleetTable"
import { useLatestInspections, useAssets, useCreateAsset, useUpdateAsset, useUpdateAssetStatus, useUpdateAssetHours } from "./shared/hooks"

const EMPTY_FORM = {
  name: "", fleetNumber: "", assetType: "DOZER", make: "", model: "",
  year: "", serialNumber: "", registration: "",
  ownershipType: "OWN", hireSupplier: "", hireStartDate: "", hireEndDate: "",
  dailyRate: "", hourlyRate: "", serviceIntervalHours: "", notes: "", meterType: "HOURS",
}

const formFromAsset = (a: Asset) => ({
  name: a.name, fleetNumber: a.fleetNumber ?? "", assetType: a.assetType, make: a.make ?? "", model: a.model ?? "",
  year: a.year != null ? String(a.year) : "", serialNumber: a.serialNumber ?? "", registration: a.registration ?? "",
  ownershipType: a.ownershipType, hireSupplier: a.hireSupplier ?? "",
  hireStartDate: a.hireStartDate ?? "", hireEndDate: a.hireEndDate ?? "",
  dailyRate: a.dailyRate != null ? String(a.dailyRate) : "", hourlyRate: a.hourlyRate != null ? String(a.hourlyRate) : "",
  serviceIntervalHours: a.serviceIntervalHours != null ? String(a.serviceIntervalHours) : "", notes: a.notes ?? "",
  meterType: a.meterType ?? "HOURS",
})

const omit = (obj: Record<string, string>, key: string) => { const n = { ...obj }; delete n[key]; return n }

function StatusBadge({ status }: { status: string }) {
  const cfg = STATUS_CFG[status] ?? STATUS_CFG.AVAILABLE
  const Icon = cfg.icon
  return (
    <span style={{ display: "inline-flex", alignItems: "center", gap: 4, background: cfg.bg, color: cfg.color, padding: "3px 10px", borderRadius: 20, fontSize: 11, fontWeight: 700, border: `1px solid ${cfg.border}` }}>
      <Icon size={10} />{cfg.label}
    </span>
  )
}

export default function AssetsTab() {
  const perms = useEarthPerms()
  const [showAdd, setShowAdd]           = useState(false)
  const [editing, setEditing]           = useState<Asset | null>(null)
  const [showStatus, setShowStatus]     = useState<Asset | null>(null)
  const [showHours, setShowHours]       = useState<Asset | null>(null)
  const [showHistory, setShowHistory]   = useState<Asset | null>(null)
  const [showMeter, setShowMeter]       = useState<Asset | null>(null)
  const [overrideOn, setOverrideOn]     = useState(false)
  const [overrideReason, setOverrideReason] = useState("")
  const [viewing, setViewing]           = useState<Asset | null>(null)
  const [expanded, setExpanded]         = useState<string | null>(null)
  const [filterStatus, setFilterStatus] = useState("ALL")
  const [filterType, setFilterType]     = useState("ALL")
  const [search, setSearch]             = useState("")
  const [view, setView]                 = useState<"cards" | "table">("cards")
  const [sortKey, setSortKey]           = useState<SortKey>("fleetNumber")
  const [sortDir, setSortDir]           = useState<SortDir>("asc")
  const [page, setPage]                 = useState(0)
  const [form, setForm]                 = useState(EMPTY_FORM)
  const [newStatus, setNewStatus]       = useState("")
  const [statusNote, setStatusNote]     = useState("")
  const [newHours, setNewHours]         = useState("")
  const [fieldErrors, setFieldErrors]   = useState<Record<string, string>>({})
  const [apiError, setApiError]         = useState("")

  const { data: assets = [], isLoading } = useAssets()
  const { data: latestInspections = [] } = useLatestInspections()

  const createAsset = useCreateAsset(() => {
    setShowAdd(false); setForm(EMPTY_FORM); setFieldErrors({}); setApiError("")
  })

  const updateAsset = useUpdateAsset((updated) => {
    setEditing(null); setForm(EMPTY_FORM); setFieldErrors({}); setApiError("")
    if (updated?.id) setViewing(updated)
  })

  const formOpen = showAdd || editing !== null
  const closeForm = () => { setShowAdd(false); setEditing(null); setApiError("") }
  const openEdit = (a: Asset) => {
    setViewing(null); setShowAdd(false); setEditing(a); setForm(formFromAsset(a)); setFieldErrors({}); setApiError("")
  }

  // Status dialog: only the moves the server allows from here, and only those this person holds the permission for.
  // DEPLOYED is never offered: use Deployments, which records the site and client.
  const statusChoices = (from: string) => (STATUS_TRANSITIONS[from] ?? []).map(to => ({
    to, authority: authorityForStatusChange(from, to), allowed: perms.has(authorityForStatusChange(from, to)),
  }))

  const updateStatus = useUpdateAssetStatus((updated) => {
    setShowStatus(null); setNewStatus(""); setStatusNote(""); setApiError(""); setOverrideOn(false); setOverrideReason("")
    if (updated?.id) setViewing(updated)
  })

  const updateHours = useUpdateAssetHours(() => {
    setShowHours(null); setNewHours(""); setApiError("")
  })

  const validate = () => {
    const errs: Record<string, string> = {}
    if (!form.name.trim()) errs.name = "Asset name is required"
    if (form.year && (isNaN(Number(form.year)) || Number(form.year) < 1950 || Number(form.year) > new Date().getFullYear() + 1))
      errs.year = "Enter a valid year"
    if (form.dailyRate && isNaN(Number(form.dailyRate))) errs.dailyRate = "Must be a number"
    setFieldErrors(errs)
    return Object.keys(errs).length === 0
  }

  const filtered = sortAssets(searchAssets(assets.filter(a => {
    if (filterStatus !== "ALL" && a.status !== filterStatus) return false
    if (filterType !== "ALL" && a.assetType !== filterType) return false
    return true
  }), search), sortKey, sortDir)
  const paged = pageOf(filtered, page)
  const pageItems = paged.items
  const sortBy = (key: SortKey) => { if (key === sortKey) setSortDir(d => (d === "asc" ? "desc" : "asc")); else { setSortKey(key); setSortDir("asc") } setPage(0) }

  const renderTable = () => {
    const head = (key: SortKey, label: string, right = false) => (
      <th key={key} aria-sort={sortKey === key ? (sortDir === "asc" ? "ascending" : "descending") : "none"} style={{ textAlign: right ? "right" : "left", padding: "8px 10px", fontSize: 12, color: "var(--hf-text-muted)", fontWeight: 600 }}>
        <button onClick={() => sortBy(key)} style={{ background: "none", border: "none", cursor: "pointer", font: "inherit", color: "inherit", padding: 0 }}>{label}{sortKey === key ? (sortDir === "asc" ? " ▲" : " ▼") : ""}</button>
      </th>)
    const plain = (label: string) => <th key={label} style={{ textAlign: "left", padding: "8px 10px", fontSize: 12, color: "var(--hf-text-muted)", fontWeight: 600 }}>{label}</th>
    const td: React.CSSProperties = { padding: "8px 10px", fontSize: 13, color: "var(--hf-text)" }
    return (
      <div style={{ border: "1px solid var(--hf-border)", borderRadius: 10, overflowX: "auto", background: "var(--hf-surface)" }}>
        <table style={{ width: "100%", borderCollapse: "collapse" }} aria-label="Fleet table">
          <thead><tr>
            {head("fleetNumber", "Fleet no.")}{head("name", "Machine")}{head("assetType", "Type")}{head("status", "Status")}
            {plain("Site / client")}{head("currentHours", "Meter", true)}{head("dailyRate", "Daily rate", true)}{plain("Last inspected")}<th />
          </tr></thead>
          <tbody>{pageItems.map(a => {
            const li = latestInspections.find(x => x.assetId === a.id)
            return (
              <tr key={a.id} style={{ borderTop: "1px solid var(--hf-border)" }}>
                <td style={td}>{a.fleetNumber ?? "—"}</td>
                <td style={td}>{a.name}{a.dueForService ? " ⚠" : ""}</td>
                <td style={td}>{a.assetType}</td>
                <td style={td}><StatusBadge status={a.status} /></td>
                <td style={td}>{[a.currentSite, a.currentClient].filter(Boolean).join(" · ") || "—"}</td>
                <td style={{ ...td, textAlign: "right" }}>{Number(a.currentHours ?? 0).toLocaleString()} {meterUnitOf(a)}</td>
                <td style={{ ...td, textAlign: "right" }}>{fmtCurrency(a.dailyRate)}</td>
                <td style={{ ...td, color: li?.result === "UNSAFE" ? "var(--hf-danger-text)" : "var(--hf-text)" }}>{inspectionAgo(li?.performedAt)}</td>
                <td style={td}><button onClick={() => setViewing(a)} aria-label={`Open ${a.fleetNumber ?? a.name}`} style={{ background: "none", border: "1px solid var(--hf-border)", borderRadius: 7, padding: "3px 10px", cursor: "pointer", fontSize: 12, color: "var(--hf-text-secondary)" }}>Open</button></td>
              </tr>)
          })}</tbody>
        </table>
      </div>)
  }

  const renderPager = () => paged.pages <= 1 && filtered.length <= pageItems.length ? (
    <div style={{ fontSize: 12, color: "var(--hf-text-muted)", marginTop: 10 }}>{filtered.length} machine{filtered.length === 1 ? "" : "s"}</div>
  ) : (
    <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginTop: 12, fontSize: 12, color: "var(--hf-text-muted)" }}>
      <span>Showing {paged.from}–{paged.to} of {filtered.length}</span>
      <span style={{ display: "flex", gap: 6, alignItems: "center" }}>
        <button disabled={paged.page === 0} onClick={() => setPage(paged.page - 1)} style={{ padding: "4px 10px", borderRadius: 7, border: "1px solid var(--hf-border)", background: "var(--hf-surface)", cursor: paged.page === 0 ? "default" : "pointer" }}>Previous</button>
        <span>Page {paged.page + 1} of {paged.pages}</span>
        <button disabled={paged.page >= paged.pages - 1} onClick={() => setPage(paged.page + 1)} style={{ padding: "4px 10px", borderRadius: 7, border: "1px solid var(--hf-border)", background: "var(--hf-surface)", cursor: paged.page >= paged.pages - 1 ? "default" : "pointer" }}>Next</button>
      </span>
    </div>)

  const stats = [
    { label: "Total fleet", value: assets.length, color: "var(--hf-primary-text)" },
    { label: "Available",   value: assets.filter(a => a.status === "AVAILABLE").length, color: "var(--hf-success-text-strong)" },
    { label: "Deployed",    value: assets.filter(a => a.status === "DEPLOYED").length, color: "var(--hf-info-text)" },
    { label: "Service due", value: assets.filter(a => a.dueForService).length, color: "var(--hf-warning-text)" },
    { label: "Breakdowns",  value: assets.filter(a => a.status === "BREAKDOWN").length, color: "var(--hf-danger-text)" },
  ]

  const renderFErr = (k: string) => fieldErrors[k] ? (
    <div style={{ display: "flex", alignItems: "center", gap: 4, fontSize: 12, color: "var(--hf-danger-text)", marginTop: 4 }}>
      <AlertCircle size={12} />{fieldErrors[k]}
    </div>
  ) : null

  return (
    <div>
      {viewing ? (
        <AssetDetail asset={viewing} actions={{
          onBack: () => setViewing(null), onEdit: () => openEdit(viewing),
          onHours: () => { setShowHours(viewing); setNewHours(String(viewing.currentHours ?? 0)) },
          onStatus: () => { setShowStatus(viewing); setNewStatus(""); setStatusNote("") },
          onMeter: () => setShowMeter(viewing), onHistory: () => setShowHistory(viewing),
        }} />
      ) : (<>
      {/* Stats row */}
      <div style={{ display: "flex", gap: 12, marginBottom: 22 }}>
        {stats.map(s => (
          <div key={s.label} style={{ flex: 1, background: "var(--hf-surface-muted)", border: "1px solid var(--hf-border)", borderRadius: 10, padding: "12px 16px" }}>
            <div style={{ fontSize: 22, fontWeight: 700, color: s.color }}>{s.value}</div>
            <div style={{ fontSize: 11, color: "var(--hf-text-muted)", marginTop: 2 }}>{s.label}</div>
          </div>
        ))}
      </div>

      {assets.filter(a => a.status === "BREAKDOWN").length > 0 && (
        <div style={{ marginBottom: 16, padding: "12px 16px", background: "var(--hf-danger-soft)", border: "1px solid var(--hf-danger-border)", borderRadius: 10, display: "flex", alignItems: "center", gap: 10 }}>
          <AlertTriangle size={17} style={{ color: 'var(--hf-danger-text)', flexShrink: 0 }} />
          <div>
            <div style={{ fontWeight: 700, fontSize: 13, color: "var(--hf-danger-text)" }}>Active Breakdowns</div>
            <div style={{ fontSize: 12, color: "var(--hf-danger-text-strong)" }}>
              {assets.filter(a => a.status === "BREAKDOWN").map(a => a.fleetNumber ?? a.name).join(", ")} — currently unserviceable
            </div>
          </div>
        </div>
      )}

      {assets.filter(a => a.dueForService).length > 0 && (
        <div style={{ marginBottom: 16, padding: "12px 16px", background: "var(--hf-warning-soft)", border: "1px solid var(--hf-warning-border)", borderRadius: 10, display: "flex", alignItems: "center", gap: 10 }}>
          <AlertTriangle size={17} style={{ color: 'var(--hf-warning-text)', flexShrink: 0 }} />
          <div>
            <div style={{ fontWeight: 700, fontSize: 13, color: "var(--hf-warning-text)" }}>Service Due</div>
            <div style={{ fontSize: 12, color: "var(--hf-warning-text-deep)" }}>
              {assets.filter(a => a.dueForService).map(a => a.fleetNumber ?? a.name).join(", ")} — schedule maintenance
            </div>
          </div>
        </div>
      )}

      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 16, flexWrap: "wrap", gap: 10 }}>
        <div style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>
          {["ALL", ...STATUSES].map(s => (
            <button key={s} onClick={() => { setFilterStatus(s); setPage(0) }}
              style={{ padding: "5px 12px", borderRadius: 20, fontSize: 12, cursor: "pointer", border: "none", fontWeight: filterStatus === s ? 600 : 400,
                background: filterStatus === s ? (s === "ALL" ? "var(--hf-primary)" : STATUS_CFG[s]?.color ?? "var(--hf-primary)") : "var(--hf-surface-sunken)",
                color: filterStatus === s ? "var(--hf-text-on-solid)" : "var(--hf-text-muted)" }}>
              {s === "ALL" ? "All" : STATUS_CFG[s]?.label ?? s}
            </button>
          ))}
        </div>
        {perms.canWrite && <button onClick={() => { setEditing(null); setShowAdd(true); setForm(EMPTY_FORM); setFieldErrors({}); setApiError("") }}
          style={{ display: "flex", alignItems: "center", gap: 7, background: "var(--hf-primary)", color: "var(--hf-text-on-solid)", border: "none", borderRadius: 8, padding: "9px 18px", fontSize: 14, fontWeight: 600, cursor: "pointer" }}>
          <Plus size={15} /> Register Equipment
        </button>}
      </div>

      <div style={{ display: "flex", gap: 10, alignItems: "center", flexWrap: "wrap", marginBottom: 12 }}>
        <input aria-label="Search fleet" placeholder="Search fleet number, name, make, serial, registration, site…" value={search}
          onChange={e => { setSearch(e.target.value); setPage(0) }} style={{ ...inputStyle(), maxWidth: 380 }} />
        <span role="group" aria-label="View" style={{ display: "flex", gap: 4 }}>
          {([["cards", "Cards"], ["table", "Table"]] as const).map(([v, l]) => (
            <button key={v} aria-pressed={view === v} onClick={() => setView(v)}
              style={{ padding: "5px 12px", borderRadius: 8, fontSize: 12, fontWeight: 600, cursor: "pointer", border: "1px solid var(--hf-border)",
                background: view === v ? "var(--hf-primary)" : "var(--hf-surface)", color: view === v ? "var(--hf-text-on-solid)" : "var(--hf-text-secondary)" }}>{l}</button>
          ))}
        </span>
      </div>

      <div style={{ display: "flex", gap: 6, flexWrap: "wrap", marginBottom: 18 }}>
        {["ALL", ...ASSET_TYPES].map(t => (
          <button key={t} onClick={() => { setFilterType(t); setPage(0) }}
            style={{ padding: "4px 10px", borderRadius: 20, fontSize: 11, cursor: "pointer", border: "1px solid",
              borderColor: filterType === t ? "var(--hf-warning)" : "var(--hf-border)",
              background: filterType === t ? "var(--hf-warning-soft)" : "var(--hf-surface)",
              color: filterType === t ? "var(--hf-warning-text)" : "var(--hf-text-muted)", fontWeight: filterType === t ? 600 : 400 }}>
            {t === "ALL" ? "All types" : `${EMOJI[t] ?? "🚧"} ${t}`}
          </button>
        ))}
      </div>

      {isLoading ? (
        <div style={{ textAlign: "center", padding: 40, color: "var(--hf-text-faint)" }}>Loading fleet...</div>
      ) : filtered.length === 0 ? (
        <div style={{ textAlign: "center", padding: "60px 20px", color: "var(--hf-text-faint)" }}>
          <Truck size={40} style={{ marginBottom: 12, opacity: 0.4 }} />
          <div style={{ fontWeight: 600, color: "var(--hf-text-tertiary)" }}>No assets found</div>
        </div>
      ) : (
        <>
        {view === "table" ? renderTable() : (<div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
          {pageItems.map(asset => {
            const isOpen = expanded === asset.id
            const hoursUsed = (asset.currentHours ?? 0) - (asset.lastServiceHours ?? 0)
            const svcPct = Math.min(100, (hoursUsed / (asset.serviceIntervalHours || 250)) * 100)

            return (
              <div key={asset.id} style={{ border: `1px solid ${asset.status === "BREAKDOWN" ? "var(--hf-danger-border)" : "var(--hf-border)"}`, borderRadius: 12, overflow: "hidden" }}>
                <div style={{ padding: "16px 20px", background: "var(--hf-surface)", display: "flex", alignItems: "center", justifyContent: "space-between", gap: 14 }}>
                  <div style={{ display: "flex", alignItems: "center", gap: 14, flex: 1, minWidth: 0 }}>
                    <div style={{ width: 48, height: 48, borderRadius: 12, background: "var(--hf-surface-muted)", border: "1px solid var(--hf-border)", display: "flex", alignItems: "center", justifyContent: "center", fontSize: 24, flexShrink: 0 }}>
                      {EMOJI[asset.assetType] ?? "🚧"}
                    </div>
                    <div style={{ minWidth: 0 }}>
                      <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 3, flexWrap: "wrap" }}>
                        {asset.fleetNumber && (
                          <span style={{ fontWeight: 800, fontSize: 13, color: "var(--hf-warning-text)", background: "var(--hf-warning-soft)", border: "1px solid var(--hf-warning-border)", padding: "1px 8px", borderRadius: 6, flexShrink: 0 }}>
                            {asset.fleetNumber}
                          </span>
                        )}
                        <span style={{ fontWeight: 700, fontSize: 15, color: "var(--hf-text)" }}>{asset.name}</span>
                        {asset.dueForService && (
                          <span style={{ display: "flex", alignItems: "center", gap: 3, background: "var(--hf-warning-soft-strong)", color: "var(--hf-warning-text)", padding: "1px 7px", borderRadius: 20, fontSize: 10, fontWeight: 700, border: "1px solid var(--hf-warning-border)", flexShrink: 0 }}>
                            <AlertTriangle size={9} /> SVC DUE
                          </span>
                        )}
                        {asset.ownershipType !== "OWN" && (
                          <span style={{ fontSize: 10, fontWeight: 700, background: OWN_TYPE_CFG[asset.ownershipType]?.bg ?? "var(--hf-surface-sunken)", color: OWN_TYPE_CFG[asset.ownershipType]?.color ?? "var(--hf-text-muted)", padding: "1px 7px", borderRadius: 20, flexShrink: 0 }}>
                            {OWN_TYPE_CFG[asset.ownershipType]?.label}
                          </span>
                        )}
                      </div>
                      <div style={{ fontSize: 12, color: "var(--hf-text-faint)" }}>
                        {[asset.make, asset.model, asset.year].filter(Boolean).join(" · ")}
                        {asset.registration && ` · ${asset.registration}`}
                        {asset.currentSite && ` · 📍 ${asset.currentSite}`}
                      </div>
                    </div>
                  </div>

                  <div style={{ display: "flex", alignItems: "center", gap: 10, flexShrink: 0 }}>
                    <div style={{ textAlign: "right" }}>
                      <div style={{ fontSize: 13, fontWeight: 600, color: "var(--hf-text)" }}>{Number(asset.currentHours ?? 0).toLocaleString()} {meterUnitOf(asset)}</div>
                      <div style={{ fontSize: 11, color: "var(--hf-text-faint)" }}>{fmtCurrency(asset.dailyRate)}/day</div>
                      {(() => {
                        const li = latestInspections.find(x => x.assetId === asset.id)
                        return <div style={{ fontSize: 11, color: li?.result === "UNSAFE" ? "var(--hf-danger-text)" : "var(--hf-text-faint)" }}>
                          {inspectionAgo(li?.performedAt)}{li && li.result !== "PASS" ? (li.result === "UNSAFE" ? " · unsafe" : " · defects") : ""}
                        </div>
                      })()}
                    </div>
                    <StatusBadge status={asset.status} />
                    <div style={{ display: "flex", gap: 5 }}>
                      <button onClick={() => setViewing(asset)} title="View profile" aria-label="View profile"
                        style={{ background: "var(--hf-info-soft)", border: "none", borderRadius: 6, padding: "6px 8px", cursor: "pointer", color: "var(--hf-info-text)" }}>
                        <Eye size={13} />
                      </button>
                      {perms.canWrite && (
                        <button onClick={() => { setShowHours(asset); setNewHours(String(asset.currentHours ?? 0)) }} title={asset.meterType === "KILOMETRES" ? "Update odometer" : "Update hour meter"} aria-label={asset.meterType === "KILOMETRES" ? "Update odometer" : "Update hour meter"}
                          style={{ background: "var(--hf-success-soft)", border: "none", borderRadius: 6, padding: "6px 8px", cursor: "pointer", color: "var(--hf-success-text-strong)" }}>
                          <Clock size={13} />
                        </button>
                      )}
                      {perms.canWrite && (
                        <button onClick={() => openEdit(asset)} title="Edit details" aria-label="Edit details"
                          style={{ background: "var(--hf-surface-muted)", border: "none", borderRadius: 6, padding: "6px 8px", cursor: "pointer", color: "var(--hf-text-secondary)" }}>
                          <Edit2 size={13} />
                        </button>
                      )}
                      {perms.canChangeStatus && (
                        <button onClick={() => { setShowStatus(asset); setNewStatus(""); setStatusNote(""); setApiError("") }} title="Change status" aria-label="Change status"
                          style={{ background: "var(--hf-warning-soft-strong)", border: "none", borderRadius: 6, padding: "6px 8px", cursor: "pointer", color: "var(--hf-warning-text)" }}>
                          <RefreshCw size={13} />
                        </button>
                      )}
                    </div>
                    <button onClick={() => setExpanded(isOpen ? null : asset.id)} aria-label={isOpen ? "Collapse details" : "Expand details"}
                      style={{ background: "none", border: "none", cursor: "pointer", color: "var(--hf-text-faint)" }}>
                      {isOpen ? <ChevronUp size={18} /> : <ChevronDown size={18} />}
                    </button>
                  </div>
                </div>

                <div style={{ padding: "0 20px 12px", background: "var(--hf-surface)" }}>
                  <div style={{ display: "flex", justifyContent: "space-between", fontSize: 10, color: "var(--hf-text-faint)", marginBottom: 3 }}>
                    <span>Service interval</span>
                    <span>{hoursUsed.toFixed(0)} / {asset.serviceIntervalHours || 250} {meterUnitOf(asset)} since last service</span>
                  </div>
                  <div style={{ height: 5, background: "var(--hf-surface-sunken)", borderRadius: 99, overflow: "hidden" }}>
                    <div style={{ height: "100%", width: `${svcPct}%`, borderRadius: 99, transition: "width 0.4s",
                      background: svcPct >= 100 ? "var(--hf-danger)" : svcPct >= 80 ? "var(--hf-warning)" : "var(--hf-accent)" }} />
                  </div>
                </div>

                {isOpen && (
                  <div style={{ borderTop: "1px solid var(--hf-border-subtle)", padding: "16px 20px", background: "var(--hf-surface-muted)" }}>
                    <div style={{ display: "grid", gridTemplateColumns: "repeat(4,1fr)", gap: 14 }}>
                      {[
                        { l: "Fleet No.", v: asset.fleetNumber || "—" },
                        { l: "Equipment", v: asset.assetType },
                        { l: "Serial No.", v: asset.serialNumber || "—" },
                        { l: "Ownership", v: OWN_TYPE_CFG[asset.ownershipType]?.label ?? asset.ownershipType },
                        { l: "Current Site", v: asset.currentSite || "—" },
                        { l: "Client", v: asset.currentClient || "—" },
                        { l: "Daily Rate", v: fmtCurrency(asset.dailyRate) },
                        { l: "Hourly Rate", v: fmtCurrency(asset.hourlyRate) },
                      ].map(item => (
                        <div key={item.l}>
                          <div style={{ fontSize: 10, color: "var(--hf-text-faint)", fontWeight: 700, textTransform: "uppercase" as const, letterSpacing: "0.06em", marginBottom: 3 }}>{item.l}</div>
                          <div style={{ fontSize: 14, fontWeight: 600, color: "var(--hf-text)" }}>{item.v}</div>
                        </div>
                      ))}
                    </div>
                    {asset.ownershipType === "HIRED_IN" && asset.hireSupplier && (
                      <div style={{ marginTop: 12, padding: "8px 12px", background: "var(--hf-violet-soft)", border: "1px solid var(--hf-violet-border)", borderRadius: 8, fontSize: 12, color: "var(--hf-violet-text-strong)" }}>
                        <strong>Hired from:</strong> {asset.hireSupplier}
                        {asset.hireEndDate && ` · Hire ends ${new Date(asset.hireEndDate).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric" })}`}
                      </div>
                    )}
                    {asset.notes && (
                      <div style={{ marginTop: 10, padding: "8px 12px", background: "var(--hf-warning-soft)", border: "1px solid var(--hf-warning-border)", borderRadius: 8, fontSize: 13, color: "var(--hf-warning-text-deep)" }}>
                        {asset.notes}
                      </div>
                    )}
                  </div>
                )}
              </div>
            )
          })}
        </div>)}
        {renderPager()}
        </>
      )}

      </>)}

      {/* ── Register Asset Modal ────────────────────────────────────────── */}
      {formOpen && (
        <Overlay onClose={closeForm}>
          <ModalHead title={editing ? `Edit — ${editing.fleetNumber ?? editing.name}` : "Register Heavy Equipment"} onClose={closeForm} />

          <Sect title="Identification">
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14 }}>
              <div>
                <label style={lbl}>Asset Name *</label>
                <input autoFocus value={form.name}
                  onChange={e => { setForm(f => ({ ...f, name: e.target.value })); setFieldErrors(f => omit(f, "name")) }}
                  placeholder="CAT D9T Dozer" style={inputStyle(!!fieldErrors.name)} />
                {renderFErr("name")}
              </div>
              <div>
                <label style={lbl}>Fleet / Unit Number</label>
                <input value={form.fleetNumber} onChange={e => setForm(f => ({ ...f, fleetNumber: e.target.value }))}
                  placeholder="D9-001" style={inputStyle()} />
                <div style={{ fontSize: 11, color: "var(--hf-text-faint)", marginTop: 3 }}>
                  Identifies this machine in a fleet of similar units (e.g. D9-001, D9-002)
                </div>
              </div>
              <div>
                <label style={lbl}>Equipment Type *</label>
                <select value={form.assetType} onChange={e => setForm(f => ({ ...f, assetType: e.target.value }))} style={{ ...inputStyle(), background: "var(--hf-surface)" }}>
                  {ASSET_TYPES.map(t => <option key={t} value={t}>{EMOJI[t] ?? "🚧"} {t}</option>)}
                </select>
              </div>
              <div>
                <label style={lbl}>Ownership</label>
                <select value={form.ownershipType} onChange={e => setForm(f => ({ ...f, ownershipType: e.target.value }))} style={{ ...inputStyle(), background: "var(--hf-surface)" }}>
                  <option value="OWN">Own asset</option>
                  <option value="HIRED_IN">Hired in (from external supplier)</option>
                  {editing && <option value="HIRED_OUT">Hired out (lent to a third party)</option>}
                </select>
              </div>
            </div>
          </Sect>

          {form.ownershipType !== "OWN" && (
            <Sect title="Hire Details">
              <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14 }}>
                <div style={{ gridColumn: "1 / -1" }}>
                  <label style={lbl}>Supplier / Owner Name</label>
                  <input value={form.hireSupplier} onChange={e => setForm(f => ({ ...f, hireSupplier: e.target.value }))}
                    placeholder="Barloworld Equipment" style={inputStyle()} />
                </div>
                <div>
                  <label style={lbl}>Hire Start Date</label>
                  <input type="date" value={form.hireStartDate} onChange={e => setForm(f => ({ ...f, hireStartDate: e.target.value }))} style={inputStyle()} />
                </div>
                <div>
                  <label style={lbl}>Hire End Date</label>
                  <input type="date" value={form.hireEndDate} onChange={e => setForm(f => ({ ...f, hireEndDate: e.target.value }))} style={inputStyle()} />
                </div>
              </div>
            </Sect>
          )}

          <Sect title="Machine Details">
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14 }}>
              <div>
                <label style={lbl}>Make</label>
                <input value={form.make} onChange={e => setForm(f => ({ ...f, make: e.target.value }))} placeholder="Caterpillar" style={inputStyle()} />
              </div>
              <div>
                <label style={lbl}>Model</label>
                <input value={form.model} onChange={e => setForm(f => ({ ...f, model: e.target.value }))} placeholder="D9T" style={inputStyle()} />
              </div>
              <div>
                <label style={lbl}>Year</label>
                <input type="number" value={form.year}
                  onChange={e => { setForm(f => ({ ...f, year: e.target.value })); setFieldErrors(f => omit(f, "year")) }}
                  placeholder="2021" style={inputStyle(!!fieldErrors.year)} />
                {renderFErr("year")}
              </div>
              {!editing && (
                <div>
                  <label htmlFor="asset-meter" style={lbl}>Meter counts</label>
                  <select id="asset-meter" value={form.meterType} onChange={e => setForm(f => ({ ...f, meterType: e.target.value }))} style={{ ...inputStyle(), background: "var(--hf-surface)" }}>
                    <option value="HOURS">Hours (plant)</option>
                    <option value="KILOMETRES">Kilometres (trucks)</option>
                  </select>
                </div>
              )}
              <div>
                <label style={lbl}>Registration</label>
                <input value={form.registration} onChange={e => setForm(f => ({ ...f, registration: e.target.value }))} placeholder="GP-CAT-001" style={inputStyle()} />
              </div>
              <div style={{ gridColumn: "1 / -1" }}>
                <label style={lbl}>Serial / VIN Number</label>
                <input value={form.serialNumber} onChange={e => setForm(f => ({ ...f, serialNumber: e.target.value }))} placeholder="CAT-D9T-2021-00123" style={inputStyle()} />
              </div>
            </div>
          </Sect>

          <Sect title="Rates">
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14 }}>
              <div>
                <label style={lbl}>Daily Rate (R)</label>
                <input type="number" value={form.dailyRate}
                  onChange={e => { setForm(f => ({ ...f, dailyRate: e.target.value })); setFieldErrors(f => omit(f, "dailyRate")) }}
                  placeholder="18500" style={inputStyle(!!fieldErrors.dailyRate)} />
                {renderFErr("dailyRate")}
              </div>
              <div>
                <label style={lbl}>Hourly Rate (R)</label>
                <input type="number" value={form.hourlyRate} onChange={e => setForm(f => ({ ...f, hourlyRate: e.target.value }))} placeholder="2200" style={inputStyle()} />
              </div>
              {editing && (
                <div>
                  <label style={lbl}>Service interval ({editing ? meterWordOf(editing) : form.meterType === "KILOMETRES" ? "kilometres" : "hours"})</label>
                  <input type="number" value={form.serviceIntervalHours} onChange={e => setForm(f => ({ ...f, serviceIntervalHours: e.target.value }))} placeholder="250" style={inputStyle()} />
                </div>
              )}
            </div>
          </Sect>

          <div>
            <label style={lbl}>Notes</label>
            <textarea value={form.notes} onChange={e => setForm(f => ({ ...f, notes: e.target.value }))} rows={2} style={{ ...inputStyle(), resize: "vertical" as const }} placeholder="Condition notes, attachments, history..." />
          </div>

          {apiError && <ErrBanner msg={apiError} />}

          <ModalFoot
            onCancel={closeForm}
            onSubmit={() => {
              if (!validate()) return
              const body = {
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
              }
              const onError = (e: unknown) => {
                const d = (e as { response?: { data?: { errors?: Record<string, string> } } } | null)?.response?.data
                if (d?.errors) setFieldErrors(d.errors)
                else setApiError(apiMessage(e, editing ? "Failed to save changes" : "Failed to register asset"))
              }
              if (editing) {
                updateAsset.mutate({ id: editing.id, body: { ...body, serviceIntervalHours: form.serviceIntervalHours ? Number(form.serviceIntervalHours) : null } }, { onError })
              } else {
                createAsset.mutate({ ...body, meterType: form.meterType }, { onError })
              }
            }}
            loading={createAsset.isPending || updateAsset.isPending}
            label={editing ? "Save Changes" : "Register Equipment"}
          />
        </Overlay>
      )}

      {/* ── Update Status Modal ─────────────────────────────────────────── */}
      {showStatus && (() => {
        const from = showStatus.status
        const choices = statusChoices(from)
        const release = newStatus !== "" && isRelease(from, newStatus)
        const reasonNeeded = newStatus !== "" && needsReason(from, newStatus)
        const reasonOk = !reasonNeeded || statusNote.trim().length >= 5
        const close = () => { setShowStatus(null); setNewStatus(""); setApiError(""); setOverrideOn(false); setOverrideReason("") }
        const overrideOk = !release || !overrideOn || overrideReason.trim().length >= 5
        return (
          <Overlay onClose={close}>
            <ModalHead title={`Change Status — ${showStatus.fleetNumber ?? showStatus.name}`} subtitle={`Now: ${STATUS_CFG[from]?.label ?? from}`} onClose={close} />
            {choices.length === 0 ? (
              <div style={{ padding: "14px 16px", background: "var(--hf-surface-muted)", borderRadius: 10, fontSize: 13, color: "var(--hf-text-muted)", marginBottom: 16 }}>
                A {STATUS_CFG[from]?.label.toLowerCase() ?? from.toLowerCase()} machine has no further status changes.
              </div>
            ) : (
              <div style={{ display: "flex", flexDirection: "column", gap: 8, marginBottom: 16 }}>
                {choices.map(({ to, authority, allowed }) => {
                  const cfg = STATUS_CFG[to]
                  const Icon = cfg.icon
                  const sel = newStatus === to
                  return (
                    <button key={to} disabled={!allowed} onClick={() => setNewStatus(to)} aria-pressed={sel}
                      title={allowed ? undefined : `Needs the ${authority} permission`}
                      style={{ display: "flex", alignItems: "center", gap: 12, padding: "12px 16px", border: `2px solid ${sel ? cfg.color : "var(--hf-border)"}`, borderRadius: 10, cursor: allowed ? "pointer" : "not-allowed", opacity: allowed ? 1 : 0.55, background: sel ? cfg.bg : "var(--hf-surface)", textAlign: "left" as const, width: "100%" }}>
                      <div style={{ width: 32, height: 32, borderRadius: "50%", background: `color-mix(in srgb, ${cfg.color} 9%, transparent)`, display: "flex", alignItems: "center", justifyContent: "center", flexShrink: 0 }}>
                        <Icon size={15} style={{ color: cfg.color }} />
                      </div>
                      <div style={{ flex: 1 }}>
                        <div style={{ fontWeight: 600, fontSize: 14, color: sel ? cfg.color : "var(--hf-text)" }}>
                          {isRelease(from, to) ? "Release to service" : cfg.label}
                        </div>
                        <div style={{ fontSize: 11, color: "var(--hf-text-faint)" }}>
                          {allowed ? (isRelease(from, to) ? "Repair done and checked; back in the yard, ready to deploy" : STATUS_DESCRIPTIONS[to]) : `Needs the ${authority} permission`}
                        </div>
                      </div>
                      {sel && <CheckCircle size={16} style={{ color: cfg.color }} />}
                    </button>
                  )
                })}
              </div>
            )}
            <div style={{ fontSize: 12, color: "var(--hf-text-faint)", marginBottom: 12 }}>
              To send a machine to a site, use <strong>Deployments</strong>, which records the site and client.
            </div>
            {newStatus && (
              <div style={{ marginBottom: 14 }}>
                <label style={lbl} htmlFor="status-reason">
                  Reason {reasonNeeded ? "*" : <span style={{ fontWeight: 400, color: "var(--hf-text-faint)" }}>(optional)</span>}
                </label>
                <input id="status-reason" value={statusNote} onChange={e => setStatusNote(e.target.value)}
                  placeholder={release ? "What was done and checked before release…" : newStatus === "BREAKDOWN" ? "Describe the breakdown…" : "Why is the status changing?"}
                  style={{ ...inputStyle(), width: "100%" }} />
                {release && (
                  <div style={{ marginTop: 8, fontSize: 12, color: "var(--hf-text-muted)" }}>
                    A release is recorded with your name, the time, this reason and the current hour reading. It is refused while the machine has an unresolved critical incident or an open job that blocks release.
                    It also needs a work order completed since the machine went out of service, finished by someone other than you.
                  </div>
                )}
                {release && (
                  <div style={{ marginTop: 10 }}>
                    <label style={{ display: "flex", gap: 8, alignItems: "center", fontSize: 13 }}>
                      <input type="checkbox" checked={overrideOn} onChange={e => setOverrideOn(e.target.checked)} />
                      Release without a completed work order, or after finishing the work myself
                    </label>
                    {overrideOn && (
                      <div style={{ marginTop: 8 }}>
                        <label style={lbl} htmlFor="release-override">Why is this release allowed? *</label>
                        <input id="release-override" value={overrideReason} onChange={e => setOverrideReason(e.target.value)} style={{ ...inputStyle(), width: "100%" }} />
                        <div style={{ marginTop: 4, fontSize: 12, color: "var(--hf-text-faint)" }}>Recorded in the status history and the audit trail.</div>
                      </div>
                    )}
                  </div>
                )}
              </div>
            )}
            {apiError && <ErrBanner msg={apiError} />}
            <ModalFoot
              onCancel={close}
              onSubmit={() => updateStatus.mutate(
                { id: showStatus.id, status: newStatus, note: statusNote.trim(), overrideReason: release && overrideOn ? overrideReason.trim() : undefined },
                { onError: (e: unknown) => setApiError(apiMessage(e, "Failed to update status — that change may not be allowed from the machine's current status.")) }
              )}
              loading={updateStatus.isPending}
              label={release ? "Release" : "Update Status"}
              disabled={!newStatus || !reasonOk || !overrideOk}
            />
          </Overlay>
        )
      })()}

      {showHistory && <StatusHistoryDialog asset={showHistory} onClose={() => setShowHistory(null)} />}
      {showMeter && <MeterDialog asset={showMeter} onClose={() => setShowMeter(null)} />}

      {/* ── Hour Meter Modal ────────────────────────────────────────────── */}
      {showHours && (
        <Overlay onClose={() => { setShowHours(null); setApiError("") }}>
          <ModalHead title={`${showHours.meterType === "KILOMETRES" ? "Odometer" : "Hour Meter"} — ${showHours.fleetNumber ?? showHours.name}`} onClose={() => { setShowHours(null); setApiError("") }} />
          <div style={{ marginBottom: 20, padding: "16px 18px", background: "var(--hf-surface-muted)", border: "1px solid var(--hf-border)", borderRadius: 10 }}>
            <div style={{ fontSize: 11, fontWeight: 700, color: "var(--hf-text-faint)", textTransform: "uppercase" as const, marginBottom: 4 }}>Current reading</div>
            <div style={{ fontSize: 30, fontWeight: 800, color: "var(--hf-primary-text)" }}>{Number(showHours.currentHours ?? 0).toLocaleString()} {meterUnitOf(showHours)}</div>
          </div>
          <div style={{ marginBottom: 14 }}>
            <label style={lbl}>{showHours.meterType === "KILOMETRES" ? "New Odometer Reading *" : "New Hour Meter Reading *"}</label>
            <input type="number" value={newHours} onChange={e => setNewHours(e.target.value)} autoFocus
              placeholder="Enter current meter reading"
              style={{ ...inputStyle(), width: "100%", fontSize: 20, fontWeight: 700 }} />
            {Number(newHours) < Number(showHours.currentHours ?? 0) && newHours !== "" && (
              <div style={{ marginTop: 8, padding: "8px 12px", background: "var(--hf-danger-soft)", border: "1px solid var(--hf-danger-border)", borderRadius: 7, fontSize: 12, color: "var(--hf-danger-text)", display: "flex", alignItems: "center", gap: 6 }}>
                {/* NOTE: the backend now rejects this outright (hour meters only count up) —
                    this banner is a pre-submit hint, the real enforcement lives server-side. */}
                <AlertCircle size={12} /> New reading is lower than current — the server will reject this.
              </div>
            )}
          </div>
          <div style={{ padding: "10px 14px", background: "var(--hf-sky-soft)", border: "1px solid var(--hf-sky-border)", borderRadius: 8, fontSize: 12, color: "var(--hf-sky-text-strong)", marginBottom: 16 }}>
            ℹ️ Update when the operator returns the machine or at the end of each shift. This drives service interval calculations.
          </div>
          {apiError && <ErrBanner msg={apiError} />}
          <ModalFoot
            onCancel={() => { setShowHours(null); setApiError("") }}
            onSubmit={() => {
              if (!newHours) return
              updateHours.mutate(
                { id: showHours.id, hours: Number(newHours) },
                { onError: (e: unknown) => setApiError(apiMessage(e, "Failed to update hours")) }
              )
            }}
            loading={updateHours.isPending}
            label="Update Hours"
          />
        </Overlay>
      )}

    </div>
  )
}

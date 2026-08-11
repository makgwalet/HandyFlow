// src/pages/security/SitesTab.tsx
//
// CHANGE: added checkpoint QR print + regenerate actions (previously the
// tab only ever showed a truncated, unprintable text fragment of the QR
// code -- no way to actually get a scannable image, and no way to rotate
// a compromised checkpoint's code without going around the API directly).
//   - "Print" per checkpoint -> downloads/opens a single-checkpoint QR PDF.
//   - "Print All" per site -> downloads/opens a whole-site QR sheet PDF
//     (the realistic "print once, cut out each QR, mount at its
//     checkpoint" workflow).
//   - "Regenerate" per checkpoint -> rotates that checkpoint's code only
//     (confirm() first, since it immediately invalidates whatever's
//     currently mounted at that checkpoint), then invalidates the query
//     cache so the (still-truncated) displayed code updates.
// Everything else in this file is unchanged from the original.

import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import {
  Plus, MapPin, QrCode, X, AlertCircle, ChevronDown, ChevronRight,
  Trash2, Radio, Bluetooth, Edit2, AlertTriangle, Printer, RefreshCw,
} from "lucide-react"

// ── Types ──────────────────────────────────────────────────────────────────────

interface Checkpoint {
  id: string; name: string; description: string | null
  qrCode: string; sortOrder: number
}

interface Site {
  id: string; name: string; customerId: string | null
  address: Record<string, string> | null
  latitude: number | null; longitude: number | null
  contactName: string | null; contactPhone: string | null
  active: boolean; checkpoints: Checkpoint[]
  contractStatus: string; contractStart: string | null; contractEnd: string | null
  terminationReason: string | null; terminatedAt: string | null
  createdAt: string
  requireSignedQr: boolean; branchId: string | null
}

interface Branch { id: string; name: string; region: string | null; active: boolean }

const CONTRACT_CONFIG: Record<string, { color: string; bg: string; label: string }> = {
  ACTIVE:         { color: "#166534", bg: "#DCFCE7",  label: "Active" },
  EXPIRING_SOON:  { color: "#D97706", bg: "#FFFBEB",  label: "Expiring soon" },
  EXPIRED:        { color: "#DC2626", bg: "#FEF2F2",  label: "Expired" },
  TERMINATED:     { color: "#64748B", bg: "#F1F5F9",  label: "Terminated" },
}

const EMPTY_SITE_FORM = {
  name: "", contactName: "", contactPhone: "", instructions: "",
  latitude: "", longitude: "",
}
const EMPTY_CP_FORM = { name: "", description: "" }

// ── PDF download helper ─────────────────────────────────────────────────────────
// Opens the returned PDF blob in a new tab (print/save from there) rather
// than forcing a direct download -- lets the admin preview before printing.

async function openPdfInNewTab(url: string) {
  const res = await apiClient.get(url, { responseType: "blob" })
  const blobUrl = URL.createObjectURL(new Blob([res.data], { type: "application/pdf" }))
  window.open(blobUrl, "_blank")
}

// ── Checkpoint QR thumbnail ──────────────────────────────────────────────────
// Fetches the actual QR image (authenticated blob, same pattern as the PDF
// downloads above) rather than a public/third-party QR-rendering service --
// the payload is a security-sensitive signed token and should never leave
// the app's own backend.

function CheckpointQrThumbnail({ siteId, checkpointId }: { siteId: string; checkpointId: string }) {
  const { data: imgUrl, isLoading, isError } = useQuery({
    queryKey: ["checkpoint-qr-image", checkpointId],
    queryFn: async () => {
      const res = await apiClient.get(`/api/v1/security/sites/${siteId}/checkpoints/${checkpointId}/qr-image`, { responseType: "blob" })
      return URL.createObjectURL(new Blob([res.data], { type: "image/png" }))
    },
    staleTime: Infinity, // only changes on regenerate -- that invalidates this query key directly
  })

  const boxStyle: React.CSSProperties = {
    width: 96, height: 96, borderRadius: 8, border: "1px solid #E2E8F0",
    display: "flex", alignItems: "center", justifyContent: "center", flexShrink: 0,
    background: "#F8FAFC", alignSelf: "center",
  }

  if (isLoading) return <div style={boxStyle}><div style={{ width: 44, height: 44, background: "#E2E8F0", borderRadius: 4 }} /></div>
  if (isError || !imgUrl) return <div style={boxStyle}><QrCode size={24} color="#CBD5E1" /></div>

  return <img src={imgUrl} alt="Checkpoint QR code" style={{ ...boxStyle, objectFit: "contain", padding: 6 }} />
}

// ── Component ──────────────────────────────────────────────────────────────────

export default function SitesTab() {
  const qc = useQueryClient()

  const [expanded,        setExpanded]        = useState<string | null>(null)
  const [showAddSite,     setShowAddSite]      = useState(false)
  const [showAddCp,       setShowAddCp]        = useState<string | null>(null) // siteId
  const [showTerminate,   setShowTerminate]    = useState<Site | null>(null)
  const [showDelete,      setShowDelete]       = useState<Site | null>(null)
  const [siteForm,        setSiteForm]         = useState(EMPTY_SITE_FORM)
  const [cpForm,          setCpForm]           = useState(EMPTY_CP_FORM)
  const [terminateReason, setTerminateReason]  = useState("")
  const [showPortal,      setShowPortal]        = useState<Site | null>(null)
  const [portalLabel,     setPortalLabel]        = useState("")
  const [portalToken,     setPortalToken]        = useState<string | null>(null)
  const [siteErrors,      setSiteErrors]       = useState<Record<string, string>>({})
  const [apiError,        setApiError]         = useState("")
  const [printingCpId,    setPrintingCpId]     = useState<string | null>(null)
  const [printingSiteId,  setPrintingSiteId]   = useState<string | null>(null)
  const [regeneratingCpId, setRegeneratingCpId] = useState<string | null>(null)
  const [regenerateConfirm, setRegenerateConfirm] = useState<{ siteId: string; checkpoint: Checkpoint } | null>(null)

  // ── Queries ────────────────────────────────────────────────────────────────

  const { data: sitesPage, isLoading } = useQuery({
    queryKey: ["sites"],
    queryFn: async () => {
      const res = await apiClient.get("/api/v1/security/sites?size=100")
      const payload = res.data?.data ?? res.data
      return (payload?.content ?? payload) as Site[]
    },
  })
  const sites = sitesPage ?? []

  // Site detail (with checkpoints) fetched on expand
  const { data: siteDetail } = useQuery({
    queryKey: ["site-detail", expanded],
    queryFn: async () => {
      if (!expanded) return null
      const res = await apiClient.get(`/api/v1/security/sites/${expanded}`)
      return (res.data?.data ?? res.data) as Site
    },
    enabled: !!expanded,
  })

  const { data: branches = [] } = useQuery({
    queryKey: ["branches-list"],
    queryFn: async () => {
      const res = await apiClient.get("/api/v1/security/branches")
      return (res.data?.data ?? res.data) as Branch[]
    },
  })

  // ── Mutations ──────────────────────────────────────────────────────────────

  const invalidate = () => {
    qc.invalidateQueries({ queryKey: ["sites"] })
    qc.invalidateQueries({ queryKey: ["site-detail", expanded] })
  }

  const createSite = useMutation({
    mutationFn: (body: any) => apiClient.post("/api/v1/security/sites", body),
    onSuccess: () => { invalidate(); setShowAddSite(false); setSiteForm(EMPTY_SITE_FORM); setSiteErrors({}); setApiError("") },
    onError: (e: any) => setApiError(e.response?.data?.message ?? "Failed to create site"),
  })

  const addCheckpoint = useMutation({
    mutationFn: ({ siteId, body }: { siteId: string; body: any }) =>
      apiClient.post(`/api/v1/security/sites/${siteId}/checkpoints`, body),
    onSuccess: () => { invalidate(); setShowAddCp(null); setCpForm(EMPTY_CP_FORM); setApiError("") },
    onError: (e: any) => setApiError(e.response?.data?.message ?? "Failed to add checkpoint"),
  })

  const terminateSite = useMutation({
    mutationFn: ({ id, reason }: { id: string; reason: string }) =>
      apiClient.post(`/api/v1/security/sites/${id}/terminate`, { reason }),
    onSuccess: () => { invalidate(); setShowTerminate(null); setTerminateReason(""); setApiError("") },
    onError: (e: any) => setApiError(e.response?.data?.message ?? "Failed to terminate contract"),
  })

  const deleteSite = useMutation({
    mutationFn: (id: string) => apiClient.delete(`/api/v1/security/sites/${id}`),
    onSuccess: () => { invalidate(); setShowDelete(null); if (expanded === showDelete?.id) setExpanded(null); setApiError("") },
    onError: (e: any) => setApiError(e.response?.data?.message ?? "Failed to delete site"),
  })

  const generatePortalToken = useMutation({
    mutationFn: ({ id, label }: { id: string; label: string }) =>
      apiClient.post(`/api/v1/security/sites/${id}/portal/generate`, { label }),
    onSuccess: (res) => {
      const token = res.data?.data?.token ?? res.data?.token
      setPortalToken(token)
      invalidate()
    },
    onError: (e: any) => setApiError(e.response?.data?.message ?? "Failed to generate portal link"),
  })

  const disablePortal = useMutation({
    mutationFn: (id: string) => apiClient.delete(`/api/v1/security/sites/${id}/portal`),
    onSuccess: () => { invalidate(); setShowPortal(null); setPortalToken(null) },
    onError: (e: any) => setApiError(e.response?.data?.message ?? "Failed to disable portal"),
  })

  const printCheckpointQr = useMutation({
    mutationFn: async ({ siteId, checkpointId }: { siteId: string; checkpointId: string }) => {
      setPrintingCpId(checkpointId)
      await openPdfInNewTab(`/api/v1/security/sites/${siteId}/checkpoints/${checkpointId}/qr-pdf`)
    },
    onSettled: () => setPrintingCpId(null),
    onError: () => setApiError("Failed to generate QR PDF"),
  })

  const printSiteQrSheet = useMutation({
    mutationFn: async (siteId: string) => {
      setPrintingSiteId(siteId)
      await openPdfInNewTab(`/api/v1/security/sites/${siteId}/checkpoints/qr-sheet`)
    },
    onSettled: () => setPrintingSiteId(null),
    onError: () => setApiError("Failed to generate QR sheet PDF"),
  })

  const regenerateQr = useMutation({
    mutationFn: async ({ siteId, checkpointId }: { siteId: string; checkpointId: string }) => {
      setRegeneratingCpId(checkpointId)
      await apiClient.post(`/api/v1/security/sites/${siteId}/checkpoints/${checkpointId}/qr-secret/regenerate`)
    },
    onSuccess: (_data, { siteId, checkpointId }) => {
      invalidate()
      qc.invalidateQueries({ queryKey: ["checkpoint-qr-image", checkpointId] })
      // Immediately open the freshly-regenerated QR for reprinting -- the
      // old physical sticker is already invalid the moment this succeeds.
      openPdfInNewTab(`/api/v1/security/sites/${siteId}/checkpoints/${checkpointId}/qr-pdf`)
    },
    onSettled: () => setRegeneratingCpId(null),
    onError: () => setApiError("Failed to regenerate QR code"),
  })

  const setSiteBranch = useMutation({
    mutationFn: ({ siteId, branchId }: { siteId: string; branchId: string | null }) =>
      apiClient.patch(`/api/v1/security/sites/${siteId}/branch`, { branchId }),
    onSuccess: () => invalidate(),
    onError: () => setApiError("Failed to update branch assignment"),
  })

  const setQrEnforcement = useMutation({
    mutationFn: ({ siteId, requireSignedQr }: { siteId: string; requireSignedQr: boolean }) =>
      apiClient.patch(`/api/v1/security/sites/${siteId}/qr-enforcement`, { requireSignedQr }),
    onSuccess: () => invalidate(),
    onError: () => setApiError("Failed to update QR enforcement"),
  })

  // ── Validation ─────────────────────────────────────────────────────────────

  const validateSite = () => {
    const errs: Record<string, string> = {}
    if (!siteForm.name.trim()) errs.name = "Site name is required"
    if (siteForm.latitude && isNaN(parseFloat(siteForm.latitude))) errs.latitude = "Invalid latitude"
    if (siteForm.longitude && isNaN(parseFloat(siteForm.longitude))) errs.longitude = "Invalid longitude"
    setSiteErrors(errs)
    return Object.keys(errs).length === 0
  }

  const handleCreateSite = () => {
    if (!validateSite()) return
    createSite.mutate({
      name:         siteForm.name.trim(),
      contactName:  siteForm.contactName || null,
      contactPhone: siteForm.contactPhone || null,
      instructions: siteForm.instructions || null,
      latitude:     siteForm.latitude ? parseFloat(siteForm.latitude) : null,
      longitude:    siteForm.longitude ? parseFloat(siteForm.longitude) : null,
    })
  }

  const handleRegenerateQr = (siteId: string, checkpoint: Checkpoint) => {
    setRegenerateConfirm({ siteId, checkpoint })
  }

  const confirmRegenerateQr = () => {
    if (!regenerateConfirm) return
    regenerateQr.mutate({ siteId: regenerateConfirm.siteId, checkpointId: regenerateConfirm.checkpoint.id })
    setRegenerateConfirm(null)
  }

  // ── Render ─────────────────────────────────────────────────────────────────

  const activeSites      = sites.filter(s => s.contractStatus !== "TERMINATED" && s.active)
  const terminatedSites  = sites.filter(s => s.contractStatus === "TERMINATED" || !s.active)

  return (
    <div>
      {/* Stats */}
      <div style={{ display: "flex", gap: 12, marginBottom: 20 }}>
        {[
          { label: "Total sites",    value: sites.length,          color: "#1B3A6B" },
          { label: "Active",         value: activeSites.length,    color: "#166534" },
          { label: "Terminated",     value: terminatedSites.length, color: "#64748B" },
          {
            label: "Total checkpoints",
            value: sites.reduce((sum, s) => sum + (s.checkpoints?.length ?? 0), 0),
            color: "#0D9488"
          },
        ].map(s => (
          <div key={s.label} style={{ flex: 1, background: "#F8FAFC", border: "1px solid #E2E8F0", borderRadius: 10, padding: "12px 16px" }}>
            <div style={{ fontSize: 22, fontWeight: 700, color: s.color }}>{s.value}</div>
            <div style={{ fontSize: 12, color: "#64748B", marginTop: 2 }}>{s.label}</div>
          </div>
        ))}
      </div>

      {/* Toolbar */}
      <div style={{ display: "flex", justifyContent: "flex-end", marginBottom: 18 }}>
        <button onClick={() => { setShowAddSite(true); setSiteForm(EMPTY_SITE_FORM); setSiteErrors({}); setApiError("") }}
          style={{ display: "flex", alignItems: "center", gap: 7, background: "#1B3A6B", color: "#fff", border: "none", borderRadius: 8, padding: "9px 18px", fontSize: 14, fontWeight: 600, cursor: "pointer" }}>
          <Plus size={15} /> Add Site
        </button>
      </div>

      {isLoading ? (
        <div style={{ textAlign: "center", padding: 40, color: "#94A3B8" }}>Loading sites...</div>
      ) : sites.length === 0 ? (
        <div style={{ textAlign: "center", padding: "60px 20px", color: "#94A3B8" }}>
          <MapPin size={36} color="#CBD5E1" style={{ marginBottom: 12 }} />
          <div style={{ fontWeight: 600, color: "#475569" }}>No sites registered</div>
          <div style={{ fontSize: 13, marginTop: 4 }}>Add your first client site to get started.</div>
        </div>
      ) : (
        <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
          {sites.map(site => {
            const contract = CONTRACT_CONFIG[site.contractStatus] ?? CONTRACT_CONFIG.ACTIVE
            const isExpanded = expanded === site.id
            const detail = isExpanded ? siteDetail : null

            return (
              <div key={site.id} style={{ border: "1px solid #E2E8F0", borderRadius: 12, overflow: "hidden", background: "#fff" }}>
                {/* Site header row */}
                <div style={{ display: "flex", alignItems: "center", padding: "16px 20px", gap: 14, cursor: "pointer" }}
                  onClick={() => setExpanded(isExpanded ? null : site.id)}>
                  <div style={{ width: 40, height: 40, borderRadius: 10, background: "#EFF6FF", display: "flex", alignItems: "center", justifyContent: "center", flexShrink: 0 }}>
                    <MapPin size={18} color="#1B3A6B" />
                  </div>
                  <div style={{ flex: 1 }}>
                    <div style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 3 }}>
                      <span style={{ fontWeight: 700, fontSize: 15, color: "#0F172A" }}>{site.name}</span>
                      <span style={{ fontSize: 11, fontWeight: 600, background: contract.bg, color: contract.color, padding: "2px 8px", borderRadius: 20 }}>
                        {contract.label}
                      </span>
                    </div>
                    <div style={{ fontSize: 12, color: "#94A3B8" }}>
                      {site.contactName && `${site.contactName} · `}
                      {site.contactPhone && `${site.contactPhone} · `}
                      {site.checkpoints?.length ?? 0} checkpoint{(site.checkpoints?.length ?? 0) !== 1 ? "s" : ""}
                    </div>
                    {site.contractStatus === "TERMINATED" && site.terminationReason && (
                      <div style={{ fontSize: 11, color: "#DC2626", marginTop: 2 }}>
                        Terminated: {site.terminationReason}
                        {site.terminatedAt && ` · ${new Date(site.terminatedAt).toLocaleDateString("en-ZA")}`}
                      </div>
                    )}
                  </div>
                  <div style={{ display: "flex", alignItems: "center", gap: 8, flexShrink: 0 }}>
                    {site.contractStatus !== "TERMINATED" && site.active && (
                      <>
                        <button onClick={e => { e.stopPropagation(); setShowPortal(site); setPortalLabel(site.name); setPortalToken(null); setApiError("") }}
                          title="Client portal"
                          style={{ background: "#F0FDF4", border: "none", borderRadius: 6, padding: "6px 10px", cursor: "pointer", color: "#166534", fontSize: 12, fontWeight: 600 }}>
                          Portal
                        </button>
                        {(site.checkpoints?.length ?? 0) > 0 && (
                          <button onClick={e => { e.stopPropagation(); printSiteQrSheet.mutate(site.id) }}
                            disabled={printingSiteId === site.id}
                            title="Print all checkpoint QR codes for this site"
                            style={{ display: "flex", alignItems: "center", gap: 5, background: "#F5F3FF", border: "none", borderRadius: 6, padding: "6px 10px", cursor: "pointer", color: "#7C3AED", fontSize: 12, fontWeight: 600 }}>
                            <Printer size={12} /> {printingSiteId === site.id ? "Preparing…" : "Print All"}
                          </button>
                        )}
                        <button onClick={e => { e.stopPropagation(); setShowTerminate(site); setTerminateReason(""); setApiError("") }}
                          title="Terminate contract"
                          style={{ background: "#FEF3C7", border: "none", borderRadius: 6, padding: "6px 10px", cursor: "pointer", color: "#D97706", fontSize: 12, fontWeight: 600 }}>
                          Terminate
                        </button>
                        <button onClick={e => { e.stopPropagation(); setShowAddCp(site.id); setCpForm(EMPTY_CP_FORM); setApiError("") }}
                          title="Add checkpoint"
                          style={{ background: "#EFF6FF", border: "none", borderRadius: 6, padding: "6px 10px", cursor: "pointer", color: "#1D4ED8", fontSize: 12, fontWeight: 600 }}>
                          + Checkpoint
                        </button>
                      </>
                    )}
                    <button onClick={e => { e.stopPropagation(); setShowDelete(site); setApiError("") }}
                      title="Delete site"
                      style={{ background: "#FEF2F2", border: "none", borderRadius: 6, padding: "6px 8px", cursor: "pointer", color: "#DC2626", display: "flex" }}>
                      <Trash2 size={13} />
                    </button>
                    {isExpanded ? <ChevronDown size={16} color="#94A3B8" /> : <ChevronRight size={16} color="#94A3B8" />}
                  </div>
                </div>

                {/* Expanded — checkpoints */}
                {isExpanded && (
                  <div style={{ borderTop: "1px solid #F1F5F9", padding: "16px 20px", background: "#FAFAFA" }}>
                    {!detail ? (
                      <div style={{ color: "#94A3B8", fontSize: 13 }}>Loading checkpoints...</div>
                    ) : detail.checkpoints.length === 0 ? (
                      <div style={{ color: "#94A3B8", fontSize: 13, textAlign: "center", padding: "20px 0" }}>
                        No checkpoints yet. Click "+ Checkpoint" to add one.
                      </div>
                    ) : (
                      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(230px, 1fr))", gap: 12 }}>
                        {detail.checkpoints.map((cp, i) => (
                          <div key={cp.id} style={{ display: "flex", flexDirection: "column", gap: 10, padding: "14px 16px", background: "#fff", border: "1px solid #E2E8F0", borderRadius: 10 }}>
                            <div style={{ display: "flex", justifyContent: "center" }}>
                              <CheckpointQrThumbnail siteId={site.id} checkpointId={cp.id} />
                            </div>

                            <div style={{ display: "flex", alignItems: "flex-start", gap: 10 }}>
                              <div style={{ width: 24, height: 24, borderRadius: "50%", background: "#EFF6FF", display: "flex", alignItems: "center", justifyContent: "center", flexShrink: 0, fontSize: 11, fontWeight: 700, color: "#1D4ED8" }}>
                                {i + 1}
                              </div>
                              <div style={{ flex: 1, minWidth: 0 }}>
                                <div style={{ fontWeight: 600, fontSize: 14, color: "#0F172A" }}>{cp.name}</div>
                                {cp.description && <div style={{ fontSize: 12, color: "#94A3B8", marginTop: 2 }}>{cp.description}</div>}
                              </div>
                            </div>

                            <div style={{ display: "flex", gap: 8, marginTop: "auto", paddingTop: 2 }}>
                              <button onClick={() => printCheckpointQr.mutate({ siteId: site.id, checkpointId: cp.id })}
                                disabled={printingCpId === cp.id}
                                title="Print this checkpoint's QR code"
                                style={{ flex: 1, display: "flex", alignItems: "center", justifyContent: "center", gap: 5, background: "#EFF6FF", border: "none", borderRadius: 7, padding: "7px 10px", cursor: "pointer", color: "#1D4ED8", fontSize: 12, fontWeight: 600 }}>
                                <Printer size={12} /> {printingCpId === cp.id ? "…" : "Print"}
                              </button>
                              <button onClick={() => handleRegenerateQr(site.id, cp)}
                                disabled={regeneratingCpId === cp.id}
                                title="Regenerate this checkpoint's QR code (e.g. compromised sticker)"
                                style={{ flex: 1, display: "flex", alignItems: "center", justifyContent: "center", gap: 5, background: "#FEF2F2", border: "none", borderRadius: 7, padding: "7px 10px", cursor: "pointer", color: "#DC2626", fontSize: 12, fontWeight: 600 }}>
                                <RefreshCw size={12} /> {regeneratingCpId === cp.id ? "…" : "Regenerate"}
                              </button>
                            </div>
                          </div>
                        ))}
                      </div>
                    )}

                    {/* GPS coordinates if set */}
                    {(site.latitude || site.longitude) && (
                      <div style={{ marginTop: 12, padding: "8px 12px", background: "#F0FDF4", border: "1px solid #BBF7D0", borderRadius: 8, fontSize: 12, color: "#166534", display: "flex", gap: 8 }}>
                        <MapPin size={13} />
                        {site.latitude?.toFixed(6)}, {site.longitude?.toFixed(6)}
                      </div>
                    )}

                    {/* Site settings — branch assignment + QR enforcement */}
                    {detail && (
                      <div style={{ marginTop: 12, padding: "14px 16px", background: "#fff", border: "1px solid #E2E8F0", borderRadius: 10, display: "flex", flexWrap: "wrap" as const, gap: 20 }}>
                        <div style={{ flex: "1 1 220px" }}>
                          <label style={{ ...lbl, marginBottom: 6 }}>Branch</label>
                          <select
                            value={detail.branchId ?? ""}
                            onChange={e => setSiteBranch.mutate({ siteId: site.id, branchId: e.target.value || null })}
                            disabled={setSiteBranch.isPending}
                            style={{ ...inp, background: "#fff" }}>
                            <option value="">— No branch assigned —</option>
                            {branches.filter(b => b.active).map(b => (
                              <option key={b.id} value={b.id}>{b.name}{b.region ? ` (${b.region})` : ""}</option>
                            ))}
                          </select>
                          <div style={{ fontSize: 11, color: "#94A3B8", marginTop: 4 }}>
                            Note: branch assignment doesn't yet restrict who can see this site — visibility scoping isn't wired up.
                          </div>
                        </div>

                        <div style={{ flex: "1 1 220px" }}>
                          <label style={{ ...lbl, marginBottom: 6 }}>QR Signature Enforcement</label>
                          <label style={{ display: "flex", alignItems: "center", gap: 8, cursor: setQrEnforcement.isPending ? "wait" : "pointer" }}>
                            <input
                              type="checkbox"
                              checked={detail.requireSignedQr}
                              disabled={setQrEnforcement.isPending}
                              onChange={e => setQrEnforcement.mutate({ siteId: site.id, requireSignedQr: e.target.checked })}
                            />
                            <span style={{ fontSize: 13, color: "#374151" }}>
                              {detail.requireSignedQr ? "Enforced — unsigned QR scans are rejected" : "Not enforced — legacy unsigned codes still accepted"}
                            </span>
                          </label>
                          <div style={{ fontSize: 11, color: "#D97706", marginTop: 4 }}>
                            Only enable after reprinting every checkpoint's QR here (use "Print" or "Print All" above) — enabling first will break scanning at any checkpoint still using an old sticker.
                          </div>
                        </div>
                      </div>
                    )}

                  </div>
                )}
              </div>
            )
          })}
        </div>
      )}

      {/* ── Add Site Modal ─────────────────────────────────────────────────── */}
      {showAddSite && (
        <Modal title="Add New Site" onClose={() => { setShowAddSite(false); setSiteErrors({}); setApiError("") }} width={520}>
          <div style={{ display: "flex", flexDirection: "column", gap: 14 }}>
            <div>
              <label style={lbl}>Site Name *</label>
              <input value={siteForm.name} onChange={e => { setSiteForm(f => ({ ...f, name: e.target.value })); setSiteErrors(v => omit(v, "name")) }}
                placeholder="Sandton City Mall — Gate A" autoFocus style={inpSt(siteErrors.name)} />
              <FErr msg={siteErrors.name} />
            </div>
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
              <div>
                <label style={lbl}>Contact Name</label>
                <input value={siteForm.contactName} onChange={e => setSiteForm(f => ({ ...f, contactName: e.target.value }))}
                  placeholder="John Smith" style={inp} />
              </div>
              <div>
                <label style={lbl}>Contact Phone</label>
                <input value={siteForm.contactPhone} onChange={e => setSiteForm(f => ({ ...f, contactPhone: e.target.value }))}
                  placeholder="+27 11 555 0101" style={inp} />
              </div>
              <div>
                <label style={lbl}>Latitude <span style={{ fontWeight: 400, color: "#94A3B8" }}>(optional)</span></label>
                <input type="number" value={siteForm.latitude} onChange={e => { setSiteForm(f => ({ ...f, latitude: e.target.value })); setSiteErrors(v => omit(v, "latitude")) }}
                  placeholder="-26.1076" step="0.000001" style={inpSt(siteErrors.latitude)} />
                <FErr msg={siteErrors.latitude} />
              </div>
              <div>
                <label style={lbl}>Longitude</label>
                <input type="number" value={siteForm.longitude} onChange={e => { setSiteForm(f => ({ ...f, longitude: e.target.value })); setSiteErrors(v => omit(v, "longitude")) }}
                  placeholder="28.0567" step="0.000001" style={inpSt(siteErrors.longitude)} />
                <FErr msg={siteErrors.longitude} />
              </div>
            </div>
            <div>
              <label style={lbl}>Site Instructions <span style={{ fontWeight: 400, color: "#94A3B8" }}>(optional)</span></label>
              <textarea value={siteForm.instructions} onChange={e => setSiteForm(f => ({ ...f, instructions: e.target.value }))}
                rows={2} placeholder="Access code for main gate: 1234. Report to front desk on arrival."
                style={{ ...inp, resize: "vertical" as const }} />
            </div>
          </div>

          <div style={{ marginTop: 14, padding: "10px 14px", background: "#EFF6FF", border: "1px solid #BFDBFE", borderRadius: 8, fontSize: 12, color: "#1D4ED8", display: "flex", gap: 8 }}>
            <Radio size={13} style={{ marginTop: 1, flexShrink: 0 }} />
            Checkpoints (QR, NFC, BLE) are added after the site is created. QR codes are auto-generated.
          </div>

          {apiError && <ErrBanner msg={apiError} />}
          <Footer onCancel={() => { setShowAddSite(false); setSiteErrors({}); setApiError("") }}
            onSubmit={handleCreateSite} loading={createSite.isPending} label="Create Site" />
        </Modal>
      )}

      {/* ── Add Checkpoint Modal ───────────────────────────────────────────── */}
      {showAddCp && (
        <Modal title="Add Checkpoint" onClose={() => { setShowAddCp(null); setApiError("") }} width={460}>
          <div style={{ display: "flex", flexDirection: "column", gap: 14 }}>
            <div>
              <label style={lbl}>Checkpoint Name *</label>
              <input value={cpForm.name} onChange={e => setCpForm(f => ({ ...f, name: e.target.value }))}
                placeholder="North Gate / Server Room / Parking Level 2" autoFocus style={inp} />
            </div>
            <div>
              <label style={lbl}>Description <span style={{ fontWeight: 400, color: "#94A3B8" }}>(optional)</span></label>
              <input value={cpForm.description} onChange={e => setCpForm(f => ({ ...f, description: e.target.value }))}
                placeholder="Check that gate is locked and alarm is armed" style={inp} />
            </div>
          </div>

          <div style={{ marginTop: 14, padding: "10px 14px", background: "#F0FDF4", border: "1px solid #86EFAC", borderRadius: 8, fontSize: 12, color: "#166534", display: "flex", gap: 8 }}>
            <QrCode size={13} style={{ marginTop: 1, flexShrink: 0 }} />
            A unique QR code is generated automatically. Use "Print" on the checkpoint row after
            saving to get a scannable, printable PDF. NFC tag and BLE beacon can be added later
            via the Guard App.
          </div>

          {apiError && <ErrBanner msg={apiError} />}
          <Footer onCancel={() => { setShowAddCp(null); setApiError("") }}
            onSubmit={() => {
              if (!cpForm.name.trim()) { setApiError("Checkpoint name is required"); return }
              addCheckpoint.mutate({ siteId: showAddCp, body: { name: cpForm.name.trim(), description: cpForm.description || null } })
            }}
            loading={addCheckpoint.isPending} label="Add Checkpoint" />
        </Modal>
      )}

      {/* ── Terminate Contract Modal ───────────────────────────────────────── */}
      {showTerminate && (
        <Modal title="Terminate Contract" onClose={() => { setShowTerminate(null); setApiError("") }} width={440}>
          <div style={{ padding: "12px 14px", background: "#FEF3C7", border: "1px solid #FCD34D", borderRadius: 9, marginBottom: 16, display: "flex", gap: 10 }}>
            <AlertTriangle size={16} color="#D97706" style={{ flexShrink: 0, marginTop: 1 }} />
            <div>
              <div style={{ fontWeight: 600, fontSize: 13, color: "#92400E" }}>Terminate contract for {showTerminate.name}?</div>
              <div style={{ fontSize: 12, color: "#78350F", marginTop: 2 }}>This deactivates the site and removes it from active shifts. Historical records are preserved.</div>
            </div>
          </div>
          <div>
            <label style={lbl}>Reason *</label>
            <textarea value={terminateReason} onChange={e => setTerminateReason(e.target.value)}
              rows={3} placeholder="e.g. Client contract ended 30 June 2026 — not renewed"
              style={{ ...inp, resize: "vertical" as const }} autoFocus />
          </div>
          {apiError && <ErrBanner msg={apiError} />}
          <div style={{ display: "flex", gap: 10, marginTop: 20 }}>
            <button onClick={() => { setShowTerminate(null); setApiError("") }} style={cancelBtn}>Cancel</button>
            <button
              onClick={() => {
                if (!terminateReason.trim()) { setApiError("A reason is required to terminate a contract"); return }
                terminateSite.mutate({ id: showTerminate.id, reason: terminateReason })
              }}
              disabled={terminateSite.isPending}
              style={{ flex: 1, padding: "10px", background: "#D97706", color: "#fff", border: "none", borderRadius: 9, fontSize: 14, fontWeight: 700, cursor: "pointer" }}>
              {terminateSite.isPending ? "Terminating..." : "Terminate Contract"}
            </button>
          </div>
        </Modal>
      )}

      {/* ── Client Portal Modal ──────────────────────────────────────────────── */}
      {showPortal && (
        <Modal title="Client Portal" onClose={() => { setShowPortal(null); setPortalToken(null); setApiError("") }} width={480}>
          <div style={{ padding: "12px 14px", background: "#EFF6FF", border: "1px solid #BFDBFE", borderRadius: 9, marginBottom: 18 }}>
            <div style={{ fontSize: 13, fontWeight: 600, color: "#1D4ED8", marginBottom: 4 }}>Share read-only access with your client</div>
            <div style={{ fontSize: 12, color: "#475569", lineHeight: 1.6 }}>
              The portal gives your client a real-time view of guards on-site, shifts, and open incidents — no HandyFlow login required. The URL is the only credential.
            </div>
          </div>

          {!portalToken ? (
            <>
              <div style={{ marginBottom: 14 }}>
                <label style={lbl}>Portal label <span style={{ fontWeight: 400, color: "#94A3B8" }}>(shown in portal header)</span></label>
                <input value={portalLabel} onChange={e => setPortalLabel(e.target.value)}
                  placeholder={showPortal.name}
                  style={{ width: "100%", padding: "9px 12px", border: "1.5px solid #E2E8F0", borderRadius: 8, fontSize: 14, boxSizing: "border-box" as const, outline: "none" }} />
              </div>
              {apiError && <ErrBanner msg={apiError} />}
              <div style={{ display: "flex", gap: 10, marginTop: 16 }}>
                <button onClick={() => { setShowPortal(null); setApiError("") }} style={cancelBtn}>Cancel</button>
                <button onClick={() => generatePortalToken.mutate({ id: showPortal.id, label: portalLabel || showPortal.name })}
                  disabled={generatePortalToken.isPending}
                  style={{ flex: 1, padding: "10px", background: "#166534", color: "#fff", border: "none", borderRadius: 9, fontSize: 14, fontWeight: 700, cursor: "pointer" }}>
                  {generatePortalToken.isPending ? "Generating..." : "Generate Portal Link"}
                </button>
              </div>
            </>
          ) : (
            <>
              <div style={{ marginBottom: 14 }}>
                <label style={lbl}>Portal URL — share this with your client</label>
                <div style={{ display: "flex", gap: 8 }}>
                  <input readOnly value={`${window.location.origin}/portal/${portalToken}`}
                    style={{ flex: 1, padding: "9px 12px", border: "1.5px solid #86EFAC", borderRadius: 8, fontSize: 13, background: "#F0FDF4", color: "#166534", fontFamily: "monospace", outline: "none" }} />
                  <button onClick={() => navigator.clipboard.writeText(`${window.location.origin}/portal/${portalToken}`)}
                    style={{ padding: "9px 14px", background: "#F0FDF4", border: "1px solid #86EFAC", borderRadius: 8, cursor: "pointer", fontSize: 12, color: "#166534", fontWeight: 600 }}>
                    Copy
                  </button>
                </div>
                <div style={{ fontSize: 11, color: "#94A3B8", marginTop: 5 }}>Anyone with this link can view the portal. To revoke access, click "Disable Portal".</div>
              </div>
              <div style={{ display: "flex", gap: 10, marginTop: 16 }}>
                <button onClick={() => { setShowPortal(null); setPortalToken(null) }} style={{ flex: 1, padding: "10px", background: "#1B3A6B", color: "#fff", border: "none", borderRadius: 9, fontSize: 14, fontWeight: 700, cursor: "pointer" }}>Done</button>
                <button onClick={() => disablePortal.mutate(showPortal.id)} disabled={disablePortal.isPending}
                  style={{ padding: "10px 16px", background: "#FEF2F2", color: "#DC2626", border: "1px solid #FECACA", borderRadius: 9, fontSize: 13, cursor: "pointer", fontWeight: 600 }}>
                  Disable Portal
                </button>
              </div>
            </>
          )}
        </Modal>
      )}

      {/* ── Delete Confirmation Modal ──────────────────────────────────────── */}
      {showDelete && (
        <Modal title="" onClose={() => { setShowDelete(null); setApiError("") }} width={400}>
          <div style={{ textAlign: "center" }}>
            <div style={{ width: 56, height: 56, borderRadius: "50%", background: "#FEF2F2", border: "2px solid #FECACA", display: "flex", alignItems: "center", justifyContent: "center", margin: "0 auto 16px" }}>
              <Trash2 size={22} color="#DC2626" />
            </div>
            <h3 style={{ margin: "0 0 8px", fontSize: 17, fontWeight: 700, color: "#0F172A" }}>Delete Site?</h3>
            <div style={{ fontSize: 14, fontWeight: 600, color: "#0F172A", marginBottom: 8 }}>{showDelete.name}</div>
            <p style={{ fontSize: 13, color: "#64748B", margin: "0 0 20px", lineHeight: 1.6 }}>
              The site record will be deactivated. All shift history, incident records, and checkpoint scan logs are preserved.
            </p>
            {apiError && <ErrBanner msg={apiError} />}
            <div style={{ display: "flex", gap: 10 }}>
              <button onClick={() => { setShowDelete(null); setApiError("") }} style={{ flex: 1, padding: "10px", border: "1.5px solid #E2E8F0", borderRadius: 9, background: "#fff", fontSize: 14, fontWeight: 600, cursor: "pointer", color: "#374151" }}>Keep Site</button>
              <button onClick={() => deleteSite.mutate(showDelete.id)} disabled={deleteSite.isPending}
                style={{ flex: 1, padding: "10px", border: "none", borderRadius: 9, background: "#DC2626", color: "#fff", fontSize: 14, fontWeight: 700, cursor: "pointer" }}>
                {deleteSite.isPending ? "Deleting..." : "Yes, Delete"}
              </button>
            </div>
          </div>
        </Modal>
      )}

      {/* ── Regenerate QR Confirmation Modal ──────────────────────────────── */}
      {regenerateConfirm && (
        <Modal title="" onClose={() => setRegenerateConfirm(null)} width={420}>
          <div style={{ textAlign: "center" }}>
            <div style={{ width: 56, height: 56, borderRadius: "50%", background: "#FEF2F2", border: "2px solid #FECACA", display: "flex", alignItems: "center", justifyContent: "center", margin: "0 auto 16px" }}>
              <RefreshCw size={22} color="#DC2626" />
            </div>
            <h3 style={{ margin: "0 0 8px", fontSize: 17, fontWeight: 700, color: "#0F172A" }}>Regenerate QR Code?</h3>
            <div style={{ fontSize: 14, fontWeight: 600, color: "#0F172A", marginBottom: 12 }}>{regenerateConfirm.checkpoint.name}</div>
            <div style={{ padding: "12px 14px", background: "#FEF3C7", border: "1px solid #FCD34D", borderRadius: 9, marginBottom: 20, display: "flex", gap: 10, textAlign: "left" as const }}>
              <AlertTriangle size={16} color="#D97706" style={{ flexShrink: 0, marginTop: 1 }} />
              <div style={{ fontSize: 12.5, color: "#78350F", lineHeight: 1.6 }}>
                The physical sticker currently mounted at this checkpoint will stop working immediately.
                A fresh QR PDF will open automatically for reprinting.
              </div>
            </div>
            {apiError && <ErrBanner msg={apiError} />}
            <div style={{ display: "flex", gap: 10 }}>
              <button onClick={() => setRegenerateConfirm(null)} style={{ flex: 1, padding: "10px", border: "1.5px solid #E2E8F0", borderRadius: 9, background: "#fff", fontSize: 14, fontWeight: 600, cursor: "pointer", color: "#374151" }}>Cancel</button>
              <button onClick={confirmRegenerateQr} disabled={regenerateQr.isPending}
                style={{ flex: 1, padding: "10px", border: "none", borderRadius: 9, background: "#DC2626", color: "#fff", fontSize: 14, fontWeight: 700, cursor: "pointer" }}>
                {regenerateQr.isPending ? "Regenerating..." : "Yes, Regenerate"}
              </button>
            </div>
          </div>
        </Modal>
      )}
    </div>
  )
}

// ── Shared sub-components ──────────────────────────────────────────────────────

function Modal({ title, onClose, children, width = 520 }: {
  title: string; onClose: () => void; children: React.ReactNode; width?: number
}) {
  return (
    <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000, backdropFilter: "blur(2px)" }}>
      <div style={{ background: "#fff", borderRadius: 16, padding: 28, width, maxHeight: "92vh", overflowY: "auto", boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }}>
        {title && (
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 22 }}>
            <h3 style={{ margin: 0, fontSize: 17, fontWeight: 700, color: "#0F172A" }}>{title}</h3>
            <button onClick={onClose} style={{ background: "none", border: "none", cursor: "pointer", color: "#94A3B8", display: "flex" }}><X size={20} /></button>
          </div>
        )}
        {children}
      </div>
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

function Footer({ onCancel, onSubmit, loading, label }: {
  onCancel: () => void; onSubmit: () => void; loading: boolean; label: string
}) {
  return (
    <div style={{ display: "flex", gap: 10, justifyContent: "flex-end", marginTop: 20 }}>
      <button onClick={onCancel} style={cancelBtn}>Cancel</button>
      <button onClick={onSubmit} disabled={loading}
        style={{ padding: "9px 22px", background: loading ? "#94A3B8" : "#1B3A6B", color: "#fff", border: "none", borderRadius: 9, fontSize: 14, fontWeight: 700, cursor: loading ? "not-allowed" : "pointer" }}>
        {loading ? "Saving..." : label}
      </button>
    </div>
  )
}

function FErr({ msg }: { msg?: string }) {
  if (!msg) return null
  return (
    <div style={{ display: "flex", alignItems: "center", gap: 4, fontSize: 12, color: "#DC2626", marginTop: 4 }}>
      <AlertCircle size={12} />{msg}
    </div>
  )
}

const omit = (obj: Record<string, string>, key: string) => { const n = { ...obj }; delete n[key]; return n }
const lbl: React.CSSProperties = { display: "block", fontSize: 13, fontWeight: 600, color: "#374151", marginBottom: 5 }
const inp: React.CSSProperties = { width: "100%", padding: "9px 12px", border: "1.5px solid #E2E8F0", borderRadius: 8, fontSize: 14, boxSizing: "border-box" as const, background: "#fff", outline: "none" }
const inpSt = (err?: string): React.CSSProperties => ({ ...inp, border: `1.5px solid ${err ? "#DC2626" : "#E2E8F0"}`, background: err ? "#FFF5F5" : "#fff" })
const cancelBtn: React.CSSProperties = { padding: "9px 18px", border: "1px solid #E2E8F0", borderRadius: 9, background: "#fff", fontSize: 14, cursor: "pointer", color: "#374151" }

// src/pages/security/GuardsTab.tsx
// Changes from original:
//   - Guard interface: add psiraExpiryDate, statusChangedAt, statusNote
//   - Photo: show avatar fallback when photoUrl is null OR "PENDING_UPLOAD"
//   - PSiRA expiry: compliance badge (red if expired, amber if <30 days)
//   - Guard detail view: show statusChangedAt + statusNote
//   - Status filter: already correct
//   - PATCH /guards/{id}/status: already wired correctly

import { useState, useRef, useEffect } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import {
  Plus, Search, Shield, Phone, BadgeCheck, Trash2, X,
  Edit2, Eye, AlertCircle, Fingerprint, Upload,
  CheckCircle, AlertTriangle, Clock, Ban, HelpCircle, Calendar,
} from "lucide-react"

// ── Types ──────────────────────────────────────────────────────────────────────

interface Guard {
  id: string; firstName: string; lastName: string; fullName: string
  psiraNumber: string | null; idNumber: string | null; phone: string | null
  grade: string; active: boolean
  status: string; statusNote: string | null; statusChangedAt: string | null
  psiraExpiryDate: string | null
  notes: string | null; photoUrl: string | null; createdAt: string
}

// ── Constants ──────────────────────────────────────────────────────────────────

const GRADE_COLORS: Record<string, string> = {
  A: "#7C3AED", B: "#1D4ED8", C: "#0D9488", D: "#D97706", E: "#DC2626",
}

const GUARD_STATUSES = [
  { value: "ACTIVE",              label: "Active",               color: "#166534", bg: "#DCFCE7", icon: CheckCircle,   description: "Available for shift assignment" },
  { value: "ON_LEAVE",            label: "On Leave",             color: "#1D4ED8", bg: "#EFF6FF", icon: Clock,         description: "On approved leave — unavailable" },
  { value: "SUSPENDED",           label: "Suspended",            color: "#DC2626", bg: "#FEF2F2", icon: Ban,           description: "Suspended — cannot be assigned" },
  { value: "UNDER_INVESTIGATION", label: "Under Investigation",  color: "#D97706", bg: "#FEF3C7", icon: HelpCircle,    description: "Under investigation — restricted" },
  { value: "TERMINATED",          label: "Terminated",           color: "#64748B", bg: "#F1F5F9", icon: AlertTriangle, description: "Employment terminated" },
]

const STATUS_MAP = Object.fromEntries(GUARD_STATUSES.map(s => [s.value, s]))
const EMPTY_FORM = { firstName: "", lastName: "", psiraNumber: "", idNumber: "", phone: "", grade: "C", notes: "" }

// ── SA ID Validator ────────────────────────────────────────────────────────────

function validateSaId(id: string): { valid: boolean; dob?: string; gender?: string; error?: string } {
  const clean = id.replace(/\s/g, "")
  if (!clean) return { valid: true }
  if (!/^\d{13}$/.test(clean)) return { valid: false, error: "SA ID must be exactly 13 digits" }
  let sum = 0
  for (let i = 0; i < 12; i++) {
    let d = parseInt(clean[i])
    if (i % 2 === 1) { d *= 2; if (d > 9) d -= 9 }
    sum += d
  }
  if ((10 - (sum % 10)) % 10 !== parseInt(clean[12]))
    return { valid: false, error: "SA ID number is invalid (checksum failed)" }
  const yy = parseInt(clean.slice(0, 2)), mm = parseInt(clean.slice(2, 4)), dd = parseInt(clean.slice(4, 6))
  const currentYY = new Date().getFullYear() % 100
  const year = yy <= currentYY ? 2000 + yy : 1900 + yy
  if (mm < 1 || mm > 12 || dd < 1 || dd > 31) return { valid: false, error: "SA ID contains invalid date of birth" }
  const months = ["Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec"]
  return { valid: true, dob: `${String(dd).padStart(2,"0")} ${months[mm-1]} ${year}`, gender: parseInt(clean[6]) >= 5 ? "Male" : "Female" }
}

// ── PSiRA expiry helper ────────────────────────────────────────────────────────

function psiraExpiryStatus(dateStr: string | null): { label: string; color: string; bg: string } | null {
  if (!dateStr) return null
  const expiry = new Date(dateStr)
  const now    = new Date()
  const daysLeft = Math.ceil((expiry.getTime() - now.getTime()) / 86400000)
  if (daysLeft < 0)  return { label: "PSiRA Expired",              color: "#DC2626", bg: "#FEF2F2" }
  if (daysLeft <= 30) return { label: `PSiRA expires in ${daysLeft}d`, color: "#D97706", bg: "#FEF3C7" }
  return null // valid — no badge needed, clutter-free when compliant
}

// ── Photo component ────────────────────────────────────────────────────────────

function GuardAvatar({ guard, size = 36 }: { guard: Guard; size?: number }) {
  // WHY check for "PENDING_UPLOAD"?
  // Dev-mode photo capture stores a placeholder instead of base64.
  // The frontend must treat it the same as null and show the initials avatar.
  const hasPhoto = guard.photoUrl && guard.photoUrl !== "PENDING_UPLOAD"
  const unavail  = (guard.status ?? "ACTIVE") !== "ACTIVE"
  return hasPhoto ? (
    <img src={guard.photoUrl!} alt={guard.fullName}
      style={{ width: size, height: size, borderRadius: "50%", objectFit: "cover",
               border: "2px solid #BFDBFE", flexShrink: 0, opacity: unavail ? 0.6 : 1 }} />
  ) : (
    <div style={{ width: size, height: size, borderRadius: "50%",
                  background: unavail ? "#F1F5F9" : "#EFF6FF",
                  border: `2px solid ${unavail ? "#E2E8F0" : "#BFDBFE"}`,
                  display: "flex", alignItems: "center", justifyContent: "center",
                  fontWeight: 700, fontSize: size * 0.33,
                  color: unavail ? "#94A3B8" : "#1D4ED8", flexShrink: 0 }}>
      {guard.firstName?.[0]}{guard.lastName?.[0]}
    </div>
  )
}

// ── Main component ─────────────────────────────────────────────────────────────

export default function GuardsTab() {
  const qc = useQueryClient()
  const videoRef  = useRef<HTMLVideoElement>(null)
  const canvasRef = useRef<HTMLCanvasElement>(null)

  const [search,          setSearch]          = useState("")
  const [showAdd,         setShowAdd]         = useState(false)
  const [editing,         setEditing]         = useState<Guard | null>(null)
  const [viewing,         setViewing]         = useState<Guard | null>(null)
  const [deleting,        setDeleting]        = useState<Guard | null>(null)
  const [changingStatus,  setChangingStatus]  = useState<Guard | null>(null)
  const [form,            setForm]            = useState(EMPTY_FORM)
  const [fieldErrors,     setFieldErrors]     = useState<Record<string, string>>({})
  const [apiError,        setApiError]        = useState("")
  const [photoMode,       setPhotoMode]       = useState<"none" | "camera">("none")
  const [capturedPhoto,   setCapturedPhoto]   = useState<string | null>(null)
  const [cameraStream,    setCameraStream]    = useState<MediaStream | null>(null)
  const [fpStatus,        setFpStatus]        = useState<"idle" | "scanning" | "done">("idle")
  const [statusFilter,    setStatusFilter]    = useState("ALL")
  const [newStatus,       setNewStatus]       = useState("")
  const [statusNote,      setStatusNote]      = useState("")

  // ── Queries & mutations ────────────────────────────────────────────────────

  const { data, isLoading } = useQuery({
    queryKey: ["guards", search],
    queryFn: async () => {
      const params = search ? `?search=${encodeURIComponent(search)}&size=100` : "?size=100"
      const res = await apiClient.get(`/api/v1/security/guards${params}`)
      const payload = res.data?.data ?? res.data
      return (payload?.content ?? payload) as Guard[]
    },
  })
  const guards = data ?? []

  const createGuard = useMutation({
    mutationFn: (body: any) => apiClient.post("/api/v1/security/guards", body),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["guards"] }); closeAdd() },
    onError:   (e: any) => {
      const d = e.response?.data
      if (d?.errors && typeof d.errors === "object") setFieldErrors(d.errors)
      else setApiError(d?.message ?? "Failed to create guard")
    },
  })

  const updateGuard = useMutation({
    mutationFn: ({ id, body }: { id: string; body: any }) => apiClient.put(`/api/v1/security/guards/${id}`, body),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["guards"] }); closeEdit() },
    onError:   (e: any) => setApiError(e.response?.data?.message ?? "Failed to update guard"),
  })

  const updateStatus = useMutation({
    mutationFn: ({ id, status, note }: { id: string; status: string; note: string }) =>
      apiClient.patch(`/api/v1/security/guards/${id}/status`, { status, note }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["guards"] })
      setChangingStatus(null); setNewStatus(""); setStatusNote("")
    },
    onError: (e: any) => setApiError(e.response?.data?.message ?? "Failed to update status"),
  })

  const deleteGuard = useMutation({
    mutationFn: (id: string) => apiClient.delete(`/api/v1/security/guards/${id}`),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["guards"] }); setDeleting(null) },
    onError:   (e: any) => setApiError(e.response?.data?.message ?? "Failed to remove guard"),
  })

  // ── Helpers ────────────────────────────────────────────────────────────────

  const closeAdd  = () => { setShowAdd(false);  setForm(EMPTY_FORM); setFieldErrors({}); setApiError(""); stopCamera(); setCapturedPhoto(null); setFpStatus("idle") }
  const closeEdit = () => { setEditing(null);   setForm(EMPTY_FORM); setFieldErrors({}); setApiError(""); stopCamera(); setCapturedPhoto(null); setFpStatus("idle") }

  const openEdit = (g: Guard) => {
    setEditing(g)
    setForm({ firstName: g.firstName, lastName: g.lastName,
              psiraNumber: g.psiraNumber ?? "", idNumber: g.idNumber ?? "",
              phone: g.phone ?? "", grade: g.grade, notes: g.notes ?? "" })
    // Don't pre-fill PENDING_UPLOAD — show the avatar
    setCapturedPhoto(g.photoUrl && g.photoUrl !== "PENDING_UPLOAD" ? g.photoUrl : null)
    setFieldErrors({}); setApiError("")
  }

  const handleSubmit = (isEdit: boolean) => {
    const errs: Record<string, string> = {}
    if (!form.firstName.trim()) errs.firstName = "First name is required"
    if (!form.lastName.trim())  errs.lastName  = "Last name is required"
    if (form.phone && !/^(\+|0)[\d\s\-]{7,}$/.test(form.phone)) errs.phone = "Phone must start with + or 0"
    if (form.idNumber) { const r = validateSaId(form.idNumber); if (!r.valid) errs.idNumber = r.error! }
    setFieldErrors(errs)
    if (Object.keys(errs).length > 0) return
    const body = { ...form, photoUrl: capturedPhoto ?? undefined }
    if (isEdit && editing) updateGuard.mutate({ id: editing.id, body })
    else createGuard.mutate(body)
  }

  const startCamera = async () => {
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: "user" } })
      setCameraStream(stream); setPhotoMode("camera")
      setTimeout(() => { if (videoRef.current) { videoRef.current.srcObject = stream; videoRef.current.play() } }, 100)
    } catch { setApiError("Camera access denied") }
  }

  const capturePhoto = () => {
    if (!videoRef.current || !canvasRef.current) return
    const c = canvasRef.current
    c.width = videoRef.current.videoWidth; c.height = videoRef.current.videoHeight
    c.getContext("2d")?.drawImage(videoRef.current, 0, 0)
    setCapturedPhoto(c.toDataURL("image/jpeg", 0.8)); stopCamera()
  }

  const stopCamera = () => { cameraStream?.getTracks().forEach(t => t.stop()); setCameraStream(null); setPhotoMode("none") }

  const handleFileUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]; if (!file) return
    const reader = new FileReader()
    reader.onload = ev => setCapturedPhoto(ev.target?.result as string)
    reader.readAsDataURL(file)
  }

  const filtered = statusFilter === "ALL" ? guards : guards.filter(g => (g.status ?? "ACTIVE") === statusFilter)

  const fmtDate = (iso: string | null) => iso ? new Date(iso).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric" }) : null

  const inpSt = (key: string): React.CSSProperties => ({
    width: "100%", padding: "9px 12px", border: `1.5px solid ${fieldErrors[key] ? "#DC2626" : "#E2E8F0"}`,
    borderRadius: 8, fontSize: 14, boxSizing: "border-box" as const,
    background: fieldErrors[key] ? "#FFF5F5" : "#fff", outline: "none",
  })

  const FErr = ({ name }: { name: string }) => fieldErrors[name] ? (
    <div style={{ display: "flex", alignItems: "center", gap: 4, fontSize: 12, color: "#DC2626", marginTop: 4 }}>
      <AlertCircle size={12} />{fieldErrors[name]}
    </div>
  ) : null

  const StatusBadge = ({ status }: { status?: string }) => {
    const s = STATUS_MAP[status ?? "ACTIVE"] ?? STATUS_MAP.ACTIVE
    const Icon = s.icon
    return <span style={{ display: "inline-flex", alignItems: "center", gap: 4, background: s.bg, color: s.color, padding: "3px 10px", borderRadius: 20, fontSize: 11, fontWeight: 600, whiteSpace: "nowrap" as const }}><Icon size={10} />{s.label}</span>
  }

  const idFeedback = validateSaId(form.idNumber)

  // ── Guard Form (shared for add + edit) ────────────────────────────────────

  const GuardForm = () => (
    <>
      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14 }}>
        <div>
          <label style={lbl}>First Name *</label>
          <input value={form.firstName} onChange={e => { setForm(f => ({ ...f, firstName: e.target.value })); setFieldErrors(f => omit(f, "firstName")) }} placeholder="James" style={inpSt("firstName")} autoFocus />
          <FErr name="firstName" />
        </div>
        <div>
          <label style={lbl}>Last Name *</label>
          <input value={form.lastName} onChange={e => { setForm(f => ({ ...f, lastName: e.target.value })); setFieldErrors(f => omit(f, "lastName")) }} placeholder="Dlamini" style={inpSt("lastName")} />
          <FErr name="lastName" />
        </div>
        <div>
          <label style={lbl}>PSiRA Number</label>
          <input value={form.psiraNumber} onChange={e => setForm(f => ({ ...f, psiraNumber: e.target.value }))} placeholder="PSR-2024-001" style={inpSt("psiraNumber")} />
        </div>
        <div>
          <label style={lbl}>SA ID Number</label>
          <input value={form.idNumber}
            onChange={e => { setForm(f => ({ ...f, idNumber: e.target.value.replace(/\D/g, "").slice(0, 13) })); setFieldErrors(f => omit(f, "idNumber")) }}
            placeholder="8501015026088" inputMode="numeric" style={inpSt("idNumber")} />
          <FErr name="idNumber" />
          {form.idNumber.length === 13 && (
            idFeedback.valid ? (
              <div style={{ marginTop: 6, padding: "8px 10px", background: "#F0FDF4", border: "1px solid #86EFAC", borderRadius: 7, fontSize: 12, color: "#166534", display: "flex", gap: 14 }}>
                <span>✓ Valid</span>
                {idFeedback.dob && <span>DOB: {idFeedback.dob}</span>}
                {idFeedback.gender && <span>{idFeedback.gender}</span>}
              </div>
            ) : (
              <div style={{ marginTop: 6, padding: "8px 10px", background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 7, fontSize: 12, color: "#DC2626" }}>
                ✗ {idFeedback.error}
              </div>
            )
          )}
        </div>
        <div>
          <label style={lbl}>Phone</label>
          <input value={form.phone} onChange={e => { setForm(f => ({ ...f, phone: e.target.value })); setFieldErrors(f => omit(f, "phone")) }} placeholder="+27 82 555 0101" style={inpSt("phone")} />
          <FErr name="phone" />
        </div>
        <div>
          <label style={lbl}>PSiRA Grade</label>
          <select value={form.grade} onChange={e => setForm(f => ({ ...f, grade: e.target.value }))} style={{ ...inpSt("grade"), background: "#fff" }}>
            {["A","B","C","D","E"].map(g => <option key={g} value={g}>Grade {g}</option>)}
          </select>
        </div>
        <div style={{ gridColumn: "1 / -1" }}>
          <label style={lbl}>Notes</label>
          <textarea value={form.notes} onChange={e => setForm(f => ({ ...f, notes: e.target.value }))} rows={2} placeholder="Any relevant notes..." style={{ ...inpSt("notes"), resize: "vertical" as const }} />
        </div>
      </div>

      {/* Photo */}
      <div style={{ marginTop: 16, padding: 16, background: "#F8FAFC", borderRadius: 10, border: "1px solid #E2E8F0" }}>
        <div style={{ fontSize: 13, fontWeight: 700, color: "#374151", marginBottom: 12 }}>Guard Photo
          <span style={{ fontSize: 11, fontWeight: 400, color: "#94A3B8", marginLeft: 8 }}>(dev mode — stored as PENDING_UPLOAD until S3 is configured)</span>
        </div>
        {capturedPhoto && capturedPhoto !== "PENDING_UPLOAD" ? (
          <div style={{ display: "flex", alignItems: "center", gap: 14 }}>
            <img src={capturedPhoto} alt="Guard" style={{ width: 80, height: 80, borderRadius: 10, objectFit: "cover", border: "2px solid #0D9488" }} />
            <div>
              <div style={{ fontSize: 13, fontWeight: 600, color: "#166534", marginBottom: 6 }}>✓ Photo captured</div>
              <button onClick={() => setCapturedPhoto(null)} style={{ fontSize: 12, color: "#DC2626", background: "none", border: "none", cursor: "pointer", padding: 0 }}>Remove</button>
            </div>
          </div>
        ) : photoMode === "camera" ? (
          <div>
            <video ref={videoRef} style={{ width: "100%", borderRadius: 8, maxHeight: 200, objectFit: "cover" }} />
            <canvas ref={canvasRef} style={{ display: "none" }} />
            <div style={{ display: "flex", gap: 8, marginTop: 8 }}>
              <button onClick={capturePhoto} style={{ flex: 1, padding: "8px", background: "#0D9488", color: "#fff", border: "none", borderRadius: 7, fontSize: 13, fontWeight: 600, cursor: "pointer" }}>Capture</button>
              <button onClick={stopCamera} style={{ padding: "8px 14px", background: "#F1F5F9", border: "none", borderRadius: 7, fontSize: 13, cursor: "pointer", color: "#64748B" }}>Cancel</button>
            </div>
          </div>
        ) : (
          <div style={{ display: "flex", gap: 8 }}>
            <button onClick={startCamera} style={{ flex: 1, display: "flex", alignItems: "center", justifyContent: "center", gap: 6, padding: "9px", background: "#EFF6FF", color: "#1D4ED8", border: "1px solid #BFDBFE", borderRadius: 8, fontSize: 13, cursor: "pointer" }}>
              📷 Use Camera
            </button>
            <label style={{ flex: 1, display: "flex", alignItems: "center", justifyContent: "center", gap: 6, padding: "9px", background: "#F0FDF4", color: "#166534", border: "1px solid #86EFAC", borderRadius: 8, fontSize: 13, cursor: "pointer" }}>
              <Upload size={14} /> Upload Photo
              <input type="file" accept="image/*" onChange={handleFileUpload} style={{ display: "none" }} />
            </label>
          </div>
        )}
      </div>

      {/* Fingerprint stub */}
      <div style={{ marginTop: 12, padding: 16, background: "#F8FAFC", borderRadius: 10, border: "1px solid #E2E8F0" }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
          <div>
            <div style={{ fontSize: 13, fontWeight: 700, color: "#374151", marginBottom: 2 }}>Fingerprint</div>
            <div style={{ fontSize: 11, color: "#94A3B8" }}>Requires fingerprint scanner device</div>
          </div>
          <button onClick={() => { setFpStatus("scanning"); setTimeout(() => setFpStatus("done"), 2500) }} disabled={fpStatus === "scanning"}
            style={{ display: "flex", alignItems: "center", gap: 6, padding: "8px 14px", background: fpStatus === "done" ? "#DCFCE7" : "#F5F3FF", color: fpStatus === "done" ? "#166534" : "#7C3AED", border: `1px solid ${fpStatus === "done" ? "#86EFAC" : "#DDD6FE"}`, borderRadius: 8, fontSize: 13, cursor: "pointer", fontWeight: 600 }}>
            <Fingerprint size={13} />
            {fpStatus === "done" ? "Captured" : fpStatus === "scanning" ? "Scanning..." : "Scan Fingerprint"}
          </button>
        </div>
      </div>
    </>
  )

  // ── Render ─────────────────────────────────────────────────────────────────

  return (
    <div>
      {/* Toolbar */}
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 20 }}>
        <div style={{ position: "relative" }}>
          <Search size={15} style={{ position: "absolute", left: 12, top: "50%", transform: "translateY(-50%)", color: "#94A3B8" }} />
          <input value={search} onChange={e => setSearch(e.target.value)} placeholder="Search by name or PSiRA..."
            style={{ paddingLeft: 36, paddingRight: 14, paddingTop: 9, paddingBottom: 9, border: "1px solid #E2E8F0", borderRadius: 8, fontSize: 14, width: 260, outline: "none" }} />
        </div>
        <button onClick={() => { setShowAdd(true); setForm(EMPTY_FORM); setFieldErrors({}); setApiError(""); setCapturedPhoto(null); setFpStatus("idle") }}
          style={{ display: "flex", alignItems: "center", gap: 7, background: "#1B3A6B", color: "#fff", border: "none", borderRadius: 8, padding: "9px 18px", fontSize: 14, fontWeight: 600, cursor: "pointer" }}>
          <Plus size={15} /> Add Guard
        </button>
      </div>

      {/* Stats */}
      <div style={{ display: "flex", gap: 12, marginBottom: 20 }}>
        {[
          { label: "Total",     value: guards.length,                                                     color: "#1B3A6B" },
          { label: "Active",    value: guards.filter(g => (g.status ?? "ACTIVE") === "ACTIVE").length,    color: "#166534" },
          { label: "On Leave",  value: guards.filter(g => g.status === "ON_LEAVE").length,                color: "#1D4ED8" },
          { label: "Suspended", value: guards.filter(g => g.status === "SUSPENDED").length,               color: "#DC2626" },
        ].map(s => (
          <div key={s.label} style={{ flex: 1, background: "#F8FAFC", border: "1px solid #E2E8F0", borderRadius: 10, padding: "12px 20px" }}>
            <div style={{ fontSize: 22, fontWeight: 700, color: s.color }}>{s.value}</div>
            <div style={{ fontSize: 12, color: "#64748B", marginTop: 2 }}>{s.label}</div>
          </div>
        ))}
      </div>

      {/* PSiRA expiry alerts — only show if any are expired or expiring soon */}
      {(() => {
        const expiring = guards.filter(g => {
          const s = psiraExpiryStatus(g.psiraExpiryDate)
          return s !== null
        })
        if (expiring.length === 0) return null
        return (
          <div style={{ marginBottom: 16, padding: "12px 16px", background: "#FEF3C7", border: "1px solid #FCD34D", borderRadius: 10, display: "flex", gap: 10, alignItems: "flex-start" }}>
            <AlertTriangle size={16} color="#D97706" style={{ flexShrink: 0, marginTop: 1 }} />
            <div>
              <div style={{ fontSize: 13, fontWeight: 700, color: "#92400E", marginBottom: 4 }}>PSiRA Compliance Alert</div>
              <div style={{ display: "flex", flexDirection: "column", gap: 3 }}>
                {expiring.map(g => {
                  const s = psiraExpiryStatus(g.psiraExpiryDate)!
                  return (
                    <div key={g.id} style={{ fontSize: 12, color: "#78350F" }}>
                      {g.fullName} — <span style={{ color: s.color, fontWeight: 600 }}>{s.label}</span>
                      {g.psiraExpiryDate && ` (${fmtDate(g.psiraExpiryDate)})`}
                    </div>
                  )
                })}
              </div>
            </div>
          </div>
        )
      })()}

      {/* Status filter pills */}
      <div style={{ display: "flex", gap: 6, marginBottom: 18, flexWrap: "wrap" }}>
        {["ALL", ...GUARD_STATUSES.map(s => s.value)].map(s => {
          const cfg = s !== "ALL" ? STATUS_MAP[s] : null
          return (
            <button key={s} onClick={() => setStatusFilter(s)}
              style={{ padding: "5px 12px", borderRadius: 20, fontSize: 12, cursor: "pointer", border: "none", fontWeight: statusFilter === s ? 600 : 400,
                background: statusFilter === s ? (cfg?.color ?? "#1B3A6B") : "#F1F5F9",
                color: statusFilter === s ? "#fff" : "#64748B" }}>
              {s === "ALL" ? "All guards" : cfg?.label}
            </button>
          )
        })}
      </div>

      {/* Guard table */}
      {isLoading ? (
        <div style={{ textAlign: "center", padding: 40, color: "#94A3B8" }}>Loading guards...</div>
      ) : filtered.length === 0 ? (
        <div style={{ textAlign: "center", padding: "60px 20px", color: "#94A3B8" }}>
          <Shield size={40} style={{ marginBottom: 12, opacity: 0.4 }} />
          <div style={{ fontWeight: 600, color: "#475569" }}>No guards found</div>
        </div>
      ) : (
        <div style={{ border: "1px solid #E2E8F0", borderRadius: 12, overflow: "hidden" }}>
          <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 14 }}>
            <thead>
              <tr style={{ background: "#F8FAFC", borderBottom: "1px solid #E2E8F0" }}>
                {["Guard", "PSiRA No.", "Phone", "Grade", "Status", "Actions"].map(h => (
                  <th key={h} style={{ padding: "11px 16px", textAlign: "left", fontWeight: 600, fontSize: 12, color: "#64748B", letterSpacing: "0.05em" }}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {filtered.map((g, i) => {
                const gStatus   = g.status ?? "ACTIVE"
                const unavail   = gStatus !== "ACTIVE"
                const expiryBadge = psiraExpiryStatus(g.psiraExpiryDate)
                return (
                  <tr key={g.id} style={{ borderBottom: i < filtered.length - 1 ? "1px solid #F1F5F9" : "none", background: unavail ? "#FAFAFA" : "#fff" }}>
                    <td style={{ padding: "13px 16px" }}>
                      <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
                        <GuardAvatar guard={g} size={36} />
                        <div>
                          <div style={{ fontWeight: 600, color: unavail ? "#94A3B8" : "#0F172A" }}>{g.fullName}</div>
                          <div style={{ fontSize: 12, color: "#94A3B8" }}>ID: {g.idNumber || "—"}</div>
                        </div>
                      </div>
                    </td>
                    <td style={{ padding: "13px 16px", color: "#475569" }}>
                      <div>
                        <div style={{ display: "flex", alignItems: "center", gap: 5 }}><BadgeCheck size={13} color="#0D9488" />{g.psiraNumber || "—"}</div>
                        {expiryBadge && (
                          <span style={{ fontSize: 10, fontWeight: 600, background: expiryBadge.bg, color: expiryBadge.color, padding: "1px 6px", borderRadius: 10, marginTop: 3, display: "inline-block" }}>
                            {expiryBadge.label}
                          </span>
                        )}
                      </div>
                    </td>
                    <td style={{ padding: "13px 16px", color: "#475569" }}>
                      <div style={{ display: "flex", alignItems: "center", gap: 5 }}><Phone size={13} color="#94A3B8" />{g.phone || "—"}</div>
                    </td>
                    <td style={{ padding: "13px 16px" }}>
                      <span style={{ background: `${GRADE_COLORS[g.grade] || "#64748B"}18`, color: GRADE_COLORS[g.grade] || "#64748B", padding: "3px 10px", borderRadius: 20, fontWeight: 700, fontSize: 12 }}>Grade {g.grade}</span>
                    </td>
                    <td style={{ padding: "13px 16px" }}><StatusBadge status={gStatus} /></td>
                    <td style={{ padding: "13px 16px" }}>
                      <div style={{ display: "flex", gap: 6 }}>
                        <button onClick={() => setViewing(g)} title="View" style={{ background: "#EFF6FF", border: "none", borderRadius: 6, padding: "6px 8px", cursor: "pointer", color: "#1D4ED8" }}><Eye size={13} /></button>
                        <button onClick={() => openEdit(g)} title="Edit" style={{ background: "#F0FDF4", border: "none", borderRadius: 6, padding: "6px 8px", cursor: "pointer", color: "#166534" }}><Edit2 size={13} /></button>
                        <button onClick={() => { setChangingStatus(g); setNewStatus(gStatus); setStatusNote(""); setApiError("") }} title="Change status" style={{ background: "#FEF3C7", border: "none", borderRadius: 6, padding: "6px 8px", cursor: "pointer", color: "#D97706" }}><AlertTriangle size={13} /></button>
                        <button onClick={() => setDeleting(g)} title="Remove" style={{ background: "#FEF2F2", border: "none", borderRadius: 6, padding: "6px 8px", cursor: "pointer", color: "#DC2626" }}><Trash2 size={13} /></button>
                      </div>
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}

      {/* ── Add / Edit modals ─────────────────────────────────────────────── */}
      {showAdd && (
        <Modal title="Add New Guard" onClose={closeAdd} width={580}>
          <GuardForm />
          {apiError && <ErrBanner msg={apiError} />}
          <Footer onCancel={closeAdd} onSubmit={() => handleSubmit(false)} loading={createGuard.isPending} label="Add Guard" />
        </Modal>
      )}
      {editing && (
        <Modal title={`Edit — ${editing.fullName}`} onClose={closeEdit} width={580}>
          <GuardForm />
          {apiError && <ErrBanner msg={apiError} />}
          <Footer onCancel={closeEdit} onSubmit={() => handleSubmit(true)} loading={updateGuard.isPending} label="Save Changes" />
        </Modal>
      )}

      {/* ── View Guard Modal ──────────────────────────────────────────────── */}
      {viewing && (
        <Modal title="Guard Profile" onClose={() => setViewing(null)} width={480}>
          <div style={{ textAlign: "center", marginBottom: 22 }}>
            <div style={{ position: "relative", display: "inline-block" }}>
              <GuardAvatar guard={viewing} size={110} />
              <div style={{ position: "absolute", bottom: 4, right: 4, width: 28, height: 28, borderRadius: "50%", background: STATUS_MAP[viewing.status ?? "ACTIVE"]?.color ?? "#166534", border: "2px solid #fff", display: "flex", alignItems: "center", justifyContent: "center" }}>
                {(() => { const S = GUARD_STATUSES.find(s => s.value === (viewing.status ?? "ACTIVE")); const Icon = S?.icon ?? CheckCircle; return <Icon size={12} color="#fff" /> })()}
              </div>
            </div>
            <h3 style={{ margin: "12px 0 6px", fontSize: 20, fontWeight: 700, color: "#0F172A" }}>{viewing.fullName}</h3>
            <div style={{ display: "flex", justifyContent: "center", gap: 8, flexWrap: "wrap" }}>
              <span style={{ background: `${GRADE_COLORS[viewing.grade]}18`, color: GRADE_COLORS[viewing.grade], padding: "3px 12px", borderRadius: 20, fontSize: 12, fontWeight: 700 }}>Grade {viewing.grade}</span>
              <StatusBadge status={viewing.status} />
            </div>
          </div>

          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 10 }}>
            {[
              { label: "PSiRA Number", value: viewing.psiraNumber || "—" },
              { label: "SA ID Number", value: viewing.idNumber || "—" },
              { label: "Phone",        value: viewing.phone || "—" },
              { label: "Active",       value: viewing.active ? "Yes" : "No" },
            ].map(f => (
              <div key={f.label} style={{ padding: "10px 14px", background: "#F8FAFC", borderRadius: 8 }}>
                <div style={{ fontSize: 10, fontWeight: 700, color: "#94A3B8", textTransform: "uppercase" as const, letterSpacing: "0.06em", marginBottom: 4 }}>{f.label}</div>
                <div style={{ fontSize: 14, fontWeight: 600, color: "#0F172A" }}>{f.value}</div>
              </div>
            ))}
          </div>

          {/* PSiRA expiry compliance */}
          {(() => {
            const badge = psiraExpiryStatus(viewing.psiraExpiryDate)
            if (!badge) return null
            return (
              <div style={{ marginTop: 10, padding: "10px 14px", background: badge.bg, border: `1px solid ${badge.color}40`, borderRadius: 8, fontSize: 13, color: badge.color, fontWeight: 600, display: "flex", gap: 8, alignItems: "center" }}>
                <Calendar size={14} />{badge.label} {viewing.psiraExpiryDate && `(${fmtDate(viewing.psiraExpiryDate)})`}
              </div>
            )
          })()}

          {/* SA ID decoded */}
          {viewing.idNumber && (() => { const r = validateSaId(viewing.idNumber!); return r.valid && r.dob ? (
            <div style={{ marginTop: 10, padding: "10px 14px", background: "#F0FDF4", border: "1px solid #86EFAC", borderRadius: 8, fontSize: 13, color: "#166534", display: "flex", gap: 16 }}>
              <span>DOB: {r.dob}</span><span>{r.gender}</span>
            </div>
          ) : null })()}

          {/* Status history */}
          {viewing.statusNote && (
            <div style={{ marginTop: 10, padding: "10px 14px", background: "#FFFBEB", border: "1px solid #FDE68A", borderRadius: 8, fontSize: 13 }}>
              <div style={{ fontSize: 10, fontWeight: 700, color: "#94A3B8", textTransform: "uppercase" as const, letterSpacing: "0.06em", marginBottom: 4 }}>
                Status note {viewing.statusChangedAt && `· ${fmtDate(viewing.statusChangedAt)}`}
              </div>
              <div style={{ color: "#78350F" }}>{viewing.statusNote}</div>
            </div>
          )}

          {viewing.notes && (
            <div style={{ marginTop: 10, padding: "10px 14px", background: "#F8FAFC", border: "1px solid #E2E8F0", borderRadius: 8, fontSize: 13, color: "#374151" }}>
              <div style={{ fontSize: 10, fontWeight: 700, color: "#94A3B8", textTransform: "uppercase" as const, letterSpacing: "0.06em", marginBottom: 3 }}>Notes</div>
              {viewing.notes}
            </div>
          )}

          <div style={{ display: "flex", gap: 8, marginTop: 20 }}>
            <button onClick={() => { setViewing(null); openEdit(viewing) }} style={{ flex: 1, padding: "10px", background: "#1B3A6B", color: "#fff", border: "none", borderRadius: 9, fontSize: 14, fontWeight: 600, cursor: "pointer" }}>Edit Guard</button>
            <button onClick={() => { setViewing(null); setChangingStatus(viewing); setNewStatus(viewing.status ?? "ACTIVE"); setStatusNote(""); setApiError("") }} style={{ flex: 1, padding: "10px", background: "#FEF3C7", color: "#D97706", border: "1px solid #FDE68A", borderRadius: 9, fontSize: 14, fontWeight: 600, cursor: "pointer" }}>Change Status</button>
            <button onClick={() => setViewing(null)} style={{ padding: "10px 16px", border: "1px solid #E2E8F0", borderRadius: 9, background: "#fff", fontSize: 14, cursor: "pointer", color: "#374151" }}>Close</button>
          </div>
        </Modal>
      )}

      {/* ── Change Status Modal ───────────────────────────────────────────── */}
      {changingStatus && (
        <Modal title="Update Guard Status" onClose={() => { setChangingStatus(null); setApiError("") }} width={460}>
          <div style={{ display: "flex", alignItems: "center", gap: 12, marginBottom: 20, padding: "12px 14px", background: "#F8FAFC", borderRadius: 10 }}>
            <GuardAvatar guard={changingStatus} size={44} />
            <div>
              <div style={{ fontWeight: 700, color: "#0F172A", marginBottom: 3 }}>{changingStatus.fullName}</div>
              <StatusBadge status={changingStatus.status} />
            </div>
          </div>

          <label style={lbl}>Select New Status</label>
          <div style={{ display: "flex", flexDirection: "column", gap: 8, marginBottom: 18 }}>
            {GUARD_STATUSES.map(s => {
              const Icon = s.icon; const sel = newStatus === s.value
              return (
                <button key={s.value} onClick={() => setNewStatus(s.value)}
                  style={{ display: "flex", alignItems: "center", gap: 12, padding: "12px 14px", border: `2px solid ${sel ? s.color : "#E2E8F0"}`, borderRadius: 9, cursor: "pointer", background: sel ? s.bg : "#fff", textAlign: "left" as const, width: "100%" }}>
                  <div style={{ width: 32, height: 32, borderRadius: "50%", background: `${s.color}18`, display: "flex", alignItems: "center", justifyContent: "center", flexShrink: 0 }}>
                    <Icon size={15} color={s.color} />
                  </div>
                  <div style={{ flex: 1 }}>
                    <div style={{ fontSize: 14, fontWeight: 600, color: sel ? s.color : "#0F172A" }}>{s.label}</div>
                    <div style={{ fontSize: 12, color: "#94A3B8" }}>{s.description}</div>
                  </div>
                  {sel && <CheckCircle size={16} color={s.color} />}
                </button>
              )
            })}
          </div>

          {newStatus && newStatus !== "ACTIVE" && (
            <div style={{ marginBottom: 16 }}>
              <label style={lbl}>
                Reason / Note{(newStatus === "SUSPENDED" || newStatus === "TERMINATED") ? " *" : " (optional)"}
              </label>
              <textarea value={statusNote} onChange={e => setStatusNote(e.target.value)} rows={3}
                placeholder={
                  newStatus === "ON_LEAVE" ? "e.g. Annual leave 1–14 June 2026" :
                  newStatus === "SUSPENDED" ? "e.g. Pending disciplinary hearing re: incident on 28 May" :
                  newStatus === "UNDER_INVESTIGATION" ? "e.g. Incident report #IR-2026-042 filed" :
                  "e.g. Resignation accepted effective 31 May 2026"
                }
                style={{ width: "100%", padding: "9px 12px", border: "1.5px solid #E2E8F0", borderRadius: 8, fontSize: 14, boxSizing: "border-box" as const, resize: "vertical" as const, outline: "none" }} />
            </div>
          )}

          {newStatus && newStatus !== "ACTIVE" && (
            <div style={{ marginBottom: 16, padding: "10px 14px", background: "#FEF3C7", border: "1px solid #FCD34D", borderRadius: 8, fontSize: 12, color: "#92400E", display: "flex", gap: 8 }}>
              <AlertTriangle size={14} style={{ flexShrink: 0, marginTop: 1 }} />
              Guards with this status will not appear as available when scheduling new shifts.
            </div>
          )}

          {apiError && <ErrBanner msg={apiError} />}

          <div style={{ display: "flex", gap: 10, marginTop: 8 }}>
            <button onClick={() => { setChangingStatus(null); setApiError("") }} style={{ flex: 1, padding: "10px", border: "1.5px solid #E2E8F0", borderRadius: 9, background: "#fff", fontSize: 14, fontWeight: 600, cursor: "pointer", color: "#374151" }}>Cancel</button>
            <button
              onClick={() => updateStatus.mutate({ id: changingStatus.id, status: newStatus, note: statusNote })}
              disabled={!newStatus || updateStatus.isPending || ((newStatus === "SUSPENDED" || newStatus === "TERMINATED") && !statusNote.trim())}
              style={{ flex: 1, padding: "10px", border: "none", borderRadius: 9, fontSize: 14, fontWeight: 700, cursor: "pointer",
                background: newStatus && !((newStatus === "SUSPENDED" || newStatus === "TERMINATED") && !statusNote.trim()) ? "#1B3A6B" : "#E2E8F0",
                color: "#fff" }}>
              {updateStatus.isPending ? "Updating..." : "Update Status"}
            </button>
          </div>
        </Modal>
      )}

      {/* ── Delete Confirmation ───────────────────────────────────────────── */}
      {deleting && (
        <Modal title="" onClose={() => { setDeleting(null); setApiError("") }} width={400}>
          <div style={{ textAlign: "center" }}>
            <div style={{ width: 56, height: 56, borderRadius: "50%", background: "#FEF2F2", border: "2px solid #FECACA", display: "flex", alignItems: "center", justifyContent: "center", margin: "0 auto 16px" }}>
              <Trash2 size={22} color="#DC2626" />
            </div>
            <h3 style={{ margin: "0 0 8px", fontSize: 17, fontWeight: 700 }}>Remove Guard?</h3>
            <div style={{ display: "inline-flex", alignItems: "center", gap: 8, background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 40, padding: "6px 14px", marginBottom: 14 }}>
              <Shield size={13} color="#DC2626" /><span style={{ fontSize: 13, fontWeight: 600 }}>{deleting.fullName}</span>
            </div>
            <p style={{ fontSize: 13, color: "#64748B", margin: "0 0 20px", lineHeight: 1.6 }}>
              Deactivates the guard record. Shift history and incident records are preserved.
            </p>
            {apiError && <ErrBanner msg={apiError} />}
            <div style={{ display: "flex", gap: 10 }}>
              <button onClick={() => { setDeleting(null); setApiError("") }} style={{ flex: 1, padding: "10px", border: "1.5px solid #E2E8F0", borderRadius: 9, background: "#fff", fontSize: 14, fontWeight: 600, cursor: "pointer", color: "#374151" }}>Keep Guard</button>
              <button onClick={() => deleteGuard.mutate(deleting.id)} disabled={deleteGuard.isPending}
                style={{ flex: 1, padding: "10px", border: "none", borderRadius: 9, background: "#DC2626", color: "#fff", fontSize: 14, fontWeight: 700, cursor: "pointer" }}>
                {deleteGuard.isPending ? "Removing..." : "Yes, Remove"}
              </button>
            </div>
          </div>
        </Modal>
      )}
    </div>
  )
}

// ── Shared sub-components ──────────────────────────────────────────────────────

function Modal({ title, onClose, children, width = 540 }: { title: string; onClose: () => void; children: React.ReactNode; width?: number }) {
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

function Footer({ onCancel, onSubmit, loading, label }: { onCancel: () => void; onSubmit: () => void; loading: boolean; label: string }) {
  return (
    <div style={{ display: "flex", gap: 10, justifyContent: "flex-end", marginTop: 20 }}>
      <button onClick={onCancel} style={{ padding: "9px 18px", border: "1px solid #E2E8F0", borderRadius: 9, background: "#fff", fontSize: 14, cursor: "pointer", color: "#374151" }}>Cancel</button>
      <button onClick={onSubmit} disabled={loading} style={{ padding: "9px 22px", background: loading ? "#94A3B8" : "#1B3A6B", color: "#fff", border: "none", borderRadius: 9, fontSize: 14, fontWeight: 700, cursor: loading ? "not-allowed" : "pointer" }}>
        {loading ? "Saving..." : label}
      </button>
    </div>
  )
}

const omit = (obj: Record<string, string>, key: string) => { const n = { ...obj }; delete n[key]; return n }
const lbl: React.CSSProperties = { display: "block", fontSize: 13, fontWeight: 600, color: "#374151", marginBottom: 5 }

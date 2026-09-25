// src/pages/security/GuardsTab.tsx
//
// CHANGE (bug fix): GuardForm was previously defined AS A COMPONENT INSIDE
// GuardsTab's function body (`const GuardForm = () => (...)`), then rendered
// as `<GuardForm />`. Because that makes GuardForm a brand-new function
// reference on every render of GuardsTab, React treats it as a completely
// different component type each time -- so on EVERY keystroke in ANY field
// (not just idNumber), the entire form subtree unmounted and remounted,
// dropping focus unpredictably. This is the cursor-jump bug. It was most
// visible on the ID field because that field triggers two state updates per
// keystroke (setForm + setFieldErrors) and has a conditional validation
// block that appears/disappears below it as you type, making the remount
// more noticeable there than in a plain text field -- but the underlying
// bug affected every field in the form equally.
//
// FIX: GuardFormFields is now a real top-level component (defined outside
// GuardsTab, at module scope) with a stable identity across renders, so
// React reconciles it normally instead of remounting it. Everything it
// used to read from the parent's closure is now passed explicitly as props.
//
// All other behavior/logic is unchanged from the original file.

import { useState, useRef } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { Plus, Search, Shield, Phone, BadgeCheck, Trash2, X, Edit2, Eye, AlertCircle, Fingerprint, Upload, CheckCircle, AlertTriangle, Clock, Ban, HelpCircle, Calendar, ShieldCheck } from "lucide-react"
import { usePermission } from "../../hooks/usePermission"
// ── Types ──────────────────────────────────────────────────────────────────────

interface Guard {
  id: string; firstName: string; lastName: string; fullName: string
  psiraNumber: string | null; idNumber: string | null; phone: string | null
  grade: string; active: boolean
  status: string; statusNote: string | null; statusChangedAt: string | null
  psiraExpiryDate: string | null
  notes: string | null; photoUrl: string | null; createdAt: string
  employeeCode: string | null
  // FIX (Security P4 — VettingController): field always existed on the
  // backend entity but GuardResponse never exposed it until now.
  cpVettingTier: string | null
  bankName: string | null; bankAccountNumber: string | null; bankBranchCode: string | null
}

interface GuardFormState {
  firstName: string; lastName: string; psiraNumber: string; idNumber: string
  phone: string; grade: string; notes: string; psiraExpiryDate: string
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
const EMPTY_FORM: GuardFormState = { firstName: "", lastName: "", psiraNumber: "", idNumber: "", phone: "", grade: "C", notes: "", psiraExpiryDate: "" }

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
  const hasPhoto = guard.photoUrl && guard.photoUrl !== "PENDING_UPLOAD"
  const unavail  = (guard.status ?? "ACTIVE") !== "ACTIVE"
  return hasPhoto ? (
    <img src={guard.photoUrl!} alt={guard.fullName}
      style={{ width: size, height: size, borderRadius: "50%", objectFit: "cover",
               border: "2px solid var(--hf-info-border)", flexShrink: 0, opacity: unavail ? 0.6 : 1 }} />
  ) : (
    <div style={{ width: size, height: size, borderRadius: "50%",
                  background: unavail ? "var(--hf-surface-sunken)" : "var(--hf-info-soft)",
                  border: `2px solid ${unavail ? "#E2E8F0" : "#BFDBFE"}`,
                  display: "flex", alignItems: "center", justifyContent: "center",
                  fontWeight: 700, fontSize: size * 0.33,
                  color: unavail ? "var(--hf-text-faint)" : "var(--hf-info-text)", flexShrink: 0 }}>
      {guard.firstName?.[0]}{guard.lastName?.[0]}
    </div>
  )
}

// ── Shared small components (module scope -- fine, these don't hold state) ────

function ErrBanner({ msg }: { msg: string }) {
  return (
    <div style={{ marginTop: 14, padding: "10px 12px", background: "var(--hf-danger-soft)", border: "1px solid var(--hf-danger-border)", borderRadius: 8, fontSize: 13, color: "var(--hf-danger-text)", display: "flex", alignItems: "center", gap: 8 }}>
      <AlertCircle size={14} />{msg}
    </div>
  )
}

function FieldError({ fieldErrors, name }: { fieldErrors: Record<string, string>; name: string }) {
  return fieldErrors[name] ? (
    <div style={{ display: "flex", alignItems: "center", gap: 4, fontSize: 12, color: "var(--hf-danger-text)", marginTop: 4 }}>
      <AlertCircle size={12} />{fieldErrors[name]}
    </div>
  ) : null
}

function inputStyle(fieldErrors: Record<string, string>, key: string): React.CSSProperties {
  return {
    width: "100%", padding: "9px 12px", border: `1.5px solid ${fieldErrors[key] ? "#DC2626" : "#E2E8F0"}`,
    borderRadius: 8, fontSize: 14, boxSizing: "border-box" as const,
    background: fieldErrors[key] ? "#FFF5F5" : "#fff", outline: "none",
  }
}

// ── GuardFormFields — now a real top-level component (THE FIX) ────────────────
//
// Previously defined inside GuardsTab as `const GuardForm = () => (...)`.
// Moved out here so it has a stable component identity across renders --
// see the file-header comment for the full explanation of why that inline
// definition caused the cursor-jump bug.

interface GuardFormFieldsProps {
  form: GuardFormState
  setForm: React.Dispatch<React.SetStateAction<GuardFormState>>
  fieldErrors: Record<string, string>
  setFieldErrors: React.Dispatch<React.SetStateAction<Record<string, string>>>
  capturedPhoto: string | null
  setCapturedPhoto: (v: string | null) => void
  photoMode: "none" | "camera"
  videoRef: React.RefObject<HTMLVideoElement | null>
  canvasRef: React.RefObject<HTMLCanvasElement | null>
  startCamera: () => void
  capturePhoto: () => void
  stopCamera: () => void
  handleFileUpload: (e: React.ChangeEvent<HTMLInputElement>) => void
}

function GuardFormFields({
  form, setForm, fieldErrors, setFieldErrors,
  capturedPhoto, setCapturedPhoto, photoMode,
  videoRef, canvasRef, startCamera, capturePhoto, stopCamera,
  handleFileUpload,
}: GuardFormFieldsProps) {
  const idFeedback = validateSaId(form.idNumber)
  const inpSt = (key: string) => inputStyle(fieldErrors, key)

  return (
    <>
      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14 }}>
        <div>
          <label style={lbl}>First Name *</label>
          <input value={form.firstName} onChange={e => { setForm(f => ({ ...f, firstName: e.target.value })); setFieldErrors(f => omit(f, "firstName")) }} placeholder="James" style={inpSt("firstName")} autoFocus />
          <FieldError fieldErrors={fieldErrors} name="firstName" />
        </div>
        <div>
          <label style={lbl}>Last Name *</label>
          <input value={form.lastName} onChange={e => { setForm(f => ({ ...f, lastName: e.target.value })); setFieldErrors(f => omit(f, "lastName")) }} placeholder="Dlamini" style={inpSt("lastName")} />
          <FieldError fieldErrors={fieldErrors} name="lastName" />
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
          <FieldError fieldErrors={fieldErrors} name="idNumber" />
          {form.idNumber.length === 13 && (
            idFeedback.valid ? (
              <div style={{ marginTop: 6, padding: "8px 10px", background: "var(--hf-success-soft)", border: "1px solid var(--hf-success-border)", borderRadius: 7, fontSize: 12, color: "var(--hf-success-text-strong)", display: "flex", gap: 14 }}>
                <span>✓ Valid</span>
                {idFeedback.dob && <span>DOB: {idFeedback.dob}</span>}
                {idFeedback.gender && <span>{idFeedback.gender}</span>}
              </div>
            ) : (
              <div style={{ marginTop: 6, padding: "8px 10px", background: "var(--hf-danger-soft)", border: "1px solid var(--hf-danger-border)", borderRadius: 7, fontSize: 12, color: "var(--hf-danger-text)" }}>
                ✗ {idFeedback.error}
              </div>
            )
          )}
        </div>
        <div>
          <label style={lbl}>Phone</label>
          <input value={form.phone} onChange={e => { setForm(f => ({ ...f, phone: e.target.value })); setFieldErrors(f => omit(f, "phone")) }} placeholder="+27 82 555 0101" style={inpSt("phone")} />
          <FieldError fieldErrors={fieldErrors} name="phone" />
        </div>
        <div>
          <label style={lbl}>PSiRA Grade</label>
          <select value={form.grade} onChange={e => setForm(f => ({ ...f, grade: e.target.value }))} style={{ ...inpSt("grade"), background: "var(--hf-surface)" }}>
            {["A","B","C","D","E"].map(g => <option key={g} value={g}>Grade {g}</option>)}
          </select>
        </div>
        <div>
          <label style={lbl}>PSiRA Expiry Date <span style={{ fontWeight: 400, color: "var(--hf-text-faint)" }}>(optional)</span></label>
          <input type="date" value={form.psiraExpiryDate}
            onChange={e => setForm(f => ({ ...f, psiraExpiryDate: e.target.value }))}
            style={inpSt("psiraExpiryDate")} />
          {form.psiraExpiryDate && (() => { const s = psiraExpiryStatus(form.psiraExpiryDate); return s ? (
            <div style={{ marginTop: 6, fontSize: 12, color: s.color, fontWeight: 600 }}>{s.label}</div>
          ) : <div style={{ marginTop: 6, fontSize: 12, color: "var(--hf-success-text-strong)" }}>✓ Valid</div> })()}
        </div>
        <div style={{ gridColumn: "1 / -1" }}>
          <label style={lbl}>Notes</label>
          <textarea value={form.notes} onChange={e => setForm(f => ({ ...f, notes: e.target.value }))} rows={2} placeholder="Any relevant notes..." style={{ ...inpSt("notes"), resize: "vertical" as const }} />
        </div>
      </div>

      {/* Photo */}
      <div style={{ marginTop: 16, padding: 16, background: "var(--hf-surface-muted)", borderRadius: 10, border: "1px solid var(--hf-border)" }}>
        <div style={{ fontSize: 13, fontWeight: 700, color: "var(--hf-text-secondary)", marginBottom: 12 }}>Guard Photo
          <span style={{ fontSize: 11, fontWeight: 400, color: "var(--hf-text-faint)", marginLeft: 8 }}>(dev mode — stored as PENDING_UPLOAD until S3 is configured)</span>
        </div>
        {capturedPhoto && capturedPhoto !== "PENDING_UPLOAD" ? (
          <div style={{ display: "flex", alignItems: "center", gap: 14 }}>
            <img src={capturedPhoto} alt="Guard" style={{ width: 80, height: 80, borderRadius: 10, objectFit: "cover", border: "2px solid var(--hf-accent)" }} />
            <div>
              <div style={{ fontSize: 13, fontWeight: 600, color: "var(--hf-success-text-strong)", marginBottom: 6 }}>✓ Photo captured</div>
              <button onClick={() => setCapturedPhoto(null)} style={{ fontSize: 12, color: "var(--hf-danger-text)", background: "none", border: "none", cursor: "pointer", padding: 0 }}>Remove</button>
            </div>
          </div>
        ) : photoMode === "camera" ? (
          <div>
            <video ref={videoRef} style={{ width: "100%", borderRadius: 8, maxHeight: 200, objectFit: "cover" }} />
            <canvas ref={canvasRef} style={{ display: "none" }} />
            <div style={{ display: "flex", gap: 8, marginTop: 8 }}>
              <button onClick={capturePhoto} style={{ flex: 1, padding: "8px", background: "var(--hf-accent)", color: "var(--hf-text-on-solid)", border: "none", borderRadius: 7, fontSize: 13, fontWeight: 600, cursor: "pointer" }}>Capture</button>
              <button onClick={stopCamera} style={{ padding: "8px 14px", background: "var(--hf-surface-sunken)", border: "none", borderRadius: 7, fontSize: 13, cursor: "pointer", color: "var(--hf-text-muted)" }}>Cancel</button>
            </div>
          </div>
        ) : (
          <div style={{ display: "flex", gap: 8 }}>
            <button onClick={startCamera} style={{ flex: 1, display: "flex", alignItems: "center", justifyContent: "center", gap: 6, padding: "9px", background: "var(--hf-info-soft)", color: "var(--hf-info-text)", border: "1px solid var(--hf-info-border)", borderRadius: 8, fontSize: 13, cursor: "pointer" }}>
              📷 Use Camera
            </button>
            <label style={{ flex: 1, display: "flex", alignItems: "center", justifyContent: "center", gap: 6, padding: "9px", background: "var(--hf-success-soft)", color: "var(--hf-success-text-strong)", border: "1px solid var(--hf-success-border)", borderRadius: 8, fontSize: 13, cursor: "pointer" }}>
              <Upload size={14} /> Upload Photo
              <input type="file" accept="image/*" onChange={handleFileUpload} style={{ display: "none" }} />
            </label>
          </div>
        )}
      </div>

      {/* FIX (P0 backlog item 1.4): this used to be a fake capture —
          setTimeout(() => setFpStatus("done"), 2500) with no scanner
          invoked and nothing stored, ending in a "Captured" success
          state regardless. Replaced with an honest, permanently-disabled
          state rather than a real capture flow — actual fingerprint
          hardware integration is a genuinely separate, larger piece of
          work (needs a scanner SDK, not something a web app can do on
          its own), and the report's own recommendation was "either wire
          to a real capture flow or replace with a clear 'not yet
          available' state." The FINGERPRINT_FORM document-upload
          category already covers this need today (a scanned paper form,
          uploaded like any other guard document) — this stub no longer
          pretends to be a substitute for it. */}
      <div style={{ marginTop: 12, padding: 16, background: "var(--hf-surface-muted)", borderRadius: 10, border: "1px solid var(--hf-border)" }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
          <div>
            <div style={{ fontSize: 13, fontWeight: 700, color: "var(--hf-text-secondary)", marginBottom: 2 }}>Fingerprint</div>
            <div style={{ fontSize: 11, color: "var(--hf-text-faint)" }}>Not available in the web console — requires scanner hardware. Use the "Fingerprint Form" document upload instead.</div>
          </div>
          <button disabled
            style={{ display: "flex", alignItems: "center", gap: 6, padding: "8px 14px", background: "var(--hf-surface-sunken)", color: "var(--hf-text-faint)", border: "1px solid var(--hf-border)", borderRadius: 8, fontSize: 13, cursor: "not-allowed", fontWeight: 600 }}>
            <Fingerprint size={13} />
            Not Available
          </button>
        </div>
      </div>
    </>
  )
}

// ── Main component ─────────────────────────────────────────────────────────────

export default function GuardsTab() {
  const qc = useQueryClient()
  const videoRef  = useRef<HTMLVideoElement>(null)
  const canvasRef = useRef<HTMLCanvasElement>(null)
  // FIX (Security P4 — VettingController) — see the setCpVettingTier
  // mutation's own comment. VettingController requires VIP_DETAIL_ACCESS,
  // stricter than the SECURITY_MANAGE the rest of this tab uses, so the
  // section is only shown to users who actually have it — same
  // client-side-mirror convention as the fuel margin report.
  const canViewCpTier = usePermission("VIP_DETAIL_ACCESS")

  const [search,          setSearch]          = useState("")
  const [showAdd,         setShowAdd]         = useState(false)
  const [editing,         setEditing]         = useState<Guard | null>(null)
  const [viewing,         setViewing]         = useState<Guard | null>(null)
  const [deleting,        setDeleting]        = useState<Guard | null>(null)
  const [changingStatus,  setChangingStatus]  = useState<Guard | null>(null)
  const [form,            setForm]            = useState<GuardFormState>(EMPTY_FORM)
  const [fieldErrors,     setFieldErrors]     = useState<Record<string, string>>({})
  const [apiError,        setApiError]        = useState("")
  const [photoMode,       setPhotoMode]       = useState<"none" | "camera">("none")
  const [capturedPhoto,   setCapturedPhoto]   = useState<string | null>(null)
  const [cameraStream,    setCameraStream]    = useState<MediaStream | null>(null)
  // FIX (P0 backlog item 1.3) — see the enrollGuard mutation's own
  // comment for the full context on why this exists.
  const [enrollPin,       setEnrollPin]        = useState("")
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

  const enrollGuard = useMutation({
    mutationFn: ({ id, pin }: { id: string; pin: string }) =>
      apiClient.post(`/api/v1/security/guards/${id}/enrol`, { pin }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["guards"] }); setEnrollPin("") },
    onError:   (e: any) => setApiError(e.response?.data?.message ?? "Failed to enroll guard"),
  })

  // FIX (Security P4 — VettingController): cpVettingTier has always
  // existed on Guard and is enforced automatically by
  // CloseProtectionService's DetailAssignment gate, but there was no
  // way to set it (or see it, until GuardResponse was extended
  // alongside this) anywhere in the admin UI.
  const [cpTierForm, setCpTierForm] = useState({ tier: "STANDARD", clearedAt: new Date().toISOString().slice(0, 10), expiresAt: "" })
  const setCpVettingTier = useMutation({
    mutationFn: ({ id, body }: { id: string; body: any }) =>
      apiClient.post(`/api/v1/security/cp/vetting/officers/${id}/tier`, body),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["guards"] }),
    onError:   (e: any) => setApiError(e.response?.data?.message ?? "Failed to set CP vetting tier"),
  })

  // FIX: closes the confirmed "no structured banking fields" gap —
  // payroll export needed manual cross-referencing without these.
  const [bankForm, setBankForm] = useState({ bankName: "", bankAccountNumber: "", bankBranchCode: "" })
  const updateBankDetails = useMutation({
    mutationFn: ({ id, body }: { id: string; body: any }) =>
      apiClient.post(`/api/v1/security/guards/${id}/bank-details`, body),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["guards"] }),
    onError:   (e: any) => setApiError(e.response?.data?.message ?? "Failed to save bank details"),
  })

  // ── Helpers ────────────────────────────────────────────────────────────────

  const closeAdd  = () => { setShowAdd(false);  setForm(EMPTY_FORM); setFieldErrors({}); setApiError(""); stopCamera(); setCapturedPhoto(null) }
  const closeEdit = () => { setEditing(null);   setForm(EMPTY_FORM); setFieldErrors({}); setApiError(""); stopCamera(); setCapturedPhoto(null); setEnrollPin("") }

  const openEdit = (g: Guard) => {
    setEditing(g)
    setForm({ firstName: g.firstName, lastName: g.lastName,
              psiraNumber: g.psiraNumber ?? "", idNumber: g.idNumber ?? "",
              phone: g.phone ?? "", grade: g.grade, notes: g.notes ?? "",
              psiraExpiryDate: g.psiraExpiryDate ?? "" })
    setCapturedPhoto(g.photoUrl && g.photoUrl !== "PENDING_UPLOAD" ? g.photoUrl : null)
    setBankForm({ bankName: g.bankName ?? "", bankAccountNumber: g.bankAccountNumber ?? "", bankBranchCode: g.bankBranchCode ?? "" })
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
    const body = { ...form, photoUrl: capturedPhoto ?? undefined, psiraExpiryDate: form.psiraExpiryDate || null }
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

  const StatusBadge = ({ status }: { status?: string }) => {
    const s = STATUS_MAP[status ?? "ACTIVE"] ?? STATUS_MAP.ACTIVE
    const Icon = s.icon
    return <span style={{ display: "inline-flex", alignItems: "center", gap: 4, background: s.bg, color: s.color, padding: "3px 10px", borderRadius: 20, fontSize: 11, fontWeight: 600, whiteSpace: "nowrap" as const }}><Icon size={10} />{s.label}</span>
  }

  // ── Render ─────────────────────────────────────────────────────────────────

  return (
    <div>
      {/* Toolbar */}
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 20 }}>
        <div style={{ position: "relative" }}>
          <Search size={15} style={{ position: "absolute", left: 12, top: "50%", transform: "translateY(-50%)", color: "var(--hf-text-faint)" }} />
          <input value={search} onChange={e => setSearch(e.target.value)} placeholder="Search by name or PSiRA..."
            style={{ paddingLeft: 36, paddingRight: 14, paddingTop: 9, paddingBottom: 9, border: "1px solid var(--hf-border)", borderRadius: 8, fontSize: 14, width: 260, outline: "none" }} />
        </div>
        <button onClick={() => { setShowAdd(true); setForm(EMPTY_FORM); setFieldErrors({}); setApiError(""); setCapturedPhoto(null) }}
          style={{ display: "flex", alignItems: "center", gap: 7, background: "var(--hf-primary)", color: "var(--hf-text-on-solid)", border: "none", borderRadius: 8, padding: "9px 18px", fontSize: 14, fontWeight: 600, cursor: "pointer" }}>
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
          <div key={s.label} style={{ flex: 1, background: "var(--hf-surface-muted)", border: "1px solid var(--hf-border)", borderRadius: 10, padding: "12px 20px" }}>
            <div style={{ fontSize: 22, fontWeight: 700, color: s.color }}>{s.value}</div>
            <div style={{ fontSize: 12, color: "var(--hf-text-muted)", marginTop: 2 }}>{s.label}</div>
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
          <div style={{ marginBottom: 16, padding: "12px 16px", background: "var(--hf-warning-soft-strong)", border: "1px solid var(--hf-warning-border-strong)", borderRadius: 10, display: "flex", gap: 10, alignItems: "flex-start" }}>
            <AlertTriangle size={16} color="#D97706" style={{ flexShrink: 0, marginTop: 1 }} />
            <div>
              <div style={{ fontSize: 13, fontWeight: 700, color: "var(--hf-warning-text-deep)", marginBottom: 4 }}>PSiRA Compliance Alert</div>
              <div style={{ display: "flex", flexDirection: "column", gap: 3 }}>
                {expiring.map(g => {
                  const s = psiraExpiryStatus(g.psiraExpiryDate)!
                  return (
                    <div key={g.id} style={{ fontSize: 12, color: "var(--hf-warning-text-deep)" }}>
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
                background: statusFilter === s ? (cfg?.color ?? "var(--hf-primary)") : "var(--hf-surface-sunken)",
                color: statusFilter === s ? "var(--hf-text-on-solid)" : "var(--hf-text-muted)" }}>
              {s === "ALL" ? "All guards" : cfg?.label}
            </button>
          )
        })}
      </div>

      {/* Guard table */}
      {isLoading ? (
        <div style={{ textAlign: "center", padding: 40, color: "var(--hf-text-faint)" }}>Loading guards...</div>
      ) : filtered.length === 0 ? (
        <div style={{ textAlign: "center", padding: "60px 20px", color: "var(--hf-text-faint)" }}>
          <Shield size={40} style={{ marginBottom: 12, opacity: 0.4 }} />
          <div style={{ fontWeight: 600, color: "var(--hf-text-tertiary)" }}>No guards found</div>
        </div>
      ) : (
        <div style={{ border: "1px solid var(--hf-border)", borderRadius: 12, overflow: "hidden" }}>
          <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 14 }}>
            <thead>
              <tr style={{ background: "var(--hf-surface-muted)", borderBottom: "1px solid var(--hf-border)" }}>
                {["Guard", "PSiRA No.", "Phone", "Grade", "Status", "Actions"].map(h => (
                  <th key={h} style={{ padding: "11px 16px", textAlign: "left", fontWeight: 600, fontSize: 12, color: "var(--hf-text-muted)", letterSpacing: "0.05em" }}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {filtered.map((g, i) => {
                const gStatus   = g.status ?? "ACTIVE"
                const unavail   = gStatus !== "ACTIVE"
                const expiryBadge = psiraExpiryStatus(g.psiraExpiryDate)
                return (
                  <tr key={g.id} style={{ borderBottom: i < filtered.length - 1 ? "1px solid var(--hf-border-subtle)" : "none", background: unavail ? "var(--hf-surface-muted)" : "var(--hf-surface)" }}>
                    <td style={{ padding: "13px 16px" }}>
                      <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
                        <GuardAvatar guard={g} size={36} />
                        <div>
                          <div style={{ fontWeight: 600, color: unavail ? "var(--hf-text-faint)" : "var(--hf-text)" }}>{g.fullName}</div>
                          <div style={{ fontSize: 12, color: "var(--hf-text-faint)" }}>
                            ID: {g.idNumber || "—"}
                            {g.employeeCode && <span style={{ marginLeft: 8, fontFamily: "monospace", color: "var(--hf-violet-text)", fontWeight: 600 }}>{g.employeeCode}</span>}
                          </div>
                        </div>
                      </div>
                    </td>
                    <td style={{ padding: "13px 16px", color: "var(--hf-text-tertiary)" }}>
                      <div>
                        <div style={{ display: "flex", alignItems: "center", gap: 5 }}><BadgeCheck size={13} color="#0D9488" />{g.psiraNumber || "—"}</div>
                        {expiryBadge && (
                          <span style={{ fontSize: 10, fontWeight: 600, background: expiryBadge.bg, color: expiryBadge.color, padding: "1px 6px", borderRadius: 10, marginTop: 3, display: "inline-block" }}>
                            {expiryBadge.label}
                          </span>
                        )}
                      </div>
                    </td>
                    <td style={{ padding: "13px 16px", color: "var(--hf-text-tertiary)" }}>
                      <div style={{ display: "flex", alignItems: "center", gap: 5 }}><Phone size={13} color="#94A3B8" />{g.phone || "—"}</div>
                    </td>
                    <td style={{ padding: "13px 16px" }}>
                      <span style={{ background: `${GRADE_COLORS[g.grade] || "#64748B"}18`, color: GRADE_COLORS[g.grade] || "var(--hf-text-muted)", padding: "3px 10px", borderRadius: 20, fontWeight: 700, fontSize: 12 }}>Grade {g.grade}</span>
                    </td>
                    <td style={{ padding: "13px 16px" }}><StatusBadge status={gStatus} /></td>
                    <td style={{ padding: "13px 16px" }}>
                      <div style={{ display: "flex", gap: 6 }}>
                        <button onClick={() => setViewing(g)} title="View" style={{ background: "var(--hf-info-soft)", border: "none", borderRadius: 6, padding: "6px 8px", cursor: "pointer", color: "var(--hf-info-text)" }}><Eye size={13} /></button>
                        <button onClick={() => openEdit(g)} title="Edit" style={{ background: "var(--hf-success-soft)", border: "none", borderRadius: 6, padding: "6px 8px", cursor: "pointer", color: "var(--hf-success-text-strong)" }}><Edit2 size={13} /></button>
                        <button onClick={() => { setChangingStatus(g); setNewStatus(gStatus); setStatusNote(""); setApiError("") }} title="Change status" style={{ background: "var(--hf-warning-soft-strong)", border: "none", borderRadius: 6, padding: "6px 8px", cursor: "pointer", color: "var(--hf-warning-text)" }}><AlertTriangle size={13} /></button>
                        <button onClick={() => setDeleting(g)} title="Remove" style={{ background: "var(--hf-danger-soft)", border: "none", borderRadius: 6, padding: "6px 8px", cursor: "pointer", color: "var(--hf-danger-text)" }}><Trash2 size={13} /></button>
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
          <GuardFormFields
            form={form} setForm={setForm}
            fieldErrors={fieldErrors} setFieldErrors={setFieldErrors}
            capturedPhoto={capturedPhoto} setCapturedPhoto={setCapturedPhoto}
            photoMode={photoMode} videoRef={videoRef} canvasRef={canvasRef}
            startCamera={startCamera} capturePhoto={capturePhoto} stopCamera={stopCamera}
            handleFileUpload={handleFileUpload}
          />
          {apiError && <ErrBanner msg={apiError} />}
          <Footer onCancel={closeAdd} onSubmit={() => handleSubmit(false)} loading={createGuard.isPending} label="Add Guard" />
        </Modal>
      )}
      {editing && (
        <Modal title={`Edit — ${editing.fullName}`} onClose={closeEdit} width={580}>
          <GuardFormFields
            form={form} setForm={setForm}
            fieldErrors={fieldErrors} setFieldErrors={setFieldErrors}
            capturedPhoto={capturedPhoto} setCapturedPhoto={setCapturedPhoto}
            photoMode={photoMode} videoRef={videoRef} canvasRef={canvasRef}
            startCamera={startCamera} capturePhoto={capturePhoto} stopCamera={stopCamera}
            handleFileUpload={handleFileUpload}
          />
          {/* FIX (P0 backlog item 1.3) — see the enrollGuard mutation's own
              comment for the full context on why this exists. */}
          <div style={{ marginTop: 12, padding: 16, background: "var(--hf-surface-muted)", borderRadius: 10, border: "1px solid var(--hf-border)" }}>
            <div style={{ fontSize: 13, fontWeight: 700, color: "var(--hf-text-secondary)", marginBottom: 2 }}>Mobile App Access</div>
            <div style={{ fontSize: 11, color: "var(--hf-text-faint)", marginBottom: 10 }}>
              Set or reset this guard's PIN to enable Shield app login. Communicate the PIN verbally or via SMS — never email.
            </div>
            <div style={{ display: "flex", gap: 8 }}>
              <input
                value={enrollPin}
                onChange={e => setEnrollPin(e.target.value.replace(/\D/g, "").slice(0, 6))}
                placeholder="6-digit PIN"
                inputMode="numeric"
                style={{ flex: 1, padding: "9px 12px", border: "1.5px solid var(--hf-border)", borderRadius: 8, fontSize: 14, outline: "none", letterSpacing: 2 }}
              />
              <button
                onClick={() => enrollGuard.mutate({ id: editing.id, pin: enrollPin })}
                disabled={enrollPin.length !== 6 || enrollGuard.isPending}
                style={{ padding: "9px 16px", background: enrollPin.length === 6 ? "var(--hf-accent)" : "var(--hf-surface-sunken)", color: enrollPin.length === 6 ? "var(--hf-text-on-solid)" : "var(--hf-text-faint)", border: "none", borderRadius: 8, fontSize: 13, fontWeight: 600, cursor: enrollPin.length === 6 ? "pointer" : "not-allowed" }}>
                {enrollGuard.isPending ? "Enrolling…" : enrollGuard.isSuccess && enrollPin === "" ? "Enrolled ✓" : "Set PIN"}
              </button>
            </div>
          </div>

          {canViewCpTier && (
            <div style={{ marginTop: 12, padding: 16, background: "var(--hf-surface-muted)", borderRadius: 10, border: "1px solid var(--hf-border)" }}>
              <div style={{ display: "flex", alignItems: "center", gap: 6, marginBottom: 2 }}>
                <ShieldCheck size={14} color="#7C3AED" />
                <div style={{ fontSize: 13, fontWeight: 700, color: "var(--hf-text-secondary)" }}>Close Protection Clearance</div>
              </div>
              <div style={{ fontSize: 11, color: "var(--hf-text-faint)", marginBottom: 10 }}>
                Current tier: <strong style={{ color: "var(--hf-text-secondary)" }}>{editing.cpVettingTier ?? "Not set"}</strong> — enforced automatically on every CP detail assignment attempt.
              </div>
              <div style={{ display: "flex", gap: 8, marginBottom: 8 }}>
                <select value={cpTierForm.tier} onChange={e => setCpTierForm(f => ({ ...f, tier: e.target.value }))}
                  style={{ flex: 1, padding: "9px 12px", border: "1.5px solid var(--hf-border)", borderRadius: 8, fontSize: 13, outline: "none", background: "var(--hf-surface)" }}>
                  <option value="STANDARD">Standard</option>
                  <option value="ENHANCED">Enhanced</option>
                  <option value="HIGH">High</option>
                  <option value="CRITICAL">Critical</option>
                </select>
                <input type="date" value={cpTierForm.clearedAt} onChange={e => setCpTierForm(f => ({ ...f, clearedAt: e.target.value }))}
                  style={{ padding: "9px 12px", border: "1.5px solid var(--hf-border)", borderRadius: 8, fontSize: 13, outline: "none" }} />
              </div>
              <div style={{ display: "flex", gap: 8 }}>
                <input type="date" value={cpTierForm.expiresAt} onChange={e => setCpTierForm(f => ({ ...f, expiresAt: e.target.value }))}
                  placeholder="Expires (optional)"
                  style={{ flex: 1, padding: "9px 12px", border: "1.5px solid var(--hf-border)", borderRadius: 8, fontSize: 13, outline: "none" }} />
                <button
                  onClick={() => setCpVettingTier.mutate({ id: editing.id, body: { tier: cpTierForm.tier, clearedAt: cpTierForm.clearedAt, expiresAt: cpTierForm.expiresAt || null } })}
                  disabled={setCpVettingTier.isPending}
                  style={{ padding: "9px 16px", background: "var(--hf-violet)", color: "var(--hf-text-on-solid)", border: "none", borderRadius: 8, fontSize: 13, fontWeight: 600, cursor: "pointer", whiteSpace: "nowrap" as const }}>
                  {setCpVettingTier.isPending ? "Saving…" : "Set Tier"}
                </button>
              </div>
            </div>
          )}

          {/* FIX: closes the confirmed "no structured banking fields" gap —
              payroll export needed manual cross-referencing without these.
              Visible to any user who can already edit a guard (SECURITY_MANAGE)
              — unlike the CP tier section above, this doesn't need the
              special VIP_DETAIL_ACCESS gate. */}
          <div style={{ marginTop: 12, padding: 16, background: "var(--hf-surface-muted)", borderRadius: 10, border: "1px solid var(--hf-border)" }}>
            <div style={{ fontSize: 13, fontWeight: 700, color: "var(--hf-text-secondary)", marginBottom: 10 }}>Banking Details (for payroll)</div>
            <div style={{ display: "flex", gap: 8, marginBottom: 8 }}>
              <input value={bankForm.bankName} onChange={e => setBankForm(f => ({ ...f, bankName: e.target.value }))}
                placeholder="Bank name" style={{ flex: 1, padding: "9px 12px", border: "1.5px solid var(--hf-border)", borderRadius: 8, fontSize: 13, outline: "none" }} />
              <input value={bankForm.bankBranchCode} onChange={e => setBankForm(f => ({ ...f, bankBranchCode: e.target.value }))}
                placeholder="Branch code" style={{ width: 120, padding: "9px 12px", border: "1.5px solid var(--hf-border)", borderRadius: 8, fontSize: 13, outline: "none" }} />
            </div>
            <div style={{ display: "flex", gap: 8 }}>
              <input value={bankForm.bankAccountNumber} onChange={e => setBankForm(f => ({ ...f, bankAccountNumber: e.target.value }))}
                placeholder="Account number" style={{ flex: 1, padding: "9px 12px", border: "1.5px solid var(--hf-border)", borderRadius: 8, fontSize: 13, outline: "none" }} />
              <button
                onClick={() => updateBankDetails.mutate({ id: editing.id, body: bankForm })}
                disabled={updateBankDetails.isPending}
                style={{ padding: "9px 16px", background: "var(--hf-primary)", color: "var(--hf-text-on-solid)", border: "none", borderRadius: 8, fontSize: 13, fontWeight: 600, cursor: "pointer", whiteSpace: "nowrap" as const }}>
                {updateBankDetails.isPending ? "Saving…" : "Save Bank Details"}
              </button>
            </div>
          </div>
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
              <div style={{ position: "absolute", bottom: 4, right: 4, width: 28, height: 28, borderRadius: "50%", background: STATUS_MAP[viewing.status ?? "ACTIVE"]?.color ?? "var(--hf-success-solid-strong)", border: "2px solid var(--hf-surface)", display: "flex", alignItems: "center", justifyContent: "center" }}>
                {(() => { const S = GUARD_STATUSES.find(s => s.value === (viewing.status ?? "ACTIVE")); const Icon = S?.icon ?? CheckCircle; return <Icon size={12} color="#fff" /> })()}
              </div>
            </div>
            <h3 style={{ margin: "12px 0 6px", fontSize: 20, fontWeight: 700, color: "var(--hf-text)" }}>{viewing.fullName}</h3>
            <div style={{ display: "flex", justifyContent: "center", gap: 8, flexWrap: "wrap" }}>
              <span style={{ background: `${GRADE_COLORS[viewing.grade]}18`, color: GRADE_COLORS[viewing.grade], padding: "3px 12px", borderRadius: 20, fontSize: 12, fontWeight: 700 }}>Grade {viewing.grade}</span>
              <StatusBadge status={viewing.status} />
            </div>
          </div>

          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 10 }}>
            {[
              { label: "Employee Code", value: viewing.employeeCode || "—" },
              { label: "PSiRA Number", value: viewing.psiraNumber || "—" },
              { label: "SA ID Number", value: viewing.idNumber || "—" },
              { label: "Phone",        value: viewing.phone || "—" },
              { label: "Active",       value: viewing.active ? "Yes" : "No" },
            ].map(f => (
              <div key={f.label} style={{ padding: "10px 14px", background: "var(--hf-surface-muted)", borderRadius: 8 }}>
                <div style={{ fontSize: 10, fontWeight: 700, color: "var(--hf-text-faint)", textTransform: "uppercase" as const, letterSpacing: "0.06em", marginBottom: 4 }}>{f.label}</div>
                <div style={{ fontSize: 14, fontWeight: 600, color: "var(--hf-text)" }}>{f.value}</div>
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
            <div style={{ marginTop: 10, padding: "10px 14px", background: "var(--hf-success-soft)", border: "1px solid var(--hf-success-border)", borderRadius: 8, fontSize: 13, color: "var(--hf-success-text-strong)", display: "flex", gap: 16 }}>
              <span>DOB: {r.dob}</span><span>{r.gender}</span>
            </div>
          ) : null })()}

          {/* Status history */}
          {viewing.statusNote && (
            <div style={{ marginTop: 10, padding: "10px 14px", background: "var(--hf-warning-soft)", border: "1px solid var(--hf-warning-border)", borderRadius: 8, fontSize: 13 }}>
              <div style={{ fontSize: 10, fontWeight: 700, color: "var(--hf-text-faint)", textTransform: "uppercase" as const, letterSpacing: "0.06em", marginBottom: 4 }}>
                Status note {viewing.statusChangedAt && `· ${fmtDate(viewing.statusChangedAt)}`}
              </div>
              <div style={{ color: "var(--hf-warning-text-deep)" }}>{viewing.statusNote}</div>
            </div>
          )}

          {viewing.notes && (
            <div style={{ marginTop: 10, padding: "10px 14px", background: "var(--hf-surface-muted)", border: "1px solid var(--hf-border)", borderRadius: 8, fontSize: 13, color: "var(--hf-text-secondary)" }}>
              <div style={{ fontSize: 10, fontWeight: 700, color: "var(--hf-text-faint)", textTransform: "uppercase" as const, letterSpacing: "0.06em", marginBottom: 3 }}>Notes</div>
              {viewing.notes}
            </div>
          )}

          <div style={{ display: "flex", gap: 8, marginTop: 20 }}>
            <button onClick={() => { setViewing(null); openEdit(viewing) }} style={{ flex: 1, padding: "10px", background: "var(--hf-primary)", color: "var(--hf-text-on-solid)", border: "none", borderRadius: 9, fontSize: 14, fontWeight: 600, cursor: "pointer" }}>Edit Guard</button>
            <button onClick={() => { setViewing(null); setChangingStatus(viewing); setNewStatus(viewing.status ?? "ACTIVE"); setStatusNote(""); setApiError("") }} style={{ flex: 1, padding: "10px", background: "var(--hf-warning-soft-strong)", color: "var(--hf-warning-text)", border: "1px solid var(--hf-warning-border)", borderRadius: 9, fontSize: 14, fontWeight: 600, cursor: "pointer" }}>Change Status</button>
            <button onClick={() => setViewing(null)} style={{ padding: "10px 16px", border: "1px solid var(--hf-border)", borderRadius: 9, background: "var(--hf-surface)", fontSize: 14, cursor: "pointer", color: "var(--hf-text-secondary)" }}>Close</button>
          </div>
        </Modal>
      )}

      {/* ── Change Status Modal ───────────────────────────────────────────── */}
      {changingStatus && (
        <Modal title="Update Guard Status" onClose={() => { setChangingStatus(null); setApiError("") }} width={460}>
          <div style={{ display: "flex", alignItems: "center", gap: 12, marginBottom: 20, padding: "12px 14px", background: "var(--hf-surface-muted)", borderRadius: 10 }}>
            <GuardAvatar guard={changingStatus} size={44} />
            <div>
              <div style={{ fontWeight: 700, color: "var(--hf-text)", marginBottom: 3 }}>{changingStatus.fullName}</div>
              <StatusBadge status={changingStatus.status} />
            </div>
          </div>

          <label style={lbl}>Select New Status</label>
          <div style={{ display: "flex", flexDirection: "column", gap: 8, marginBottom: 18 }}>
            {GUARD_STATUSES.map(s => {
              const Icon = s.icon; const sel = newStatus === s.value
              return (
                <button key={s.value} onClick={() => setNewStatus(s.value)}
                  style={{ display: "flex", alignItems: "center", gap: 12, padding: "12px 14px", border: `2px solid ${sel ? s.color : "#E2E8F0"}`, borderRadius: 9, cursor: "pointer", background: sel ? s.bg : "var(--hf-surface)", textAlign: "left" as const, width: "100%" }}>
                  <div style={{ width: 32, height: 32, borderRadius: "50%", background: `${s.color}18`, display: "flex", alignItems: "center", justifyContent: "center", flexShrink: 0 }}>
                    <Icon size={15} color={s.color} />
                  </div>
                  <div style={{ flex: 1 }}>
                    <div style={{ fontSize: 14, fontWeight: 600, color: sel ? s.color : "var(--hf-text)" }}>{s.label}</div>
                    <div style={{ fontSize: 12, color: "var(--hf-text-faint)" }}>{s.description}</div>
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
                style={{ width: "100%", padding: "9px 12px", border: "1.5px solid var(--hf-border)", borderRadius: 8, fontSize: 14, boxSizing: "border-box" as const, resize: "vertical" as const, outline: "none" }} />
            </div>
          )}

          {newStatus && newStatus !== "ACTIVE" && (
            <div style={{ marginBottom: 16, padding: "10px 14px", background: "var(--hf-warning-soft-strong)", border: "1px solid var(--hf-warning-border-strong)", borderRadius: 8, fontSize: 12, color: "var(--hf-warning-text-deep)", display: "flex", gap: 8 }}>
              <AlertTriangle size={14} style={{ flexShrink: 0, marginTop: 1 }} />
              Guards with this status will not appear as available when scheduling new shifts.
            </div>
          )}

          {apiError && <ErrBanner msg={apiError} />}

          <div style={{ display: "flex", gap: 10, marginTop: 8 }}>
            <button onClick={() => { setChangingStatus(null); setApiError("") }} style={{ flex: 1, padding: "10px", border: "1.5px solid var(--hf-border)", borderRadius: 9, background: "var(--hf-surface)", fontSize: 14, fontWeight: 600, cursor: "pointer", color: "var(--hf-text-secondary)" }}>Cancel</button>
            <button
              onClick={() => updateStatus.mutate({ id: changingStatus.id, status: newStatus, note: statusNote })}
              disabled={!newStatus || updateStatus.isPending || ((newStatus === "SUSPENDED" || newStatus === "TERMINATED") && !statusNote.trim())}
              style={{ flex: 1, padding: "10px", border: "none", borderRadius: 9, fontSize: 14, fontWeight: 700, cursor: "pointer",
                background: newStatus && !((newStatus === "SUSPENDED" || newStatus === "TERMINATED") && !statusNote.trim()) ? "var(--hf-primary)" : "var(--hf-surface-strong)",
                color: "var(--hf-text-on-solid)" }}>
              {updateStatus.isPending ? "Updating..." : "Update Status"}
            </button>
          </div>
        </Modal>
      )}

      {/* ── Delete Confirmation ───────────────────────────────────────────── */}
      {deleting && (
        <Modal title="" onClose={() => { setDeleting(null); setApiError("") }} width={400}>
          <div style={{ textAlign: "center" }}>
            <div style={{ width: 56, height: 56, borderRadius: "50%", background: "var(--hf-danger-soft)", border: "2px solid var(--hf-danger-border)", display: "flex", alignItems: "center", justifyContent: "center", margin: "0 auto 16px" }}>
              <Trash2 size={22} color="#DC2626" />
            </div>
            <h3 style={{ margin: "0 0 8px", fontSize: 17, fontWeight: 700 }}>Remove Guard?</h3>
            <div style={{ display: "inline-flex", alignItems: "center", gap: 8, background: "var(--hf-danger-soft)", border: "1px solid var(--hf-danger-border)", borderRadius: 40, padding: "6px 14px", marginBottom: 14 }}>
              <Shield size={13} color="#DC2626" /><span style={{ fontSize: 13, fontWeight: 600 }}>{deleting.fullName}</span>
            </div>
            <p style={{ fontSize: 13, color: "var(--hf-text-muted)", margin: "0 0 20px", lineHeight: 1.6 }}>
              Deactivates the guard record. Shift history and incident records are preserved.
            </p>
            {apiError && <ErrBanner msg={apiError} />}
            <div style={{ display: "flex", gap: 10 }}>
              <button onClick={() => { setDeleting(null); setApiError("") }} style={{ flex: 1, padding: "10px", border: "1.5px solid var(--hf-border)", borderRadius: 9, background: "var(--hf-surface)", fontSize: 14, fontWeight: 600, cursor: "pointer", color: "var(--hf-text-secondary)" }}>Keep Guard</button>
              <button onClick={() => deleteGuard.mutate(deleting.id)} disabled={deleteGuard.isPending}
                style={{ flex: 1, padding: "10px", border: "none", borderRadius: 9, background: "var(--hf-danger)", color: "var(--hf-text-on-solid)", fontSize: 14, fontWeight: 700, cursor: "pointer" }}>
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
      <div style={{ background: "var(--hf-surface)", borderRadius: 16, padding: 28, width, maxHeight: "92vh", overflowY: "auto", boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }}>
        {title && (
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 22 }}>
            <h3 style={{ margin: 0, fontSize: 17, fontWeight: 700, color: "var(--hf-text)" }}>{title}</h3>
            <button onClick={onClose} style={{ background: "none", border: "none", cursor: "pointer", color: "var(--hf-text-faint)", display: "flex" }}><X size={20} /></button>
          </div>
        )}
        {children}
      </div>
    </div>
  )
}

function Footer({ onCancel, onSubmit, loading, label }: { onCancel: () => void; onSubmit: () => void; loading: boolean; label: string }) {
  return (
    <div style={{ display: "flex", gap: 10, justifyContent: "flex-end", marginTop: 20 }}>
      <button onClick={onCancel} style={{ padding: "9px 18px", border: "1px solid var(--hf-border)", borderRadius: 9, background: "var(--hf-surface)", fontSize: 14, cursor: "pointer", color: "var(--hf-text-secondary)" }}>Cancel</button>
      <button onClick={onSubmit} disabled={loading} style={{ padding: "9px 22px", background: loading ? "var(--hf-text-faint)" : "var(--hf-primary)", color: "var(--hf-text-on-solid)", border: "none", borderRadius: 9, fontSize: 14, fontWeight: 700, cursor: loading ? "not-allowed" : "pointer" }}>
        {loading ? "Saving..." : label}
      </button>
    </div>
  )
}

const omit = (obj: Record<string, string>, key: string) => { const n = { ...obj }; delete n[key]; return n }
const lbl: React.CSSProperties = { display: "block", fontSize: 13, fontWeight: 600, color: "#374151", marginBottom: 5 }

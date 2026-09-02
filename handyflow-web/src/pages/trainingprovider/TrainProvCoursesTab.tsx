// src/pages/trainingprovider/TrainProvCoursesTab.tsx
//
// Accredited course catalogue CRUD — confirmed via TrainProvCourseController
// and TrainProvCourse.java: GET/POST /api/v1/training-provider/courses,
// PUT/{id}, POST/{id}/archive, POST/{id}/reactivate, DELETE/{id}
// (ADMIN-only server-side). UpsertCourseRequest(title, description,
// unitStandardNumber, nqfLevel, credits, durationDays, pricePerDelegate
// [NotNull, must be >= 0], certificationOffered, certificateValidityMonths).
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { Plus, X, Archive, RotateCcw, Trash2, GraduationCap, Award } from "lucide-react"
import { apiClient } from "../../api/client"
import { TRAINPROV_ACCENT } from "./constants"

export interface CourseResponse {
  id: string; courseCode: string; title: string; description: string | null
  unitStandardNumber: string | null; nqfLevel: number | null; credits: number | null
  durationDays: number | null; pricePerDelegate: number
  certificationOffered: boolean; certificateValidityMonths: number | null
  status: "ACTIVE" | "ARCHIVED"; createdAt: string
}
interface CoursePage { content: CourseResponse[]; totalElements: number }

const inputStyle: React.CSSProperties = { width: "100%", padding: "9px 12px", border: "1px solid #E2E8F0", borderRadius: 8, fontSize: 13, boxSizing: "border-box", fontFamily: "inherit" }
const labelStyle: React.CSSProperties = { fontSize: 12, fontWeight: 600, color: "#374151", marginBottom: 5, display: "block" }
const fmtMoney = (n: number | null) => n == null ? "—" : new Intl.NumberFormat("en-ZA", { style: "currency", currency: "ZAR" }).format(n)

const emptyForm = {
  title: "", description: "", unitStandardNumber: "", nqfLevel: "", credits: "",
  durationDays: "", pricePerDelegate: "", certificationOffered: false, certificateValidityMonths: "",
}

function CourseFormModal({ initial, onClose }: { initial?: CourseResponse; onClose: () => void }) {
  const qc = useQueryClient()
  const [form, setForm] = useState(() => initial ? {
    title: initial.title, description: initial.description ?? "", unitStandardNumber: initial.unitStandardNumber ?? "",
    nqfLevel: initial.nqfLevel?.toString() ?? "", credits: initial.credits?.toString() ?? "",
    durationDays: initial.durationDays?.toString() ?? "", pricePerDelegate: initial.pricePerDelegate?.toString() ?? "",
    certificationOffered: initial.certificationOffered, certificateValidityMonths: initial.certificateValidityMonths?.toString() ?? "",
  } : emptyForm)

  const toNum = (v: string) => v.trim() === "" ? null : parseFloat(v)
  const toInt = (v: string) => v.trim() === "" ? null : parseInt(v, 10)

  const save = useMutation({
    mutationFn: async () => {
      const body = {
        title: form.title, description: form.description || null, unitStandardNumber: form.unitStandardNumber || null,
        nqfLevel: toInt(form.nqfLevel), credits: toInt(form.credits), durationDays: toNum(form.durationDays),
        pricePerDelegate: toNum(form.pricePerDelegate) ?? 0,
        certificationOffered: form.certificationOffered, certificateValidityMonths: toInt(form.certificateValidityMonths),
      }
      return initial
        ? apiClient.put(`/api/v1/training-provider/courses/${initial.id}`, body)
        : apiClient.post("/api/v1/training-provider/courses", body)
    },
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["trainprov-courses"] }); onClose() },
  })

  const valid = form.title && form.pricePerDelegate.trim() !== "" && parseFloat(form.pricePerDelegate) >= 0

  return (
    <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.4)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 50 }}>
      <div style={{ background: "#fff", borderRadius: 16, padding: 28, width: 520, maxHeight: "85vh", overflowY: "auto" }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 18 }}>
          <p style={{ fontSize: 15, fontWeight: 800, color: "#0F172A", margin: 0 }}>{initial ? "Edit course" : "Add a course"}</p>
          <button onClick={onClose} style={{ background: "none", border: "none", cursor: "pointer" }}><X size={18} color="#94A3B8" /></button>
        </div>
        <div style={{ display: "grid", gap: 12 }}>
          <div><label style={labelStyle}>Title *</label><input style={inputStyle} value={form.title} onChange={e => setForm({ ...form, title: e.target.value })} placeholder="Occupational Health & Safety Level 2" /></div>
          <div><label style={labelStyle}>Description</label><textarea style={{ ...inputStyle, minHeight: 60, resize: "vertical" }} value={form.description} onChange={e => setForm({ ...form, description: e.target.value })} /></div>
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
            <div><label style={labelStyle}>Unit standard number</label><input style={inputStyle} value={form.unitStandardNumber} onChange={e => setForm({ ...form, unitStandardNumber: e.target.value })} placeholder="Blank if unaccredited" /></div>
            <div><label style={labelStyle}>NQF level</label><input type="number" style={inputStyle} value={form.nqfLevel} onChange={e => setForm({ ...form, nqfLevel: e.target.value })} /></div>
          </div>
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
            <div><label style={labelStyle}>Credits</label><input type="number" style={inputStyle} value={form.credits} onChange={e => setForm({ ...form, credits: e.target.value })} /></div>
            <div><label style={labelStyle}>Duration (days)</label><input type="number" step="0.5" style={inputStyle} value={form.durationDays} onChange={e => setForm({ ...form, durationDays: e.target.value })} /></div>
          </div>
          <div><label style={labelStyle}>Price per delegate (ex VAT) *</label><input type="number" step="0.01" min="0" style={inputStyle} value={form.pricePerDelegate} onChange={e => setForm({ ...form, pricePerDelegate: e.target.value })} /></div>
          <label style={{ display: "flex", alignItems: "center", gap: 8, fontSize: 13, color: "#374151" }}>
            <input type="checkbox" checked={form.certificationOffered} onChange={e => setForm({ ...form, certificationOffered: e.target.checked })} />
            Offers a certificate on completion
          </label>
          {form.certificationOffered && (
            <div><label style={labelStyle}>Certificate validity (months — blank = never expires)</label><input type="number" style={inputStyle} value={form.certificateValidityMonths} onChange={e => setForm({ ...form, certificateValidityMonths: e.target.value })} /></div>
          )}
        </div>

        {save.isError && <p style={{ color: "#DC2626", fontSize: 12, marginTop: 12 }}>{(save.error as any)?.response?.data?.message ?? "Could not save this course"}</p>}

        <button onClick={() => save.mutate()} disabled={!valid || save.isPending}
          style={{ marginTop: 18, width: "100%", padding: "11px", borderRadius: 8, border: "none", background: TRAINPROV_ACCENT, color: "#fff", fontSize: 13.5, fontWeight: 700, cursor: "pointer", opacity: (!valid || save.isPending) ? 0.6 : 1 }}>
          {save.isPending ? "Saving…" : initial ? "Save changes" : "Add course"}
        </button>
      </div>
    </div>
  )
}

export default function TrainProvCoursesTab() {
  const qc = useQueryClient()
  const [showForm, setShowForm] = useState(false)
  const [editing, setEditing] = useState<CourseResponse | null>(null)
  const [statusFilter, setStatusFilter] = useState<string>("ACTIVE")

  const { data, isLoading } = useQuery<CoursePage>({
    queryKey: ["trainprov-courses", statusFilter],
    queryFn: async () => (await apiClient.get("/api/v1/training-provider/courses", { params: { status: statusFilter || undefined, size: 100 } })).data,
  })
  const courses = data?.content ?? []

  const archive = useMutation({
    mutationFn: async (id: string) => apiClient.post(`/api/v1/training-provider/courses/${id}/archive`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["trainprov-courses"] }),
  })
  const reactivate = useMutation({
    mutationFn: async (id: string) => apiClient.post(`/api/v1/training-provider/courses/${id}/reactivate`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["trainprov-courses"] }),
  })
  // ADMIN-only server-side (TRAININGPROVIDER_ADMIN) — real gate is the backend @PreAuthorize,
  // same note as every other module built in this engagement.
  const remove = useMutation({
    mutationFn: async (id: string) => apiClient.delete(`/api/v1/training-provider/courses/${id}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["trainprov-courses"] }),
  })

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 16 }}>
        <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
          <p style={{ fontSize: 13, color: "#94A3B8", margin: 0 }}>{courses.length} course{courses.length === 1 ? "" : "s"}</p>
          <select value={statusFilter} onChange={e => setStatusFilter(e.target.value)} style={{ ...inputStyle, width: "auto", padding: "5px 10px", fontSize: 12 }}>
            <option value="ACTIVE">Active</option>
            <option value="ARCHIVED">Archived</option>
            <option value="">All</option>
          </select>
        </div>
        <button onClick={() => setShowForm(true)}
          style={{ display: "flex", alignItems: "center", gap: 6, background: TRAINPROV_ACCENT, color: "#fff", border: "none", borderRadius: 8, padding: "9px 16px", fontSize: 13, fontWeight: 600, cursor: "pointer" }}>
          <Plus size={15} /> Add course
        </button>
      </div>

      {isLoading ? (
        <p style={{ color: "#94A3B8", fontSize: 13 }}>Loading…</p>
      ) : courses.length === 0 ? (
        <p style={{ color: "#94A3B8", fontSize: 13 }}>No courses yet.</p>
      ) : (
        <div style={{ border: "1px solid #E2E8F0", borderRadius: 12, overflow: "hidden" }}>
          {courses.map((c, i) => (
            <div key={c.id} style={{ display: "flex", alignItems: "center", justifyContent: "space-between", padding: "13px 16px", borderTop: i === 0 ? "none" : "1px solid #F1F5F9" }}>
              <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
                <div style={{ width: 32, height: 32, borderRadius: 8, background: "#FFFBEB", display: "flex", alignItems: "center", justifyContent: "center" }}>
                  <GraduationCap size={15} color={TRAINPROV_ACCENT} />
                </div>
                <div>
                  <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                    <p style={{ fontSize: 13.5, fontWeight: 700, color: "#0F172A", margin: 0 }}>{c.title}</p>
                    <span style={{ fontSize: 10, fontWeight: 700, color: "#94A3B8" }}>{c.courseCode}</span>
                    {c.certificationOffered && <Award size={13} color="#D97706" />}
                    {c.status === "ARCHIVED" && <span style={{ fontSize: 10, fontWeight: 700, padding: "2px 8px", borderRadius: 20, background: "#F1F5F9", color: "#64748B" }}>ARCHIVED</span>}
                  </div>
                  <p style={{ fontSize: 11.5, color: "#94A3B8", margin: 0 }}>
                    {c.unitStandardNumber ? `US ${c.unitStandardNumber}` : "Unaccredited"}{c.nqfLevel ? ` · NQF ${c.nqfLevel}` : ""}{c.credits ? ` · ${c.credits} credits` : ""} · {fmtMoney(c.pricePerDelegate)}/delegate
                  </p>
                </div>
              </div>
              <div style={{ display: "flex", alignItems: "center", gap: 4 }}>
                <button onClick={() => setEditing(c)} title="Edit"
                  style={{ background: "none", border: "1px solid #E2E8F0", borderRadius: 7, padding: "6px 10px", fontSize: 11.5, fontWeight: 600, color: "#64748B", cursor: "pointer" }}>
                  Edit
                </button>
                {c.status === "ACTIVE" ? (
                  <button onClick={() => archive.mutate(c.id)} title="Archive" style={{ background: "none", border: "1px solid #E2E8F0", borderRadius: 7, padding: 6, cursor: "pointer" }}>
                    <Archive size={13} color="#94A3B8" />
                  </button>
                ) : (
                  <button onClick={() => reactivate.mutate(c.id)} title="Reactivate" style={{ background: "none", border: "1px solid #E2E8F0", borderRadius: 7, padding: 6, cursor: "pointer" }}>
                    <RotateCcw size={13} color="#059669" />
                  </button>
                )}
                <button onClick={() => { if (confirm(`Delete ${c.title}? This cannot be undone.`)) remove.mutate(c.id) }} title="Delete"
                  style={{ background: "none", border: "1px solid #E2E8F0", borderRadius: 7, padding: 6, cursor: "pointer" }}>
                  <Trash2 size={13} color="#DC2626" />
                </button>
              </div>
            </div>
          ))}
        </div>
      )}

      {showForm && <CourseFormModal onClose={() => setShowForm(false)} />}
      {editing && <CourseFormModal initial={editing} onClose={() => setEditing(null)} />}
    </div>
  )
}

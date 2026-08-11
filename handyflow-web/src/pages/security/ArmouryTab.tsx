// src/pages/security/ArmouryTab.tsx
//
// CHANGE: added a "History" button per firearm, downloading the chain-of-
// custody PDF (GET /armoury/{id}/history/pdf) built earlier this session --
// the backend endpoint existed with no frontend button calling it at all.
// Everything else in this file is unchanged from the original.

import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { Crosshair, Plus, ArrowRight, ArrowLeft, AlertTriangle, Clock, FileText } from "lucide-react"

// ── Types ──────────────────────────────────────────────────────────────────────

interface Firearm {
  id: string
  firearmSerial: string
  firearmType: string
  makeModel: string | null
  sapsLicenseNumber: string
  licenseExpiry: string
  licenseExpired: boolean
  assignedGuardId: string | null
  assignedGuardName: string | null
  status: "IN_ARMOURY" | "ISSUED" | "LOST" | "DECOMMISSIONED"
  lastServiceAt: string | null
  nextServiceDueAt: string | null
  notes: string | null
  createdAt: string
}

// ── Config ─────────────────────────────────────────────────────────────────────

const STATUS_CONFIG = {
  IN_ARMOURY:     { label: "In Armoury",     color: "#166534", bg: "#DCFCE7" },
  ISSUED:         { label: "Issued",          color: "#1D4ED8", bg: "#EFF6FF" },
  LOST:           { label: "Lost",            color: "#991B1B", bg: "#FEF2F2" },
  DECOMMISSIONED: { label: "Decommissioned",  color: "#94A3B8", bg: "#F1F5F9" },
}

const fmtDate = (d: string | null) => d ? new Date(d).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric" }) : "—"

async function openPdfInNewTab(url: string) {
  const res = await apiClient.get(url, { responseType: "blob" })
  const blobUrl = URL.createObjectURL(new Blob([res.data], { type: "application/pdf" }))
  window.open(blobUrl, "_blank")
}

// ── Component ──────────────────────────────────────────────────────────────────

export default function ArmouryTab() {
  const qc = useQueryClient()
  const [view,     setView]     = useState<"list" | "register" | "issue" | "return">("list")
  const [selected, setSelected] = useState<Firearm | null>(null)
  const [form,     setForm]     = useState({ firearmSerial: "", firearmType: "", makeModel: "", sapsLicenseNumber: "", licenseExpiry: "", notes: "" })
  const [issueForm, setIssueForm] = useState({ guardId: "", witnessedByGuardId: "", conditionNotes: "" })
  const [returnForm, setReturnForm] = useState({ witnessedByGuardId: "", conditionNotes: "" })
  const [apiError, setApiError] = useState("")
  const [loadingHistoryId, setLoadingHistoryId] = useState<string | null>(null)

  const { data: firearms = [], isLoading } = useQuery<Firearm[]>({
    queryKey: ["armoury"],
    queryFn: async () => {
      const r = await apiClient.get("/api/v1/security/armoury?size=100")
      const p = r.data?.data ?? r.data
      return (p?.content ?? p) as Firearm[]
    },
  })

  const { data: guards = [] } = useQuery<{ id: string; fullName: string }[]>({
    queryKey: ["guards-list"],
    queryFn: async () => {
      const r = await apiClient.get("/api/v1/security/guards?size=100")
      const p = r.data?.data ?? r.data
      return (p?.content ?? p) as any[]
    },
  })

  const register = useMutation({
    mutationFn: (body: any) => apiClient.post("/api/v1/security/armoury", body),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["armoury"] }); setView("list") },
    onError: (e: any) => setApiError(e.response?.data?.message ?? "Registration failed"),
  })

  const issue = useMutation({
    mutationFn: ({ id, body }: any) => apiClient.post(`/api/v1/security/armoury/${id}/issue`, body),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["armoury"] }); setView("list"); setSelected(null) },
    onError: (e: any) => setApiError(e.response?.data?.message ?? "Issue failed"),
  })

  const returnFirearm = useMutation({
    mutationFn: ({ id, body }: any) => apiClient.post(`/api/v1/security/armoury/${id}/return`, body),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["armoury"] }); setView("list"); setSelected(null) },
    onError: (e: any) => setApiError(e.response?.data?.message ?? "Return failed"),
  })

  const downloadHistory = useMutation({
    mutationFn: async (id: string) => {
      setLoadingHistoryId(id)
      await openPdfInNewTab(`/api/v1/security/armoury/${id}/history/pdf`)
    },
    onSettled: () => setLoadingHistoryId(null),
    onError: () => setApiError("Failed to generate chain-of-custody PDF"),
  })

  const expiring = firearms.filter(f => {
    const days = Math.ceil((new Date(f.licenseExpiry).getTime() - Date.now()) / 86400000)
    return days <= 30 && f.status !== "DECOMMISSIONED"
  })

  if (view === "register") return (
    <div style={{ maxWidth: 480 }}>
      <h3 style={{ margin: "0 0 20px", fontSize: 14, fontWeight: 700 }}>Register Firearm</h3>
      {apiError && <p style={{ color: "#DC2626", fontSize: 12, marginBottom: 12 }}>{apiError}</p>}
      {[
        { key: "firearmSerial",    label: "Serial Number *" },
        { key: "firearmType",      label: "Firearm Type *" },
        { key: "makeModel",        label: "Make & Model" },
        { key: "sapsLicenseNumber",label: "SAPS License Number *" },
      ].map(f => (
        <div key={f.key} style={{ marginBottom: 14 }}>
          <label style={{ display: "block", fontSize: 11, fontWeight: 600, color: "#374151", marginBottom: 4 }}>{f.label}</label>
          <input value={(form as any)[f.key]} onChange={e => setForm(p => ({ ...p, [f.key]: e.target.value }))}
            style={inputStyle} />
        </div>
      ))}
      <div style={{ marginBottom: 14 }}>
        <label style={{ display: "block", fontSize: 11, fontWeight: 600, color: "#374151", marginBottom: 4 }}>License Expiry *</label>
        <input type="date" value={form.licenseExpiry} onChange={e => setForm(p => ({ ...p, licenseExpiry: e.target.value }))}
          style={inputStyle} />
      </div>
      <div style={{ display: "flex", gap: 10, marginTop: 20 }}>
        <button onClick={() => setView("list")} style={secondaryBtn}>Cancel</button>
        <button onClick={() => register.mutate(form)} style={primaryBtn}>Register</button>
      </div>
    </div>
  )

  if (view === "issue" && selected) return (
    <div style={{ maxWidth: 480 }}>
      <h3 style={{ margin: "0 0 4px", fontSize: 14, fontWeight: 700 }}>Issue Firearm</h3>
      <p style={{ margin: "0 0 20px", fontSize: 12, color: "#64748B" }}>{selected.firearmSerial} — {selected.firearmType}</p>
      {apiError && <p style={{ color: "#DC2626", fontSize: 12, marginBottom: 12 }}>{apiError}</p>}
      <div style={{ marginBottom: 14 }}>
        <label style={{ display: "block", fontSize: 11, fontWeight: 600, color: "#374151", marginBottom: 4 }}>Receiving Guard *</label>
        <select value={issueForm.guardId} onChange={e => setIssueForm(p => ({ ...p, guardId: e.target.value }))} style={inputStyle}>
          <option value="">Select guard…</option>
          {guards.map((g: any) => <option key={g.id} value={g.id}>{g.fullName}</option>)}
        </select>
      </div>
      <div style={{ marginBottom: 14 }}>
        <label style={{ display: "block", fontSize: 11, fontWeight: 600, color: "#374151", marginBottom: 4 }}>Witness (different guard, required) *</label>
        <select value={issueForm.witnessedByGuardId} onChange={e => setIssueForm(p => ({ ...p, witnessedByGuardId: e.target.value }))} style={inputStyle}>
          <option value="">Select witness…</option>
          {guards.filter((g: any) => g.id !== issueForm.guardId).map((g: any) => <option key={g.id} value={g.id}>{g.fullName}</option>)}
        </select>
      </div>
      <div style={{ marginBottom: 14 }}>
        <label style={{ display: "block", fontSize: 11, fontWeight: 600, color: "#374151", marginBottom: 4 }}>Condition Notes</label>
        <input value={issueForm.conditionNotes} onChange={e => setIssueForm(p => ({ ...p, conditionNotes: e.target.value }))} style={inputStyle} />
      </div>
      <div style={{ display: "flex", gap: 10 }}>
        <button onClick={() => setView("list")} style={secondaryBtn}>Cancel</button>
        <button onClick={() => issue.mutate({ id: selected.id, body: issueForm })} style={primaryBtn}>Issue Firearm</button>
      </div>
    </div>
  )

  if (view === "return" && selected) return (
    <div style={{ maxWidth: 480 }}>
      <h3 style={{ margin: "0 0 4px", fontSize: 14, fontWeight: 700 }}>Return Firearm</h3>
      <p style={{ margin: "0 0 20px", fontSize: 12, color: "#64748B" }}>
        {selected.firearmSerial} · Held by {selected.assignedGuardName}
      </p>
      {apiError && <p style={{ color: "#DC2626", fontSize: 12, marginBottom: 12 }}>{apiError}</p>}
      <div style={{ marginBottom: 14 }}>
        <label style={{ display: "block", fontSize: 11, fontWeight: 600, color: "#374151", marginBottom: 4 }}>Witness (different guard, required) *</label>
        <select value={returnForm.witnessedByGuardId} onChange={e => setReturnForm(p => ({ ...p, witnessedByGuardId: e.target.value }))} style={inputStyle}>
          <option value="">Select witness…</option>
          {guards.filter((g: any) => g.id !== selected.assignedGuardId).map((g: any) => <option key={g.id} value={g.id}>{g.fullName}</option>)}
        </select>
      </div>
      <div style={{ marginBottom: 14 }}>
        <label style={{ display: "block", fontSize: 11, fontWeight: 600, color: "#374151", marginBottom: 4 }}>Condition Notes</label>
        <input value={returnForm.conditionNotes} onChange={e => setReturnForm(p => ({ ...p, conditionNotes: e.target.value }))} style={inputStyle} />
      </div>
      <div style={{ display: "flex", gap: 10 }}>
        <button onClick={() => setView("list")} style={secondaryBtn}>Cancel</button>
        <button onClick={() => returnFirearm.mutate({ id: selected.id, body: returnForm })} style={primaryBtn}>Return to Armoury</button>
      </div>
    </div>
  )

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 20 }}>
        <div>
          <h2 style={{ margin: 0, fontSize: 16, fontWeight: 700, color: "#0F172A" }}>Firearms Register</h2>
          <p style={{ margin: "2px 0 0", fontSize: 12, color: "#64748B" }}>{firearms.length} registered · {firearms.filter(f => f.status === "ISSUED").length} issued</p>
        </div>
        <button onClick={() => { setForm({ firearmSerial: "", firearmType: "", makeModel: "", sapsLicenseNumber: "", licenseExpiry: "", notes: "" }); setView("register") }}
          style={{ ...primaryBtn, width: "auto", display: "flex", alignItems: "center", gap: 6 }}>
          <Plus size={14} /> Register Firearm
        </button>
      </div>

      {expiring.length > 0 && (
        <div style={{ background: "#FEF3C7", border: "1px solid #FDE68A", borderRadius: 10, padding: "10px 14px", marginBottom: 16, display: "flex", gap: 8, alignItems: "center" }}>
          <AlertTriangle size={14} color="#D97706" />
          <span style={{ fontSize: 12, color: "#92400E" }}>
            <strong>{expiring.length}</strong> firearm{expiring.length !== 1 ? "s" : ""} with license expiring within 30 days
          </span>
        </div>
      )}

      {isLoading ? (
        <p style={{ color: "#94A3B8", fontSize: 13 }}>Loading register…</p>
      ) : firearms.length === 0 ? (
        <div style={{ textAlign: "center", padding: "48px 0", color: "#CBD5E1" }}>
          <Crosshair size={32} strokeWidth={1.5} style={{ display: "block", margin: "0 auto 8px" }} />
          <p style={{ margin: 0, fontWeight: 500 }}>No firearms registered</p>
        </div>
      ) : (
        <div style={{ display: "grid", gap: 8 }}>
          {firearms.map(f => {
            const sc = STATUS_CONFIG[f.status]
            const daysLeft = Math.ceil((new Date(f.licenseExpiry).getTime() - Date.now()) / 86400000)
            return (
              <div key={f.id} style={{ display: "flex", alignItems: "center", gap: 16, padding: "14px 16px", border: "1px solid #E2E8F0", borderRadius: 10, background: "#fff" }}>
                <div style={{ flex: 1 }}>
                  <div style={{ display: "flex", gap: 8, alignItems: "center", marginBottom: 3 }}>
                    <span style={{ fontWeight: 700, fontSize: 13, color: "#0F172A" }}>{f.firearmSerial}</span>
                    <span style={{ fontSize: 10, fontWeight: 700, padding: "2px 8px", borderRadius: 4, color: sc.color, background: sc.bg }}>
                      {sc.label}
                    </span>
                    {f.licenseExpired && <span style={{ fontSize: 10, fontWeight: 700, padding: "2px 8px", borderRadius: 4, color: "#991B1B", background: "#FEF2F2" }}>LICENSE EXPIRED</span>}
                    {!f.licenseExpired && daysLeft <= 30 && <span style={{ fontSize: 10, fontWeight: 700, padding: "2px 8px", borderRadius: 4, color: "#92400E", background: "#FEF3C7" }}>{daysLeft}d left</span>}
                  </div>
                  <p style={{ margin: 0, fontSize: 11, color: "#64748B" }}>
                    {f.firearmType}{f.makeModel ? ` · ${f.makeModel}` : ""} · SAPS {f.sapsLicenseNumber}
                    {f.status === "ISSUED" && f.assignedGuardName && ` · Issued to ${f.assignedGuardName}`}
                    {" · "}License expires {fmtDate(f.licenseExpiry)}
                  </p>
                </div>
                <div style={{ display: "flex", gap: 8 }}>
                  <button onClick={() => downloadHistory.mutate(f.id)}
                    disabled={loadingHistoryId === f.id}
                    title="Download chain-of-custody PDF"
                    style={{ display: "flex", alignItems: "center", gap: 5, padding: "6px 12px", borderRadius: 7, border: "1px solid #E2E8F0", background: "#F8FAFC", color: "#374151", fontSize: 11, fontWeight: 600, cursor: "pointer" }}>
                    <FileText size={12} /> {loadingHistoryId === f.id ? "…" : "History"}
                  </button>
                  {f.status === "IN_ARMOURY" && (
                    <button onClick={() => { setSelected(f); setIssueForm({ guardId: "", witnessedByGuardId: "", conditionNotes: "" }); setApiError(""); setView("issue") }}
                      style={{ display: "flex", alignItems: "center", gap: 5, padding: "6px 12px", borderRadius: 7, border: "1px solid #1D4ED8", background: "#EFF6FF", color: "#1D4ED8", fontSize: 11, fontWeight: 600, cursor: "pointer" }}>
                      <ArrowRight size={12} /> Issue
                    </button>
                  )}
                  {f.status === "ISSUED" && (
                    <button onClick={() => { setSelected(f); setReturnForm({ witnessedByGuardId: "", conditionNotes: "" }); setApiError(""); setView("return") }}
                      style={{ display: "flex", alignItems: "center", gap: 5, padding: "6px 12px", borderRadius: 7, border: "1px solid #0D9488", background: "#F0FDFA", color: "#0D9488", fontSize: 11, fontWeight: 600, cursor: "pointer" }}>
                      <ArrowLeft size={12} /> Return
                    </button>
                  )}
                </div>
              </div>
            )
          })}
        </div>
      )}
    </div>
  )
}

const inputStyle = {
  width: "100%", padding: "9px 12px", border: "1px solid #E2E8F0",
  borderRadius: 8, fontSize: 13, background: "#fff", boxSizing: "border-box" as const,
} as const

const primaryBtn = {
  padding: "9px 18px", borderRadius: 8, border: "none",
  background: "#0D9488", color: "#fff", fontSize: 13, fontWeight: 600, cursor: "pointer",
} as const

const secondaryBtn = {
  padding: "9px 18px", borderRadius: 8, border: "1px solid #E2E8F0",
  background: "#fff", color: "#374151", fontSize: 13, fontWeight: 500, cursor: "pointer",
} as const

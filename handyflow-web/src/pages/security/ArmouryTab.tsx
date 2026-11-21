// src/pages/security/ArmouryTab.tsx
//
// CHANGE: added a "History" button per firearm, downloading the chain-of-
// custody PDF (GET /armoury/{id}/history/pdf) built earlier this session --
// the backend endpoint existed with no frontend button calling it at all.
// Everything else in this file is unchanged from the original.

import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { getAllGuards } from "./fetchAllPages"
import { useSecurityAccess } from "./useSecurityAccess"
import { Crosshair, Plus, ArrowRight, ArrowLeft, AlertTriangle, FileText, ShieldAlert } from "lucide-react"
import { ammoReturnProblem, ammoText, parseRounds, sapsLine } from "./armouryLogic"

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
  sapsCaseNumber?: string | null
  sapsReportDueAt?: string | null
  sapsReportOverdue?: boolean
  ammoRoundsIssued?: number | null
  ammoCalibre?: string | null
}

// ── Config ─────────────────────────────────────────────────────────────────────

const STATUS_CONFIG = {
  IN_ARMOURY:     { label: "In Armoury",     color: "var(--hf-success-text-strong)", bg: "var(--hf-success-soft-strong)" },
  ISSUED:         { label: "Issued",          color: "var(--hf-info-text)", bg: "var(--hf-info-soft)" },
  LOST:           { label: "Lost",            color: "var(--hf-danger-text-strong)", bg: "var(--hf-danger-soft)" },
  DECOMMISSIONED: { label: "Decommissioned",  color: "var(--hf-text-faint)", bg: "var(--hf-surface-sunken)" },
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
  const acc = useSecurityAccess()
  const [view,     setView]     = useState<"list" | "register" | "issue" | "return" | "lost" | "saps">("list")
  const [selected, setSelected] = useState<Firearm | null>(null)
  const [form,     setForm]     = useState({ firearmSerial: "", firearmType: "", makeModel: "", sapsLicenseNumber: "", licenseExpiry: "", notes: "" })
  const [issueForm, setIssueForm] = useState({ guardId: "", witnessedByGuardId: "", conditionNotes: "", ammoRounds: "", ammoCalibre: "" })
  const [lostForm, setLostForm] = useState({ notes: "", sapsCaseNumber: "" })
  const [sapsCase, setSapsCase] = useState("")
  const [returnForm, setReturnForm] = useState({ witnessedByGuardId: "", conditionNotes: "", ammoRoundsReturned: "", ammoUnaccountedReason: "" })
  const [apiError, setApiError] = useState("")
  const [loadingHistoryId, setLoadingHistoryId] = useState<string | null>(null)

  const { data: firearms = [], isLoading, dataUpdatedAt } = useQuery<Firearm[]>({
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
      const r = await getAllGuards()
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

  const reportLost = useMutation({
    mutationFn: ({ id, body }: { id: string; body: { notes: string; sapsCaseNumber?: string } }) => apiClient.post(`/api/v1/security/armoury/${id}/report-lost`, body),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["armoury"] }); setView("list"); setSelected(null) },
    onError: (e: { response?: { data?: { message?: string } } }) => setApiError(e.response?.data?.message ?? "Could not report the firearm lost"),
  })

  const recordSaps = useMutation({
    mutationFn: ({ id, caseNumber }: { id: string; caseNumber: string }) => apiClient.post(`/api/v1/security/armoury/${id}/saps-case`, { caseNumber }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["armoury"] }); setView("list"); setSelected(null) },
    onError: (e: { response?: { data?: { message?: string } } }) => setApiError(e.response?.data?.message ?? "Could not record the SAPS case number"),
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
      {apiError && <p style={{ color: "var(--hf-danger-text)", fontSize: 12, marginBottom: 12 }}>{apiError}</p>}
      {[
        { key: "firearmSerial",    label: "Serial Number *" },
        { key: "firearmType",      label: "Firearm Type *" },
        { key: "makeModel",        label: "Make & Model" },
        { key: "sapsLicenseNumber",label: "SAPS License Number *" },
      ].map(f => (
        <div key={f.key} style={{ marginBottom: 14 }}>
          <label style={{ display: "block", fontSize: 11, fontWeight: 600, color: "var(--hf-text-secondary)", marginBottom: 4 }}>{f.label}</label>
          <input value={(form as any)[f.key]} onChange={e => setForm(p => ({ ...p, [f.key]: e.target.value }))}
            style={inputStyle} />
        </div>
      ))}
      <div style={{ marginBottom: 14 }}>
        <label style={{ display: "block", fontSize: 11, fontWeight: 600, color: "var(--hf-text-secondary)", marginBottom: 4 }}>License Expiry *</label>
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
      <p style={{ margin: "0 0 20px", fontSize: 12, color: "var(--hf-text-muted)" }}>{selected.firearmSerial} — {selected.firearmType}</p>
      {apiError && <p style={{ color: "var(--hf-danger-text)", fontSize: 12, marginBottom: 12 }}>{apiError}</p>}
      <div style={{ marginBottom: 14 }}>
        <label style={{ display: "block", fontSize: 11, fontWeight: 600, color: "var(--hf-text-secondary)", marginBottom: 4 }}>Receiving Guard *</label>
        <select value={issueForm.guardId} onChange={e => setIssueForm(p => ({ ...p, guardId: e.target.value }))} style={inputStyle}>
          <option value="">Select guard…</option>
          {guards.map((g: any) => <option key={g.id} value={g.id}>{g.fullName}</option>)}
        </select>
      </div>
      <div style={{ marginBottom: 14 }}>
        <label style={{ display: "block", fontSize: 11, fontWeight: 600, color: "var(--hf-text-secondary)", marginBottom: 4 }}>Witness (different guard, required) *</label>
        <select value={issueForm.witnessedByGuardId} onChange={e => setIssueForm(p => ({ ...p, witnessedByGuardId: e.target.value }))} style={inputStyle}>
          <option value="">Select witness…</option>
          {guards.filter((g: any) => g.id !== issueForm.guardId).map((g: any) => <option key={g.id} value={g.id}>{g.fullName}</option>)}
        </select>
      </div>
      <div style={{ marginBottom: 14 }}>
        <label style={{ display: "block", fontSize: 11, fontWeight: 600, color: "var(--hf-text-secondary)", marginBottom: 4 }}>Condition Notes</label>
        <input value={issueForm.conditionNotes} onChange={e => setIssueForm(p => ({ ...p, conditionNotes: e.target.value }))} style={inputStyle} />
      </div>
      <div style={{ display: "flex", gap: 10, marginBottom: 14 }}>
        <div style={{ flex: 1 }}>
          <label style={{ display: "block", fontSize: 11, fontWeight: 600, color: "var(--hf-text-secondary)", marginBottom: 4 }}>Rounds issued</label>
          <input inputMode="numeric" aria-label="Rounds issued" value={issueForm.ammoRounds} onChange={e => setIssueForm(p => ({ ...p, ammoRounds: e.target.value }))} style={inputStyle} />
        </div>
        <div style={{ flex: 1 }}>
          <label style={{ display: "block", fontSize: 11, fontWeight: 600, color: "var(--hf-text-secondary)", marginBottom: 4 }}>Calibre</label>
          <input aria-label="Calibre" value={issueForm.ammoCalibre} onChange={e => setIssueForm(p => ({ ...p, ammoCalibre: e.target.value }))} style={inputStyle} />
        </div>
      </div>
      {issueForm.ammoRounds.trim() !== "" && parseRounds(issueForm.ammoRounds) === null && (
        <p style={{ color: "var(--hf-danger-text)", fontSize: 12, margin: "0 0 12px" }}>Rounds must be a whole number from 0 to 2000.</p>
      )}
      <div style={{ display: "flex", gap: 10 }}>
        <button onClick={() => setView("list")} style={secondaryBtn}>Cancel</button>
        <button
          disabled={issueForm.ammoRounds.trim() !== "" && parseRounds(issueForm.ammoRounds) === null}
          onClick={() => issue.mutate({ id: selected.id, body: {
            guardId: issueForm.guardId, witnessedByGuardId: issueForm.witnessedByGuardId, conditionNotes: issueForm.conditionNotes,
            ammoRounds: parseRounds(issueForm.ammoRounds) ?? undefined, ammoCalibre: issueForm.ammoCalibre.trim() || undefined } })}
          style={primaryBtn}>Issue Firearm</button>
      </div>
    </div>
  )

  if (view === "return" && selected) return (
    <div style={{ maxWidth: 480 }}>
      <h3 style={{ margin: "0 0 4px", fontSize: 14, fontWeight: 700 }}>Return Firearm</h3>
      <p style={{ margin: "0 0 20px", fontSize: 12, color: "var(--hf-text-muted)" }}>
        {selected.firearmSerial} · Held by {selected.assignedGuardName}
      </p>
      {apiError && <p style={{ color: "var(--hf-danger-text)", fontSize: 12, marginBottom: 12 }}>{apiError}</p>}
      <div style={{ marginBottom: 14 }}>
        <label style={{ display: "block", fontSize: 11, fontWeight: 600, color: "var(--hf-text-secondary)", marginBottom: 4 }}>Witness (different guard, required) *</label>
        <select value={returnForm.witnessedByGuardId} onChange={e => setReturnForm(p => ({ ...p, witnessedByGuardId: e.target.value }))} style={inputStyle}>
          <option value="">Select witness…</option>
          {guards.filter((g: any) => g.id !== selected.assignedGuardId).map((g: any) => <option key={g.id} value={g.id}>{g.fullName}</option>)}
        </select>
      </div>
      <div style={{ marginBottom: 14 }}>
        <label style={{ display: "block", fontSize: 11, fontWeight: 600, color: "var(--hf-text-secondary)", marginBottom: 4 }}>Condition Notes</label>
        <input value={returnForm.conditionNotes} onChange={e => setReturnForm(p => ({ ...p, conditionNotes: e.target.value }))} style={inputStyle} />
      </div>
      {(() => {
        const issued = selected.ammoRoundsIssued
        const problem = ammoReturnProblem(issued, returnForm.ammoRoundsReturned, returnForm.ammoUnaccountedReason)
        const short = issued != null && parseRounds(returnForm.ammoRoundsReturned) !== null && (parseRounds(returnForm.ammoRoundsReturned) as number) < issued
        return (
          <>
            <div style={{ marginBottom: 14 }}>
              <label style={{ display: "block", fontSize: 11, fontWeight: 600, color: "var(--hf-text-secondary)", marginBottom: 4 }}>
                Rounds handed back{issued != null ? ` (issued: ${ammoText(issued, selected.ammoCalibre)})` : ""}
              </label>
              <input inputMode="numeric" aria-label="Rounds handed back" value={returnForm.ammoRoundsReturned}
                onChange={e => setReturnForm(p => ({ ...p, ammoRoundsReturned: e.target.value }))} style={inputStyle} />
            </div>
            {short && (
              <div style={{ marginBottom: 14 }}>
                <label style={{ display: "block", fontSize: 11, fontWeight: 600, color: "var(--hf-text-secondary)", marginBottom: 4 }}>Explain the missing rounds *</label>
                <input aria-label="Explain the missing rounds" value={returnForm.ammoUnaccountedReason}
                  onChange={e => setReturnForm(p => ({ ...p, ammoUnaccountedReason: e.target.value }))} style={inputStyle} />
              </div>
            )}
            {problem && returnForm.ammoRoundsReturned !== "" && <p style={{ color: "var(--hf-danger-text)", fontSize: 12, margin: "0 0 12px" }}>{problem}</p>}
            <div style={{ display: "flex", gap: 10 }}>
              <button onClick={() => setView("list")} style={secondaryBtn}>Cancel</button>
              <button disabled={problem !== null}
                onClick={() => returnFirearm.mutate({ id: selected.id, body: {
                  witnessedByGuardId: returnForm.witnessedByGuardId, conditionNotes: returnForm.conditionNotes,
                  ammoRoundsReturned: parseRounds(returnForm.ammoRoundsReturned) ?? undefined,
                  ammoUnaccountedReason: short ? returnForm.ammoUnaccountedReason.trim() : undefined } })}
                style={primaryBtn}>Return to Armoury</button>
            </div>
          </>
        )
      })()}
    </div>
  )

  if (view === "lost" && selected) return (
    <div style={{ maxWidth: 480 }}>
      <h3 style={{ margin: "0 0 4px", fontSize: 14, fontWeight: 700 }}>Report Firearm Lost or Stolen</h3>
      <p style={{ margin: "0 0 12px", fontSize: 12, color: "var(--hf-text-muted)" }}>
        {selected.firearmSerial} — {selected.firearmType}{selected.assignedGuardName ? ` · Held by ${selected.assignedGuardName}` : ""}
      </p>
      <p style={{ margin: "0 0 16px", fontSize: 12, color: "var(--hf-warning-text-deep)" }}>
        A lost or stolen firearm must be reported to SAPS within 24 hours. The control room is alerted when you submit. The firearm cannot be issued again after this.
      </p>
      {apiError && <p role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 12, marginBottom: 12 }}>{apiError}</p>}
      <div style={{ marginBottom: 14 }}>
        <label style={{ display: "block", fontSize: 11, fontWeight: 600, color: "var(--hf-text-secondary)", marginBottom: 4 }}>What happened *</label>
        <textarea aria-label="What happened" value={lostForm.notes} onChange={e => setLostForm(p => ({ ...p, notes: e.target.value }))} rows={3} style={inputStyle} />
      </div>
      <div style={{ marginBottom: 14 }}>
        <label style={{ display: "block", fontSize: 11, fontWeight: 600, color: "var(--hf-text-secondary)", marginBottom: 4 }}>SAPS case number (if already reported)</label>
        <input aria-label="SAPS case number" value={lostForm.sapsCaseNumber} onChange={e => setLostForm(p => ({ ...p, sapsCaseNumber: e.target.value }))} style={inputStyle} />
      </div>
      <div style={{ display: "flex", gap: 10 }}>
        <button onClick={() => setView("list")} style={secondaryBtn}>Cancel</button>
        <button disabled={lostForm.notes.trim() === "" || reportLost.isPending}
          onClick={() => reportLost.mutate({ id: selected.id, body: { notes: lostForm.notes.trim(), sapsCaseNumber: lostForm.sapsCaseNumber.trim() || undefined } })}
          style={{ ...primaryBtn, background: "var(--hf-danger)" }}>Report lost</button>
      </div>
    </div>
  )

  if (view === "saps" && selected) return (
    <div style={{ maxWidth: 480 }}>
      <h3 style={{ margin: "0 0 4px", fontSize: 14, fontWeight: 700 }}>SAPS Case Number</h3>
      <p style={{ margin: "0 0 16px", fontSize: 12, color: "var(--hf-text-muted)" }}>{selected.firearmSerial} — {selected.firearmType}</p>
      {apiError && <p role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 12, marginBottom: 12 }}>{apiError}</p>}
      <div style={{ marginBottom: 14 }}>
        <label style={{ display: "block", fontSize: 11, fontWeight: 600, color: "var(--hf-text-secondary)", marginBottom: 4 }}>Case (CAS) number *</label>
        <input aria-label="Case number" value={sapsCase} onChange={e => setSapsCase(e.target.value)} style={inputStyle} />
      </div>
      <div style={{ display: "flex", gap: 10 }}>
        <button onClick={() => setView("list")} style={secondaryBtn}>Cancel</button>
        <button disabled={sapsCase.trim() === "" || recordSaps.isPending}
          onClick={() => recordSaps.mutate({ id: selected.id, caseNumber: sapsCase.trim() })} style={primaryBtn}>Save</button>
      </div>
    </div>
  )

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 20 }}>
        <div>
          <h2 style={{ margin: 0, fontSize: 16, fontWeight: 700, color: "var(--hf-text)" }}>Firearms Register</h2>
          <p style={{ margin: "2px 0 0", fontSize: 12, color: "var(--hf-text-muted)" }}>{firearms.length} registered · {firearms.filter(f => f.status === "ISSUED").length} issued</p>
        </div>
        <button onClick={() => { setForm({ firearmSerial: "", firearmType: "", makeModel: "", sapsLicenseNumber: "", licenseExpiry: "", notes: "" }); setView("register") }}
          style={{ ...primaryBtn, width: "auto", display: "flex", alignItems: "center", gap: 6 }}>
          <Plus size={14} /> Register Firearm
        </button>
      </div>

      {apiError && <p role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 12, marginBottom: 12 }}>{apiError}</p>}

      {expiring.length > 0 && (
        <div style={{ background: "var(--hf-warning-soft-strong)", border: "1px solid var(--hf-warning-border)", borderRadius: 10, padding: "10px 14px", marginBottom: 16, display: "flex", gap: 8, alignItems: "center" }}>
          <AlertTriangle size={14} style={{ color: 'var(--hf-warning-text)' }} />
          <span style={{ fontSize: 12, color: "var(--hf-warning-text-deep)" }}>
            <strong>{expiring.length}</strong> firearm{expiring.length !== 1 ? "s" : ""} with license expiring within 30 days
          </span>
        </div>
      )}

      {isLoading ? (
        <p style={{ color: "var(--hf-text-faint)", fontSize: 13 }}>Loading register…</p>
      ) : firearms.length === 0 ? (
        <div style={{ textAlign: "center", padding: "48px 0", color: "var(--hf-text-disabled)" }}>
          <Crosshair size={32} strokeWidth={1.5} style={{ display: "block", margin: "0 auto 8px" }} />
          <p style={{ margin: 0, fontWeight: 500 }}>No firearms registered</p>
        </div>
      ) : (
        <div style={{ display: "grid", gap: 8 }}>
          {firearms.map(f => {
            const sc = STATUS_CONFIG[f.status]
            const daysLeft = Math.ceil((new Date(f.licenseExpiry).getTime() - Date.now()) / 86400000)
            return (
              <div key={f.id} style={{ display: "flex", alignItems: "center", gap: 16, padding: "14px 16px", border: "1px solid var(--hf-border)", borderRadius: 10, background: "var(--hf-surface)" }}>
                <div style={{ flex: 1 }}>
                  <div style={{ display: "flex", gap: 8, alignItems: "center", marginBottom: 3 }}>
                    <span style={{ fontWeight: 700, fontSize: 13, color: "var(--hf-text)" }}>{f.firearmSerial}</span>
                    <span style={{ fontSize: 10, fontWeight: 700, padding: "2px 8px", borderRadius: 4, color: sc.color, background: sc.bg }}>
                      {sc.label}
                    </span>
                    {f.licenseExpired && <span style={{ fontSize: 10, fontWeight: 700, padding: "2px 8px", borderRadius: 4, color: "var(--hf-danger-text-strong)", background: "var(--hf-danger-soft)" }}>LICENSE EXPIRED</span>}
                    {!f.licenseExpired && daysLeft <= 30 && <span style={{ fontSize: 10, fontWeight: 700, padding: "2px 8px", borderRadius: 4, color: "var(--hf-warning-text-deep)", background: "var(--hf-warning-soft-strong)" }}>{daysLeft}d left</span>}
                  </div>
                  <p style={{ margin: 0, fontSize: 11, color: "var(--hf-text-muted)" }}>
                    {f.firearmType}{f.makeModel ? ` · ${f.makeModel}` : ""} · SAPS {f.sapsLicenseNumber}
                    {f.status === "ISSUED" && f.assignedGuardName && ` · Issued to ${f.assignedGuardName}`}
                    {" · "}License expires {fmtDate(f.licenseExpiry)}
                    {f.status === "ISSUED" && ammoText(f.ammoRoundsIssued, f.ammoCalibre) && ` · ${ammoText(f.ammoRoundsIssued, f.ammoCalibre)} out`}
                  </p>
                  {(() => {
                    const s = sapsLine(f, dataUpdatedAt)
                    return s ? <p style={{ margin: "3px 0 0", fontSize: 11, fontWeight: 600, color: s.urgent ? "var(--hf-danger-text-strong)" : "var(--hf-text-muted)" }}>{s.text}</p> : null
                  })()}
                </div>
                <div style={{ display: "flex", gap: 8 }}>
                  <button onClick={() => downloadHistory.mutate(f.id)}
                    disabled={loadingHistoryId === f.id}
                    title="Download chain-of-custody PDF"
                    style={{ display: "flex", alignItems: "center", gap: 5, padding: "6px 12px", borderRadius: 7, border: "1px solid var(--hf-border)", background: "var(--hf-surface-muted)", color: "var(--hf-text-secondary)", fontSize: 11, fontWeight: 600, cursor: "pointer" }}>
                    <FileText size={12} /> {loadingHistoryId === f.id ? "…" : "History"}
                  </button>
                  {f.status === "IN_ARMOURY" && acc.armouryIssue && (
                    <button onClick={() => { setSelected(f); setIssueForm({ guardId: "", witnessedByGuardId: "", conditionNotes: "", ammoRounds: "", ammoCalibre: "" }); setApiError(""); setView("issue") }}
                      style={{ display: "flex", alignItems: "center", gap: 5, padding: "6px 12px", borderRadius: 7, border: "1px solid var(--hf-info)", background: "var(--hf-info-soft)", color: "var(--hf-info-text)", fontSize: 11, fontWeight: 600, cursor: "pointer" }}>
                      <ArrowRight size={12} /> Issue
                    </button>
                  )}
                  {(f.status === "IN_ARMOURY" || f.status === "ISSUED") && (
                    <button onClick={() => { setSelected(f); setLostForm({ notes: "", sapsCaseNumber: "" }); setApiError(""); setView("lost") }}
                      title="Report this firearm lost or stolen"
                      style={{ display: "flex", alignItems: "center", gap: 5, padding: "6px 12px", borderRadius: 7, border: "1px solid var(--hf-danger-border, var(--hf-border))", background: "var(--hf-surface)", color: "var(--hf-danger-text-strong)", fontSize: 11, fontWeight: 600, cursor: "pointer" }}>
                      <ShieldAlert size={12} /> Report lost
                    </button>
                  )}
                  {f.status === "LOST" && !f.sapsCaseNumber && (
                    <button onClick={() => { setSelected(f); setSapsCase(""); setApiError(""); setView("saps") }}
                      style={{ display: "flex", alignItems: "center", gap: 5, padding: "6px 12px", borderRadius: 7, border: "1px solid var(--hf-border)", background: "var(--hf-surface-muted)", color: "var(--hf-text-secondary)", fontSize: 11, fontWeight: 600, cursor: "pointer" }}>
                      SAPS case
                    </button>
                  )}
                  {f.status === "ISSUED" && acc.armouryIssue && (
                    <button onClick={() => { setSelected(f); setReturnForm({ witnessedByGuardId: "", conditionNotes: "", ammoRoundsReturned: "", ammoUnaccountedReason: "" }); setApiError(""); setView("return") }}
                      style={{ display: "flex", alignItems: "center", gap: 5, padding: "6px 12px", borderRadius: 7, border: "1px solid var(--hf-accent)", background: "var(--hf-accent-soft)", color: "var(--hf-accent-text)", fontSize: 11, fontWeight: 600, cursor: "pointer" }}>
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
  width: "100%", padding: "9px 12px", border: "1px solid var(--hf-border)",
  borderRadius: 8, fontSize: 13, background: "var(--hf-surface)", boxSizing: "border-box" as const,
} as const

const primaryBtn = {
  padding: "9px 18px", borderRadius: 8, border: "none",
  background: "var(--hf-accent)", color: "var(--hf-text-on-solid)", fontSize: 13, fontWeight: 600, cursor: "pointer",
} as const

const secondaryBtn = {
  padding: "9px 18px", borderRadius: 8, border: "1px solid var(--hf-border)",
  background: "var(--hf-surface)", color: "var(--hf-text-secondary)", fontSize: 13, fontWeight: 500, cursor: "pointer",
} as const

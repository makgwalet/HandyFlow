import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import {
  Plus, X, ChevronDown, ChevronUp, ChevronRight,
  FileText, Download, Send, CheckCircle, Clock, User, AlertTriangle,
} from "lucide-react"

interface Party {
  id: string
  partyType: string
  partyRole: string
  fullName: string
  email: string
  phone: string
  companyName: string
  signingOrder: number
  signingStatus: string
  signedAt: string | null
  otpSentAt: string | null
}

interface Contract {
  id: string
  contractNumber: string
  title: string
  contractType: string
  status: string
  valueAmount: number
  currency: string
  startDate: string
  endDate: string
  autoRenew: boolean
  notes: string | null
  sentAt: string | null
  signedAt: string | null
  terminatedAt: string | null
  terminationReason: string | null
  parties: Party[]
}

interface Template { id: string; name: string; contractType: string; description: string }

const STATUS_STYLE: Record<string, { color: string; bg: string }> = {
  DRAFT:        { color: "#64748B", bg: "#F8FAFC" },
  UNDER_REVIEW: { color: "#D97706", bg: "#FFFBEB" },
  PENDING_SIGN: { color: "#1D4ED8", bg: "#EFF6FF" },
  SIGNED:       { color: "#166534", bg: "#DCFCE7" },
  TERMINATED:   { color: "#DC2626", bg: "#FEF2F2" },
  EXPIRED:      { color: "#94A3B8", bg: "#F1F5F9" },
}

const SIGN_STATUS_STYLE: Record<string, { color: string; icon: typeof CheckCircle }> = {
  PENDING: { color: "#D97706", icon: Clock },
  SIGNED:  { color: "#166534", icon: CheckCircle },
  DECLINED:{ color: "#DC2626", icon: AlertTriangle },
}

const CONTRACT_TYPES = [
  "SERVICE_AGREEMENT", "EMPLOYMENT", "NDA", "LEASE", "SUPPLY",
  "PARTNERSHIP", "MAINTENANCE", "CONSULTING", "RETAINER", "OTHER",
]

const nextActions = (status: string) => {
  if (status === "DRAFT")        return [{ label: "Submit for Review", action: "submit-for-review" }]
  if (status === "UNDER_REVIEW") return [{ label: "Send for Signing",  action: "send-for-signing"  }]
  if (status === "SIGNED")       return [{ label: "Terminate",         action: "terminate"          }]
  return []
}

export default function ContractsTab() {
  const qc = useQueryClient()
  const [statusFilter, setStatus] = useState("")
  const [expanded, setExpanded]   = useState<string | null>(null)
  const [showCreate, setShowCreate] = useState(false)
  const [showParty, setShowParty]   = useState<string | null>(null)
  const [showOtp, setShowOtp]       = useState<{ contractId: string; partyId: string; partyName: string } | null>(null)
  const [showSign, setShowSign]     = useState<{ contractId: string; partyId: string } | null>(null)
  const [showTerminate, setShowTerminate] = useState<string | null>(null)
  const [error, setError] = useState("")

  const initForm = () => ({
    title: "", contractType: "SERVICE_AGREEMENT", templateId: "",
    valueAmount: "", currency: "ZAR",
    startDate: "", endDate: "", autoRenew: false, notes: "",
  })
  const [form, setForm] = useState(initForm())
  const f = (k: keyof ReturnType<typeof initForm>, v: any) => setForm(p => ({ ...p, [k]: v }))

  const [partyForm, setPartyForm] = useState({
    partyType: "INDIVIDUAL", partyRole: "COUNTERPARTY",
    fullName: "", email: "", phone: "", companyName: "", signingOrder: "1",
  })

  const [otpCode, setOtpCode]           = useState("")
  const [terminateReason, setTerminateReason] = useState("")

  const { data: page, isLoading } = useQuery({
    queryKey: ["contracts", statusFilter],
    queryFn: async () => {
      const params = new URLSearchParams({ size: "50" })
      if (statusFilter) params.set("status", statusFilter)
      const r = await apiClient.get(`/api/v1/contracts?${params}`)
      return r.data
    },
  })

  const { data: templates = [] } = useQuery<Template[]>({
    queryKey: ["contract-templates"],
    queryFn: async () => {
      const r = await apiClient.get("/api/v1/contracts/templates")
      return r.data || []
    },
  })

  const createContract = useMutation({
    mutationFn: (body: any) => apiClient.post("/api/v1/contracts", body),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["contracts"] }); setShowCreate(false); setForm(initForm()) },
    onError: (e: any) => setError(e.response?.data?.message || "Failed to create contract"),
  })

  const contractAction = useMutation({
    mutationFn: ({ id, action, body }: { id: string; action: string; body?: any }) =>
      apiClient.post(`/api/v1/contracts/${id}/${action}`, body || {}),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["contracts"] }); setShowTerminate(null) },
    onError: (e: any) => setError(e.response?.data?.message || "Action failed"),
  })

  const addParty = useMutation({
    mutationFn: ({ id, body }: { id: string; body: any }) =>
      apiClient.post(`/api/v1/contracts/${id}/parties`, body),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["contracts"] }); setShowParty(null) },
    onError: (e: any) => setError(e.response?.data?.message || "Failed to add party"),
  })

  const requestOtp = useMutation({
    mutationFn: ({ contractId, partyId }: { contractId: string; partyId: string }) =>
      apiClient.post(`/api/v1/contracts/${contractId}/parties/${partyId}/request-otp`),
    onSuccess: (_, vars) => {
      qc.invalidateQueries({ queryKey: ["contracts"] })
      setShowSign({ contractId: vars.contractId, partyId: vars.partyId })
    },
    onError: (e: any) => setError(e.response?.data?.message || "Failed to send OTP"),
  })

  const signContract = useMutation({
    mutationFn: ({ contractId, partyId, otp }: { contractId: string; partyId: string; otp: string }) =>
      apiClient.post(`/api/v1/contracts/${contractId}/parties/${partyId}/sign`, { otp }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["contracts"] }); setShowSign(null); setOtpCode("") },
    onError: (e: any) => setError(e.response?.data?.message || "Signing failed — check OTP"),
  })

  const downloadPdf = (id: string, number: string) => {
    apiClient.get(`/api/v1/contracts/${id}/pdf`, { responseType: "blob" } as any)
      .then((res: any) => {
        const url = URL.createObjectURL(new Blob([res.data], { type: "application/pdf" }))
        const a = document.createElement("a"); a.href = url; a.download = `contract-${number}.pdf`
        a.click(); URL.revokeObjectURL(url)
      })
      .catch(() => alert("Failed to download PDF"))
  }

  const contracts: Contract[] = page?.content || []
  const fmtR = (n: number) => n ? `R ${Number(n).toLocaleString("en-ZA", { minimumFractionDigits: 2 })}` : "—"
  const fmtDate = (d: string) => d ? new Date(d + "T00:00:00").toLocaleDateString("en-ZA") : "—"
  const fmtDT   = (d: string) => d ? new Date(d).toLocaleString("en-ZA", { dateStyle: "medium", timeStyle: "short" }) : "—"

  return (
    <div>
      {/* Toolbar */}
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 16, gap: 10, flexWrap: "wrap" }}>
        <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
          {["", "DRAFT", "UNDER_REVIEW", "PENDING_SIGN", "SIGNED", "TERMINATED"].map(s => (
            <button key={s} onClick={() => setStatus(s)} style={filterBtn(statusFilter === s)}>
              {s ? s.replace("_", " ") : "All"}
            </button>
          ))}
        </div>
        <button onClick={() => { setShowCreate(true); setError("") }} style={btnPrimary}><Plus size={15} /> New Contract</button>
      </div>

      {/* Stats */}
      {contracts.length > 0 && (
        <div style={{ display: "flex", gap: 10, marginBottom: 20, flexWrap: "wrap" }}>
          {["DRAFT", "PENDING_SIGN", "SIGNED"].map(s => {
            const style = STATUS_STYLE[s] || { color: "#475569", bg: "#F8FAFC" }
            const count = contracts.filter(c => c.status === s).length
            return count > 0 ? (
              <div key={s} style={{ background: style.bg, borderRadius: 8, padding: "8px 14px" }}>
                <span style={{ fontSize: 18, fontWeight: 700, color: style.color }}>{count}</span>
                <span style={{ fontSize: 11, color: style.color, marginLeft: 6 }}>{s.replace("_", " ")}</span>
              </div>
            ) : null
          })}
        </div>
      )}

      {isLoading ? (
        <div style={{ textAlign: "center", padding: 40, color: "#94A3B8" }}>Loading contracts...</div>
      ) : contracts.length === 0 ? (
        <div style={{ textAlign: "center", padding: "60px 20px", color: "#94A3B8" }}>
          <FileText size={40} style={{ marginBottom: 12, opacity: 0.4 }} />
          <div style={{ fontWeight: 600, color: "#475569" }}>No contracts yet</div>
          <div style={{ fontSize: 14, marginTop: 4 }}>Create your first contract to get started.</div>
        </div>
      ) : (
        <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
          {contracts.map(c => {
            const style = STATUS_STYLE[c.status] || { color: "#475569", bg: "#F8FAFC" }
            const isOpen = expanded === c.id
            const allSigned = c.parties?.length > 0 && c.parties.every(p => p.signingStatus === "SIGNED")

            return (
              <div key={c.id} style={{ border: "1px solid #E2E8F0", borderRadius: 10, overflow: "hidden" }}>
                {/* Contract row */}
                <div onClick={() => setExpanded(isOpen ? null : c.id)}
                  style={{ display: "flex", alignItems: "center", justifyContent: "space-between", padding: "14px 20px", cursor: "pointer", background: isOpen ? "#F8FAFC" : "#fff" }}>
                  <div style={{ flex: 1 }}>
                    <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 4 }}>
                      <span style={{ fontWeight: 700, fontSize: 14, color: "#0F172A" }}>{c.title}</span>
                      <span style={{ background: style.bg, color: style.color, padding: "2px 8px", borderRadius: 20, fontSize: 11, fontWeight: 600 }}>{c.status.replace("_", " ")}</span>
                      <span style={{ background: "#F8FAFC", color: "#64748B", padding: "2px 8px", borderRadius: 20, fontSize: 11 }}>{c.contractType.replace("_", " ")}</span>
                    </div>
                    <div style={{ fontSize: 12, color: "#64748B", display: "flex", gap: 14 }}>
                      <span>#{c.contractNumber}</span>
                      {c.startDate && <span>{fmtDate(c.startDate)} → {fmtDate(c.endDate)}</span>}
                      {c.valueAmount > 0 && <span style={{ fontWeight: 600, color: "#0F172A" }}>{fmtR(c.valueAmount)}</span>}
                      {c.parties?.length > 0 && <span>{c.parties.length} {c.parties.length === 1 ? "party" : "parties"}</span>}
                    </div>
                  </div>
                  <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
                    {c.status === "SIGNED" && (
                      <button onClick={e => { e.stopPropagation(); downloadPdf(c.id, c.contractNumber) }}
                        style={{ display: "flex", alignItems: "center", gap: 5, padding: "6px 12px", background: "#EFF6FF", color: "#1D4ED8", border: "1px solid #BFDBFE", borderRadius: 6, fontSize: 12, cursor: "pointer" }}>
                        <Download size={12} /> PDF
                      </button>
                    )}
                    {isOpen ? <ChevronUp size={16} color="#94A3B8" /> : <ChevronDown size={16} color="#94A3B8" />}
                  </div>
                </div>

                {/* Expanded detail */}
                {isOpen && (
                  <div style={{ borderTop: "1px solid #E2E8F0", padding: "16px 20px", background: "#FAFAFA" }}>
                    {/* Info grid */}
                    <div style={{ display: "grid", gridTemplateColumns: "repeat(4, 1fr)", gap: 12, marginBottom: 16 }}>
                      {[
                        ["Start", fmtDate(c.startDate)],
                        ["End", fmtDate(c.endDate)],
                        ["Value", fmtR(c.valueAmount)],
                        ["Auto-renew", c.autoRenew ? "Yes" : "No"],
                      ].map(([label, value]) => (
                        <div key={label as string}>
                          <div style={{ fontSize: 10, fontWeight: 600, color: "#94A3B8", marginBottom: 2 }}>{(label as string).toUpperCase()}</div>
                          <div style={{ fontSize: 13, color: "#0F172A" }}>{value as string}</div>
                        </div>
                      ))}
                    </div>

                    {c.notes && (
                      <div style={{ marginBottom: 14, padding: "8px 12px", background: "#fff", border: "1px solid #E2E8F0", borderRadius: 7, fontSize: 13, color: "#475569" }}>
                        {c.notes}
                      </div>
                    )}

                    {/* Parties */}
                    <div style={{ marginBottom: 14 }}>
                      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 8 }}>
                        <span style={{ fontSize: 11, fontWeight: 600, color: "#64748B" }}>PARTIES & SIGNING</span>
                        {["DRAFT", "UNDER_REVIEW"].includes(c.status) && (
                          <button onClick={() => { setShowParty(c.id); setError("") }}
                            style={{ display: "flex", alignItems: "center", gap: 4, padding: "4px 10px", background: "#F8FAFC", border: "1px solid #E2E8F0", borderRadius: 6, fontSize: 12, cursor: "pointer" }}>
                            <Plus size={11} /> Add Party
                          </button>
                        )}
                      </div>

                      {c.parties?.length === 0 ? (
                        <div style={{ fontSize: 13, color: "#94A3B8" }}>No parties added yet.</div>
                      ) : (
                        <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
                          {c.parties.map(party => {
                            const signStyle = SIGN_STATUS_STYLE[party.signingStatus] || { color: "#64748B", icon: Clock }
                            const Icon = signStyle.icon
                            return (
                              <div key={party.id} style={{ display: "flex", alignItems: "center", justifyContent: "space-between", padding: "10px 14px", background: "#fff", border: "1px solid #E2E8F0", borderRadius: 8 }}>
                                <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
                                  <div style={{ width: 32, height: 32, borderRadius: "50%", background: "#F0FDF4", border: "1px solid #86EFAC", display: "flex", alignItems: "center", justifyContent: "center" }}>
                                    <User size={14} color="#0D9488" />
                                  </div>
                                  <div>
                                    <div style={{ fontWeight: 600, fontSize: 13, color: "#0F172A" }}>{party.fullName}</div>
                                    <div style={{ fontSize: 11, color: "#94A3B8" }}>
                                      {party.partyRole} · {party.partyType}
                                      {party.companyName && ` · ${party.companyName}`}
                                    </div>
                                  </div>
                                </div>
                                <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                                  <div style={{ display: "flex", alignItems: "center", gap: 4 }}>
                                    <Icon size={13} color={signStyle.color} />
                                    <span style={{ fontSize: 12, color: signStyle.color, fontWeight: 600 }}>
                                      {party.signingStatus}
                                    </span>
                                  </div>
                                  {party.signedAt && (
                                    <span style={{ fontSize: 11, color: "#94A3B8" }}>{fmtDT(party.signedAt)}</span>
                                  )}
                                  {c.status === "PENDING_SIGN" && party.signingStatus === "PENDING" && (
                                    <button
                                      onClick={() => { setShowOtp({ contractId: c.id, partyId: party.id, partyName: party.fullName }); setError("") }}
                                      style={{ display: "flex", alignItems: "center", gap: 4, padding: "5px 10px", background: "#EFF6FF", color: "#1D4ED8", border: "1px solid #BFDBFE", borderRadius: 6, fontSize: 11, cursor: "pointer" }}>
                                      <Send size={11} /> Send OTP
                                    </button>
                                  )}
                                </div>
                              </div>
                            )
                          })}
                        </div>
                      )}
                    </div>

                    {/* Actions */}
                    <div style={{ display: "flex", justifyContent: "flex-end", gap: 8 }}>
                      {nextActions(c.status).map(({ label, action }) => (
                        <button key={action}
                          onClick={() => {
                            if (action === "terminate") { setShowTerminate(c.id); return }
                            contractAction.mutate({ id: c.id, action })
                          }}
                          style={{
                            padding: "8px 16px", borderRadius: 7, fontSize: 13, cursor: "pointer", border: "none", fontWeight: 500,
                            background: action === "terminate" ? "#FEF2F2" : action === "send-for-signing" ? "#DCFCE7" : "#EFF6FF",
                            color: action === "terminate" ? "#DC2626" : action === "send-for-signing" ? "#166534" : "#1D4ED8",
                          }}>
                          {label}
                        </button>
                      ))}
                    </div>

                    {c.terminationReason && (
                      <div style={{ marginTop: 10, padding: "8px 12px", background: "#FEF2F2", borderRadius: 7, fontSize: 12, color: "#DC2626" }}>
                        Terminated: {c.terminationReason}
                      </div>
                    )}
                  </div>
                )}
              </div>
            )
          })}
        </div>
      )}

      {/* Add party modal */}
      {showParty && (
        <Modal title="Add Party" onClose={() => setShowParty(null)}>
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14 }}>
            <Field label="Party Type">
              <select value={partyForm.partyType} onChange={e => setPartyForm(f => ({ ...f, partyType: e.target.value }))} style={inputStyle}>
                {["INDIVIDUAL", "COMPANY"].map(t => <option key={t} value={t}>{t}</option>)}
              </select>
            </Field>
            <Field label="Role">
              <select value={partyForm.partyRole} onChange={e => setPartyForm(f => ({ ...f, partyRole: e.target.value }))} style={inputStyle}>
                {["COUNTERPARTY", "WITNESS", "GUARANTOR", "BENEFICIARY"].map(r => <option key={r} value={r}>{r}</option>)}
              </select>
            </Field>
            <div style={{ gridColumn: "1 / -1" }}>
              <Field label="Full Name *"><input value={partyForm.fullName} onChange={e => setPartyForm(f => ({ ...f, fullName: e.target.value }))} placeholder="Jane Smith" style={inputStyle} /></Field>
            </div>
            {partyForm.partyType === "COMPANY" && (
              <div style={{ gridColumn: "1 / -1" }}>
                <Field label="Company Name"><input value={partyForm.companyName} onChange={e => setPartyForm(f => ({ ...f, companyName: e.target.value }))} placeholder="Acme (Pty) Ltd" style={inputStyle} /></Field>
              </div>
            )}
            <Field label="Email"><input value={partyForm.email} onChange={e => setPartyForm(f => ({ ...f, email: e.target.value }))} placeholder="jane@example.com" style={inputStyle} /></Field>
            <Field label="Phone (for OTP)"><input value={partyForm.phone} onChange={e => setPartyForm(f => ({ ...f, phone: e.target.value }))} placeholder="+27 82 123 4567" style={inputStyle} /></Field>
            <Field label="Signing Order">
              <input type="number" value={partyForm.signingOrder} onChange={e => setPartyForm(f => ({ ...f, signingOrder: e.target.value }))} min="1" style={inputStyle} />
            </Field>
          </div>
          {error && <ErrMsg msg={error} />}
          <ModalFooter
            onCancel={() => setShowParty(null)}
            onSubmit={() => addParty.mutate({ id: showParty, body: { ...partyForm, signingOrder: parseInt(partyForm.signingOrder) } })}
            loading={addParty.isPending} disabled={!partyForm.fullName} label="Add Party"
          />
        </Modal>
      )}

      {/* OTP request modal */}
      {showOtp && (
        <Modal title={`Send OTP to ${showOtp.partyName}`} onClose={() => setShowOtp(null)}>
          <div style={{ padding: "16px 0", fontSize: 13, color: "#475569", lineHeight: 1.6 }}>
            An OTP will be sent to this party's registered phone number. They can then use it to sign the contract.
          </div>
          {error && <ErrMsg msg={error} />}
          <ModalFooter
            onCancel={() => setShowOtp(null)}
            onSubmit={() => requestOtp.mutate({ contractId: showOtp.contractId, partyId: showOtp.partyId })}
            loading={requestOtp.isPending} disabled={false} label="Send OTP"
          />
        </Modal>
      )}

      {/* Sign contract modal */}
      {showSign && (
        <Modal title="Sign Contract" onClose={() => { setShowSign(null); setOtpCode("") }}>
          <div style={{ marginBottom: 16, padding: "12px 16px", background: "#F0FDF4", border: "1px solid #86EFAC", borderRadius: 8, fontSize: 13, color: "#166534" }}>
            OTP sent successfully. Enter the code received via SMS to complete signing.
          </div>
          <Field label="OTP Code">
            <input
              value={otpCode}
              onChange={e => setOtpCode(e.target.value)}
              placeholder="Enter 6-digit OTP"
              maxLength={6}
              style={{ ...inputStyle, fontSize: 20, letterSpacing: "0.3em", textAlign: "center" }}
              autoFocus
            />
          </Field>
          {error && <ErrMsg msg={error} />}
          <ModalFooter
            onCancel={() => { setShowSign(null); setOtpCode("") }}
            onSubmit={() => signContract.mutate({ contractId: showSign.contractId, partyId: showSign.partyId, otp: otpCode })}
            loading={signContract.isPending} disabled={otpCode.length < 4} label="Sign Contract"
          />
        </Modal>
      )}

      {/* Terminate modal */}
      {showTerminate && (
        <Modal title="Terminate Contract" onClose={() => setShowTerminate(null)}>
          <p style={{ margin: "0 0 16px", fontSize: 13, color: "#DC2626" }}>
            This will permanently terminate the contract. This action cannot be undone.
          </p>
          <Field label="Reason *">
            <textarea value={terminateReason} onChange={e => setTerminateReason(e.target.value)} rows={3}
              placeholder="Reason for termination..." style={{ ...inputStyle, resize: "vertical" as const }} />
          </Field>
          {error && <ErrMsg msg={error} />}
          <div style={{ display: "flex", justifyContent: "flex-end", gap: 10, marginTop: 20 }}>
            <button onClick={() => setShowTerminate(null)} style={btnCancel}>Cancel</button>
            <button onClick={() => contractAction.mutate({ id: showTerminate, action: "terminate", body: { reason: terminateReason } })}
              disabled={!terminateReason || contractAction.isPending}
              style={{ padding: "9px 18px", background: "#DC2626", color: "#fff", border: "none", borderRadius: 8, fontSize: 14, cursor: "pointer" }}>
              {contractAction.isPending ? "Terminating..." : "Terminate"}
            </button>
          </div>
        </Modal>
      )}

      {/* Create contract modal */}
      {showCreate && (
        <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000 }}>
          <div style={{ background: "#fff", borderRadius: 14, padding: 28, width: 620, maxHeight: "90vh", overflowY: "auto", boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 22 }}>
              <h3 style={{ margin: 0, fontSize: 17, fontWeight: 700, color: "#0F172A" }}>New Contract</h3>
              <button onClick={() => setShowCreate(false)} style={{ background: "none", border: "none", cursor: "pointer", color: "#94A3B8" }}><X size={20} /></button>
            </div>
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14 }}>
              <div style={{ gridColumn: "1 / -1" }}>
                <Field label="Title *"><input value={form.title} onChange={e => f("title", e.target.value)} placeholder="Service Agreement — Acme Corp" style={inputStyle} /></Field>
              </div>
              <Field label="Contract Type *">
                <select value={form.contractType} onChange={e => f("contractType", e.target.value)} style={inputStyle}>
                  {CONTRACT_TYPES.map(t => <option key={t} value={t}>{t.replace(/_/g, " ")}</option>)}
                </select>
              </Field>
              <Field label="Template (optional)">
                <select value={form.templateId} onChange={e => f("templateId", e.target.value)} style={inputStyle}>
                  <option value="">Blank contract</option>
                  {templates.map(t => <option key={t.id} value={t.id}>{t.name}</option>)}
                </select>
              </Field>
              <Field label="Value (R)"><input type="number" value={form.valueAmount} onChange={e => f("valueAmount", e.target.value)} placeholder="50000.00" style={inputStyle} /></Field>
              <Field label="Currency"><input value={form.currency} onChange={e => f("currency", e.target.value)} placeholder="ZAR" style={inputStyle} /></Field>
              <Field label="Start Date"><input type="date" value={form.startDate} onChange={e => f("startDate", e.target.value)} style={inputStyle} /></Field>
              <Field label="End Date"><input type="date" value={form.endDate} onChange={e => f("endDate", e.target.value)} style={inputStyle} /></Field>
              <div style={{ gridColumn: "1 / -1" }}>
                <Field label="Notes"><textarea value={form.notes} onChange={e => f("notes", e.target.value)} rows={2} placeholder="Contract notes..." style={{ ...inputStyle, resize: "vertical" as const }} /></Field>
              </div>
              <div style={{ gridColumn: "1 / -1" }}>
                <label style={{ display: "flex", alignItems: "center", gap: 8, fontSize: 13, cursor: "pointer" }}>
                  <input type="checkbox" checked={form.autoRenew} onChange={e => f("autoRenew", e.target.checked)} />
                  Auto-renew on expiry
                </label>
              </div>
            </div>
            {error && <ErrMsg msg={error} />}
            <div style={{ display: "flex", justifyContent: "flex-end", gap: 10, marginTop: 20 }}>
              <button onClick={() => setShowCreate(false)} style={btnCancel}>Cancel</button>
              <button
                onClick={() => createContract.mutate({
                  title: form.title, contractType: form.contractType,
                  templateId: form.templateId || null,
                  valueAmount: parseFloat(form.valueAmount) || null,
                  currency: form.currency,
                  startDate: form.startDate || null,
                  endDate: form.endDate || null,
                  autoRenew: form.autoRenew,
                  notes: form.notes || null,
                })}
                disabled={!form.title || createContract.isPending} style={btnPrimary}>
                {createContract.isPending ? "Creating..." : "Create Contract"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

function Modal({ title, onClose, children }: { title: string; onClose: () => void; children: React.ReactNode }) {
  return (
    <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000 }}>
      <div style={{ background: "#fff", borderRadius: 14, padding: 28, width: 480, maxHeight: "85vh", overflowY: "auto", boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 22 }}>
          <h3 style={{ margin: 0, fontSize: 17, fontWeight: 700, color: "#0F172A" }}>{title}</h3>
          <button onClick={onClose} style={{ background: "none", border: "none", cursor: "pointer", color: "#94A3B8" }}><X size={20} /></button>
        </div>
        {children}
      </div>
    </div>
  )
}

function ModalFooter({ onCancel, onSubmit, loading, disabled, label }: any) {
  return (
    <div style={{ display: "flex", justifyContent: "flex-end", gap: 10, marginTop: 20 }}>
      <button onClick={onCancel} style={btnCancel}>Cancel</button>
      <button onClick={onSubmit} disabled={disabled || loading} style={btnPrimary}>
        {loading ? "Saving..." : label}
      </button>
    </div>
  )
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return <div><label style={{ display: "block", fontSize: 13, fontWeight: 500, color: "#374151", marginBottom: 5 }}>{label}</label>{children}</div>
}
function ErrMsg({ msg }: { msg: string }) {
  return <div style={{ marginTop: 10, color: "#DC2626", fontSize: 13 }}>{msg}</div>
}

const filterBtn = (active: boolean): React.CSSProperties => ({
  padding: "6px 12px", borderRadius: 6, fontSize: 12, cursor: "pointer",
  border: active ? "1px solid #0D9488" : "1px solid #E2E8F0",
  background: active ? "#F0FDF4" : "#fff", color: active ? "#0D9488" : "#64748B", fontWeight: active ? 600 : 400,
})
const btnPrimary: React.CSSProperties = { display: "flex", alignItems: "center", gap: 7, background: "#1B3A6B", color: "#fff", border: "none", borderRadius: 8, padding: "9px 18px", fontSize: 14, fontWeight: 500, cursor: "pointer" }
const btnCancel: React.CSSProperties  = { padding: "9px 18px", border: "1px solid #E2E8F0", borderRadius: 8, background: "#fff", fontSize: 14, cursor: "pointer", color: "#374151" }
const inputStyle: React.CSSProperties = { width: "100%", padding: "9px 12px", border: "1px solid #E2E8F0", borderRadius: 8, fontSize: 14, boxSizing: "border-box" as const, background: "#fff" }

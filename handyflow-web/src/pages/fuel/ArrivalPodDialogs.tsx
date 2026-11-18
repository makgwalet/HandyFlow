// Office dialogs for one delivery: look at its arrival check (and release a stale one), and approve a POD exception.
// Both are gated by their own permission, granted to no role by default. The server checks the permission again.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import { Modal, btnPrimary, btnGhost, inputStyle, labelStyle } from "../warehousing/whseUi"
import { POD_EXCEPTIONS, RELEASE_REASONS, podLabel, unwrap, validateApprove, validateRelease } from "./opsPolicyOps"

const msg = (e: any, fb: string) => e?.response?.data?.message ?? fb
const Err = ({ text }: { text: string }) => text ? <p role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 12, margin: "8px 0 0" }}>{text}</p> : null

interface Check { allowed: boolean; status?: string | null; checkedAt?: string | null; expiresAt?: string | null; releasedAt?: string | null; freshHours?: number | null; findings?: { severity?: string; message?: string; code?: string }[] }

const STATUS_TEXT: Record<string, string> = {
  FRESH: "Clear and fresh", STALE: "Clear, but too old", MISSING: "No check on record", BLOCKED: "Blocked", RELEASED: "Released by a supervisor",
}

export function ArrivalCheckDialog({ deliveryId, label, onClose }: { deliveryId: string; label: string; onClose: () => void }) {
  const qc = useQueryClient()
  const canRelease = usePermissions(["FUEL_ARRIVAL_CHECK_OVERRIDE"])
  const q = useQuery<Check>({ queryKey: ["fuel-arrival-check", deliveryId], retry: false, queryFn: async () => unwrap(await apiClient.get(`/api/v1/fuel/deliveries/${deliveryId}/arrival-check`)) })
  const [reason, setReason] = useState(""); const [why, setWhy] = useState(""); const [err, setErr] = useState("")
  const release = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/fuel/deliveries/${deliveryId}/arrival-check/release`, { reasonCode: reason, explanation: why.trim() }),
    onSuccess: () => { setErr(""); setReason(""); setWhy(""); qc.invalidateQueries({ queryKey: ["fuel-arrival-check", deliveryId] }) },
    onError: (e: any) => setErr(msg(e, "Could not release the check")),
  })
  const c = q.data
  const status = c?.status ?? (c ? (c.allowed ? "FRESH" : "BLOCKED") : "")
  return (
    <Modal title={`Arrival check: ${label}`} onClose={onClose}>
      {q.isLoading && <p style={{ fontSize: 13 }}>Loading…</p>}
      {q.isError && <Err text={msg(q.error, "Could not load the arrival check")} />}
      {c && (
        <>
          <p style={{ fontSize: 14, fontWeight: 600, margin: "0 0 4px" }}>{STATUS_TEXT[status] ?? status}</p>
          <p style={{ fontSize: 12.5, color: "var(--hf-text-muted)", margin: "0 0 8px" }}>
            {c.checkedAt ? `Checked ${new Date(c.checkedAt).toLocaleString()}.` : "The driver has not run the check."}
            {c.expiresAt ? ` Counts until ${new Date(c.expiresAt).toLocaleString()}.` : ""}
            {c.freshHours ? ` A check stays fresh for ${c.freshHours} hours.` : ""}
          </p>
          {(c.findings ?? []).length > 0 && <ul style={{ fontSize: 12.5, margin: "0 0 8px", paddingLeft: 18 }}>{(c.findings ?? []).map((f, i) => <li key={i}>{f.message ?? f.code}</li>)}</ul>}
          {status === "BLOCKED" && <p style={{ fontSize: 12.5, color: "var(--hf-danger-text)" }}>A blocked check cannot be released here: an invalid licence or a prohibited vehicle or product has to be put right, then checked again.</p>}
          {status === "STALE" && !canRelease && <p style={{ fontSize: 12.5, color: "var(--hf-text-muted)" }}>The driver needs to run the check again. Releasing it needs the arrival check override permission, which you do not have.</p>}
          {status === "STALE" && canRelease && (
            <fieldset style={{ border: "1px solid var(--hf-border)", borderRadius: 8, padding: 12, margin: "8px 0 0" }}>
              <legend style={{ fontSize: 12.5, fontWeight: 600 }}>Release this check for one more window</legend>
              <label style={labelStyle} htmlFor="rel-reason">Reason</label>
              <select id="rel-reason" aria-label="Release reason" value={reason} onChange={e => setReason(e.target.value)} style={inputStyle}>
                <option value="">Choose…</option>
                {RELEASE_REASONS.map(r => <option key={r.code} value={r.code}>{r.label}</option>)}
              </select>
              <label style={{ ...labelStyle, marginTop: 10 }} htmlFor="rel-why">Explanation</label>
              <textarea id="rel-why" aria-label="Release explanation" value={why} onChange={e => setWhy(e.target.value)} rows={3} style={inputStyle} />
              <p style={{ fontSize: 11.5, color: "var(--hf-text-muted)", margin: "6px 0 0" }}>Your name and the time are recorded with the original check. Licences and vehicle rules are checked again now; if any fail, the release is refused. You cannot release a check you ran yourself.</p>
              <Err text={err} />
              <div style={{ display: "flex", justifyContent: "flex-end", marginTop: 10 }}>
                <button style={btnPrimary} disabled={release.isPending} onClick={() => { const p = validateRelease(reason, why); if (p) return setErr(p); setErr(""); release.mutate() }}>Release check</button>
              </div>
            </fieldset>
          )}
        </>
      )}
      <div style={{ display: "flex", justifyContent: "flex-end", marginTop: 12 }}><button style={btnGhost} onClick={onClose}>Close</button></div>
    </Modal>
  )
}

interface PodEx { id: string; type: string; reason: string; status: string; approvedAt?: string | null; usedAt?: string | null; evidenceCount?: number }

export function PodExceptionDialog({ deliveryId, label, onClose }: { deliveryId: string; label: string; onClose: () => void }) {
  const qc = useQueryClient()
  const canApprove = usePermissions(["FUEL_POD_EXCEPTION_APPROVE"])
  const q = useQuery<PodEx[]>({ queryKey: ["fuel-pod-exceptions", deliveryId], retry: false, queryFn: async () => unwrap(await apiClient.get(`/api/v1/fuel/deliveries/${deliveryId}/pod-exceptions`)) ?? [] })
  const [type, setType] = useState(""); const [reason, setReason] = useState(""); const [err, setErr] = useState("")
  const approve = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/fuel/deliveries/${deliveryId}/pod-exception/approve`, { type, reason: reason.trim() }),
    onSuccess: () => { setErr(""); setType(""); setReason(""); qc.invalidateQueries({ queryKey: ["fuel-pod-exceptions", deliveryId] }) },
    onError: (e: any) => setErr(msg(e, "Could not approve the exception")),
  })
  const list = q.data ?? []
  return (
    <Modal title={`POD exception: ${label}`} onClose={onClose}>
      <p style={{ fontSize: 12.5, color: "var(--hf-text-muted)", margin: "0 0 8px" }}>An ordinary delivery needs the name of the person who received the fuel. An exception lets the driver finish without one, once, with a reason and evidence (a photo, a location, a document or a signature) on the load.</p>
      {q.isError && <Err text={msg(q.error, "Could not load the exceptions")} />}
      {list.length === 0 && !q.isLoading && !q.isError && <p style={{ fontSize: 13 }}>No exception approved for this delivery.</p>}
      {list.map(x => (
        <div key={x.id} style={{ borderTop: "1px solid var(--hf-border)", padding: "8px 0", fontSize: 13 }}>
          <strong>{podLabel(x.type)}</strong> · {x.status === "USED" ? "used" : x.status === "APPROVED" ? "approved, not used yet" : x.status.toLowerCase()}
          <div style={{ color: "var(--hf-text-muted)", fontSize: 12 }}>{x.reason}{typeof x.evidenceCount === "number" ? ` · ${x.evidenceCount} piece${x.evidenceCount === 1 ? "" : "s"} of evidence` : ""}</div>
        </div>
      ))}
      {canApprove ? (
        <fieldset style={{ border: "1px solid var(--hf-border)", borderRadius: 8, padding: 12, margin: "10px 0 0" }}>
          <legend style={{ fontSize: 12.5, fontWeight: 600 }}>Approve an exception</legend>
          <label style={labelStyle} htmlFor="pod-type">Kind</label>
          <select id="pod-type" aria-label="Exception kind" value={type} onChange={e => setType(e.target.value)} style={inputStyle}>
            <option value="">Choose…</option>
            {POD_EXCEPTIONS.filter(e => e.code.startsWith("AFTER") || e.code.startsWith("CONTACTLESS")).map(e => <option key={e.code} value={e.code}>{e.label}</option>)}
          </select>
          <label style={{ ...labelStyle, marginTop: 10 }} htmlFor="pod-why">Reason</label>
          <textarea id="pod-why" aria-label="Exception reason" value={reason} onChange={e => setReason(e.target.value)} rows={3} style={inputStyle} />
          <p style={{ fontSize: 11.5, color: "var(--hf-text-muted)", margin: "6px 0 0" }}>Unattended and automated sites are not approved here: they are set on the customer's site. The exception must also be allowed in the company's POD policy.</p>
          <Err text={err} />
          <div style={{ display: "flex", justifyContent: "flex-end", marginTop: 10 }}>
            <button style={btnPrimary} disabled={approve.isPending} onClick={() => { const p = validateApprove(type, reason); if (p) return setErr(p); setErr(""); approve.mutate() }}>Approve</button>
          </div>
        </fieldset>
      ) : <p style={{ fontSize: 12, color: "var(--hf-text-muted)", marginTop: 10 }}>Approving an exception needs the POD exception approval permission, which you do not have.</p>}
      <div style={{ display: "flex", justifyContent: "flex-end", marginTop: 12 }}><button style={btnGhost} onClick={onClose}>Close</button></div>
    </Modal>
  )
}

// src/pages/security/DeviceCompliancePanel.tsx
//
// What the company requires of a guard phone, and which phones fall short. Report only: nothing is blocked and nobody is signed out.
// Only someone with the whole company sees it (the server enforces that).
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { ShieldCheck } from "lucide-react"
import { apiClient } from "../../api/client"
import Chip from "../../components/ui/Chip"
import { accessErrorText } from "./siteScopeLogic"
import {
  APP_VERSION_MAX, formProblem, formToRequest, isChanged, missingText, orderItems, policyToForm, summaryText, violationLabel,
  type ComplianceReport, type PolicyForm, type PolicyView,
} from "./deviceComplianceLogic"

const th: React.CSSProperties = { textAlign: "left", padding: "8px 10px", fontSize: 11, color: "var(--hf-text-muted)", fontWeight: 700, textTransform: "uppercase", letterSpacing: 0.4 }
const td: React.CSSProperties = { padding: "9px 10px", fontSize: 13, borderTop: "1px solid var(--hf-border-subtle)" }
const muted: React.CSSProperties = { fontSize: 12, color: "var(--hf-text-muted)" }
const field: React.CSSProperties = { padding: "6px 8px", border: "1px solid var(--hf-border)", borderRadius: 7, background: "var(--hf-surface)", color: "var(--hf-text)", fontSize: 13, width: 110 }
const btn: React.CSSProperties = { padding: "5px 12px", border: "1px solid var(--hf-border)", borderRadius: 7, background: "var(--hf-surface)", color: "var(--hf-text-secondary)", cursor: "pointer", fontSize: 12 }

/** Remounted (by key) after every save, so the form always starts from what the server holds. */
function PolicyEditor({ policy }: { policy: PolicyView }) {
  const qc = useQueryClient()
  const [form, setForm] = useState<PolicyForm>(() => policyToForm(policy))
  const [problem, setProblem] = useState<string | null>(null)
  const save = useMutation({
    mutationFn: () => apiClient.put("/api/v1/security/devices/compliance-policy", formToRequest(form)),
    onSuccess: () => { setProblem(null); qc.invalidateQueries({ queryKey: ["device-compliance"] }) },
    onError: (e: unknown) => setProblem(accessErrorText(e, "The policy could not be saved.")),
  })
  const bad = formProblem(form)
  const changed = isChanged(form, policy)
  const set = (patch: Partial<PolicyForm>) => setForm(f => ({ ...f, ...patch }))

  return (
    <div style={{ display: "flex", gap: 14, alignItems: "flex-end", flexWrap: "wrap", marginBottom: 10 }}>
      <label style={{ display: "grid", gap: 3, fontSize: 12, color: "var(--hf-text-secondary)" }}>Lowest app version
        <input aria-label="Lowest app version" placeholder="e.g. 2.4" maxLength={APP_VERSION_MAX + 10} style={field} value={form.minAppVersion} onChange={e => set({ minAppVersion: e.target.value })} />
      </label>
      <label style={{ display: "grid", gap: 3, fontSize: 12, color: "var(--hf-text-secondary)" }}>Lowest Android / iOS version
        <input aria-label="Lowest Android or iOS version" placeholder="e.g. 12" inputMode="numeric" maxLength={4} style={field} value={form.minOsMajor} onChange={e => set({ minOsMajor: e.target.value })} />
      </label>
      <label style={{ display: "flex", gap: 6, alignItems: "center", fontSize: 13, color: "var(--hf-text)" }}>
        <input type="checkbox" checked={form.requireScreenLock} onChange={e => set({ requireScreenLock: e.target.checked })} /> Screen lock required
      </label>
      <label style={{ display: "flex", gap: 6, alignItems: "center", fontSize: 13, color: "var(--hf-text)" }}>
        <input type="checkbox" checked={form.blockRooted} onChange={e => set({ blockRooted: e.target.checked })} /> No rooted or jailbroken phones
      </label>
      <button type="button" style={{ ...btn, opacity: bad || !changed ? 0.5 : 1 }} disabled={!!bad || !changed || save.isPending} onClick={() => save.mutate()}>
        {save.isPending ? "Saving…" : "Save policy"}
      </button>
      {bad && <span role="alert" style={{ ...muted, color: "var(--hf-danger-text)", flexBasis: "100%" }}>{bad}</span>}
      {problem && <span role="alert" style={{ ...muted, color: "var(--hf-danger-text)", flexBasis: "100%" }}>{problem}</span>}
    </div>
  )
}

export default function DeviceCompliancePanel() {
  const { data, isLoading, error } = useQuery<ComplianceReport>({
    queryKey: ["device-compliance"],
    queryFn: async () => (await apiClient.get("/api/v1/security/devices/compliance")).data?.data,
  })

  return (
    <section aria-label="Phone requirements" style={{ marginBottom: 20, padding: 16, border: "1px solid var(--hf-border)", borderRadius: 12, background: "var(--hf-surface)" }}>
      <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 4, fontWeight: 700, color: "var(--hf-text-primary)" }}>
        <ShieldCheck size={16} /> Phone requirements
      </div>
      <div style={{ ...muted, marginBottom: 10 }}>What a guard phone must be like. Phones report their own app, software, screen lock and root status. Report only: nothing is blocked and nobody is signed out. Leave a box empty or unticked to ask for nothing.</div>
      {isLoading && <div style={muted}>Checking phones…</div>}
      {error && <div role="alert" style={{ ...muted, color: "var(--hf-danger-text)" }}>{accessErrorText(error, "Could not load phone requirements.")}</div>}
      {data && (
        <>
          <PolicyEditor key={data.policy.updatedAt ?? "none"} policy={data.policy} />
          <div style={{ fontSize: 13, marginBottom: data.items.length ? 10 : 0, color: data.nonCompliant > 0 ? "var(--hf-danger)" : "var(--hf-text-secondary)", fontWeight: data.nonCompliant > 0 ? 700 : 500 }}>
            {summaryText(data)}
          </div>
          {data.items.length > 0 && (
            <div style={{ overflowX: "auto" }}>
              <table style={{ width: "100%", borderCollapse: "collapse" }}>
                <thead><tr><th style={th}>Phone</th><th style={th}>Guard</th><th style={th}>Problem</th><th style={th}>App</th><th style={th}>Software</th></tr></thead>
                <tbody>
                  {orderItems(data.items).map(i => (
                    <tr key={i.deviceId}>
                      <td style={td}>{i.deviceName ?? "Unnamed phone"}</td>
                      <td style={td}>{i.guardName ?? (i.deviceType === "SHARED_SITE_DEVICE" ? "Shared" : "-")}</td>
                      <td style={td}>
                        <span style={{ display: "inline-flex", gap: 4, flexWrap: "wrap" }}>
                          {i.violations.map(code => <Chip key={code} tone="bad">{violationLabel(code)}</Chip>)}
                          {i.missing.length > 0 && <Chip tone="warn">{missingText(i.missing)}</Chip>}
                        </span>
                      </td>
                      <td style={td}>{i.appVersion ?? "-"}</td>
                      <td style={td}>{i.osVersion ?? "-"}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </>
      )}
    </section>
  )
}

// Persistent red banner for red flags raised by the questionnaires (Q-5, CLINIC-DEC-021). It shows each flag and the group it came from,
// stays until a clinician marks it reviewed, and lets a clinician sign without reviewing only by giving a reason.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { flagReasonProblem, flagSource, splitFlags, type ActiveRedFlag, FLAG_REASON_MAX } from "./redFlags"

export const redFlagsKey = (consultationId: string | null) => ["clinic-red-flags", consultationId]

interface Props {
  consultationId: string | null
  /** Called with the reason typed for signing without review ("" when none, or when the reason is not usable). */
  onOverrideReason?: (reason: string) => void
  readOnly?: boolean
}

export default function RedFlagBanner({ consultationId, onOverrideReason, readOnly }: Props) {
  const qc = useQueryClient()
  const { data: flags = [] } = useQuery<ActiveRedFlag[]>({
    queryKey: redFlagsKey(consultationId),
    enabled: !!consultationId,
    queryFn: async () => {
      const r = await apiClient.get(`/api/v1/clinic/consultations/${consultationId}/red-flags`)
      return r.data?.data ?? r.data ?? []
    },
    refetchInterval: 15_000,
  })
  const review = useMutation({
    mutationFn: (f: ActiveRedFlag) =>
      apiClient.post(`/api/v1/clinic/consultations/${consultationId}/red-flags/${f.groupCode}/${f.flagCode}/review`),
    onSuccess: () => qc.invalidateQueries({ queryKey: redFlagsKey(consultationId) }),
  })
  const [overriding, setOverriding] = useState(false)
  const [reason, setReason] = useState("")

  const { open, done, urgentOpen } = splitFlags(flags)
  if (flags.length === 0) return null

  const changeReason = (v: string) => { setReason(v); onOverrideReason?.(flagReasonProblem(v) ? "" : v.trim()) }
  const stopOverride = () => { setOverriding(false); setReason(""); onOverrideReason?.("") }

  return (
    <div style={{ marginTop: 12, display: "grid", gap: 8 }}>
      {open.length > 0 && (
        <div role="alert" aria-label="Red flags to review"
          style={{ padding: "12px 14px", borderRadius: 10, border: "2px solid var(--hf-danger)", background: "var(--hf-danger-soft)", color: "var(--hf-danger-text)" }}>
          <div style={{ fontWeight: 800, fontSize: 14, marginBottom: 8 }}>
            {urgentOpen ? "Urgent red flag" : "Red flag"}{open.length > 1 ? "s" : ""} to review before signing
          </div>
          <div style={{ display: "grid", gap: 8 }}>
            {open.map(f => (
              <div key={`${f.groupCode}:${f.flagCode}`} style={{ display: "flex", gap: 10, alignItems: "flex-start", flexWrap: "wrap" }}>
                <div style={{ flex: "1 1 260px", minWidth: 0 }}>
                  <div style={{ fontWeight: 700, fontSize: 13 }}>
                    {f.severity === "URGENT" ? "Needs attention: " : "Note: "}{f.label}
                  </div>
                  {f.message && <div style={{ fontSize: 12 }}>{f.message}</div>}
                  <div style={{ fontSize: 11, opacity: 0.85 }}>Source: {flagSource(f)}</div>
                </div>
                {!readOnly && (
                  <button type="button" onClick={() => review.mutate(f)} disabled={review.isPending}
                    style={{ padding: "5px 12px", borderRadius: 8, border: "1px solid var(--hf-danger)", background: "var(--hf-surface)",
                      color: "var(--hf-danger-text)", fontWeight: 700, fontSize: 12, cursor: "pointer" }}>
                    Mark reviewed
                  </button>
                )}
              </div>
            ))}
          </div>
          {review.isError && <div role="alert" style={{ fontSize: 12, marginTop: 6 }}>Could not save the review. Try again.</div>}
          <div style={{ fontSize: 11, marginTop: 8, opacity: 0.85 }}>
            Reviewed means you have seen the flag; it does not mean it is resolved.
          </div>
          {!readOnly && !overriding && (
            <button type="button" onClick={() => setOverriding(true)}
              style={{ marginTop: 6, background: "none", border: "none", padding: 0, fontSize: 12, textDecoration: "underline", color: "inherit", cursor: "pointer" }}>
              Sign without reviewing…
            </button>
          )}
          {overriding && (
            <div style={{ marginTop: 8 }}>
              <label htmlFor="red-flag-override" style={{ fontSize: 12, fontWeight: 700 }}>Reason for signing without reviewing (audited)</label>
              <textarea id="red-flag-override" value={reason} rows={2} maxLength={FLAG_REASON_MAX + 50}
                onChange={e => changeReason(e.target.value)}
                style={{ width: "100%", boxSizing: "border-box", marginTop: 4, padding: 8, borderRadius: 6, border: "1px solid var(--hf-border)", fontSize: 13 }} />
              <button type="button" onClick={stopOverride}
                style={{ marginTop: 4, background: "none", border: "none", padding: 0, fontSize: 12, textDecoration: "underline", color: "inherit", cursor: "pointer" }}>
                Cancel: I will review them
              </button>
            </div>
          )}
        </div>
      )}
      {done.length > 0 && (
        <div style={{ fontSize: 12, color: "var(--hf-text-muted)", padding: "6px 10px", border: "1px solid var(--hf-border)", borderRadius: 8 }}>
          {done.map(f => (
            <div key={`${f.groupCode}:${f.flagCode}`}>
              Reviewed: {f.label} ({flagSource(f)}){f.reviewedBy ? ` by ${f.reviewedBy}` : ""}
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

// src/pages/auditor-portal/AuditorPortalTenantDetailPage.tsx
import { useEffect, useMemo, useState } from "react"
import { useParams, useNavigate } from "react-router-dom"
import { auditorPortalApi, type EvidenceItem, type ControlExceptionItem } from "../../api/auditorPortal.api"
import { EvidenceRequests } from "../../components/evidenceRequests/EvidenceRequests"
import { auditorEvidenceRequestApi } from "../../api/evidenceRequests.api"
import { AuditQueries } from "../../components/auditQueries/AuditQueries"
import { auditorAuditQueryApi } from "../../api/auditQueries.api"
import { PortalCard, PortalShell } from "./PortalShell"
import { Stat } from "../internal-audit/auditUi"
import { grid } from "../internal-audit/auditUi.styles"

const NAVY = "var(--hf-primary)"
const NAVY_TEXT = "var(--hf-primary-text)";
const BORDER = "var(--hf-border)"
const CANVAS = "var(--hf-surface-muted)"
const INK = "var(--hf-text)"
const MUTED = "var(--hf-text-muted)"
const FAINT = "var(--hf-text-faint)"

const fmtD = (d: string) => new Date(d).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric" })
const fmtSize = (bytes: number) => bytes < 1024 * 1024 ? `${(bytes / 1024).toFixed(0)} KB` : `${(bytes / (1024 * 1024)).toFixed(1)} MB`

type Tab = "exceptions" | "evidence" | "requests" | "queries"

export function AuditorPortalTenantDetailPage() {
  const { tenantId } = useParams<{ tenantId: string }>()
  const navigate = useNavigate()
  const [tab, setTab] = useState<Tab>("exceptions")
  const [exceptions, setExceptions] = useState<ControlExceptionItem[]>([])
  const [evidence, setEvidence] = useState<EvidenceItem[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState("")
  const queryApi = useMemo(() => (tenantId ? auditorAuditQueryApi(tenantId) : null), [tenantId])
  const requestApi = useMemo(() => (tenantId ? auditorEvidenceRequestApi(tenantId) : null), [tenantId])

  useEffect(() => {
    if (!tenantId) return
    Promise.all([
      auditorPortalApi.getControlExceptions(tenantId),
      auditorPortalApi.getEvidence(tenantId),
    ]).then(([exc, ev]) => { setExceptions(exc); setEvidence(ev) })
      .catch(e => setError(e?.response?.data?.message ?? "Could not load this business's records. Try again shortly."))
      .finally(() => setLoading(false))
  }, [tenantId])

  const openCount = exceptions.filter(e => e.status === "OPEN").length
  const resolvedCount = exceptions.filter(e => e.status === "RESOLVED").length
  return (
    <PortalShell
      left={<button onClick={() => navigate("/auditor/portal")} style={{ background: "none", border: "none", color: MUTED, fontSize: 13, cursor: "pointer", padding: 0 }}>← Back</button>}
      right={<span style={{ fontSize: 13, color: FAINT }}>Business Access #{tenantId?.slice(0, 8)}</span>}>
        <h1 style={{ fontSize: 18, fontWeight: 800, color: INK, margin: "0 0 14px" }}>Review</h1>

        {!loading && !error && (
          <div style={{ ...grid(150), marginBottom: 18 }}>
            <Stat label="Open exceptions" value={openCount} tone={openCount ? "danger" : undefined} />
            <Stat label="Resolved" value={resolvedCount} />
            <Stat label="Evidence files" value={evidence.length} />
          </div>
        )}

        <div role="tablist" aria-label="Review sections" style={{ display: "flex", gap: 4, borderBottom: `1px solid ${BORDER}`, marginBottom: 20, overflowX: "auto" }}>
          {([["exceptions", "Control Exceptions"], ["evidence", "Evidence"], ["requests", "Requests"], ["queries", "Queries"]] as [Tab, string][]).map(([id, label]) => (
            <button key={id} role="tab" aria-selected={tab === id} onClick={() => setTab(id)} style={{
              padding: "8px 14px", background: "none", border: "none", whiteSpace: "nowrap",
              borderBottom: tab === id ? `2px solid ${NAVY}` : "2px solid transparent",
              color: tab === id ? NAVY_TEXT : MUTED, fontWeight: tab === id ? 700 : 500, fontSize: 13, cursor: "pointer", marginBottom: -1,
            }}>{label}</button>
          ))}
        </div>

        {tab === "queries" && queryApi ? (
          <AuditQueries side="auditor" api={queryApi} />
        ) : tab === "requests" && requestApi ? (
          <EvidenceRequests side="auditor" api={requestApi} />
        ) : loading ? (
          <div style={{ color: FAINT, fontSize: 13 }}>Loading…</div>
        ) : error ? (
          <div role="alert" style={{ padding: "8px 12px", background: "var(--hf-danger-soft)", border: "1px solid var(--hf-danger-border)", borderRadius: 6, fontSize: 12.5, color: "var(--hf-danger-text)" }}>{error}</div>
        ) : tab === "exceptions" ? (
          exceptions.length === 0 ? (
            <Empty text="No control exceptions on record." />
          ) : (
            <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
              {exceptions.map(e => (
                <PortalCard key={e.id}>
                  <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start" }}>
                    <div>
                      <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 4 }}>
                        <SeverityBadge severity={e.severity} />
                        <StatusBadge status={e.status} />
                        <span style={{ fontSize: 11, color: FAINT, textTransform: "uppercase" as const, letterSpacing: "0.03em" }}>
                          {e.sourceModule} · {e.controlType.replace(/_/g, " ")}
                        </span>
                      </div>
                      <div style={{ fontSize: 13.5, color: INK }}>{e.description}</div>
                      <div style={{ fontSize: 11, color: FAINT, marginTop: 6 }}>Flagged {fmtD(e.detectedAt)}</div>
                      {e.status === "RESOLVED" && (
                        <div style={{ marginTop: 8, padding: "8px 10px", background: CANVAS, borderRadius: 6, fontSize: 12 }}>
                          <span style={{ color: MUTED }}>Resolved by {e.resolvedByName} on {e.resolvedAt && fmtD(e.resolvedAt)}</span>
                          {e.resolutionNotes && <div style={{ color: INK, marginTop: 2 }}>{e.resolutionNotes}</div>}
                        </div>
                      )}
                    </div>
                  </div>
                </PortalCard>
              ))}
            </div>
          )
        ) : (
          evidence.length === 0 ? (
            <Empty text="No evidence on file." />
          ) : (
            <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
              {evidence.map(e => (
                <div key={e.id} style={{ display: "flex", justifyContent: "space-between", alignItems: "center", background: "var(--hf-surface)", border: `1px solid ${BORDER}`, borderRadius: 10, padding: "10px 14px" }}>
                  <div>
                    <div style={{ fontSize: 13, fontWeight: 600, color: INK }}>{e.fileName}</div>
                    <div style={{ fontSize: 11, color: FAINT, marginTop: 2 }}>
                      {e.evidenceType} · {fmtSize(e.fileSizeBytes)} · uploaded by {e.uploadedByName ?? "—"} · {fmtD(e.createdAt)}
                    </div>
                  </div>
                </div>
              ))}
            </div>
          )
        )}
    </PortalShell>
  )
}

function SeverityBadge({ severity }: { severity: string }) {
  const tones: Record<string, { c: string; bg: string }> = {
    WARNING: { c: "var(--hf-warning-text)", bg: "var(--hf-warning-soft)" },
    CRITICAL: { c: "var(--hf-danger-text)", bg: "var(--hf-danger-soft)" },
  }
  const t = tones[severity] ?? tones.WARNING
  return <span style={{ background: t.bg, color: t.c, padding: "2px 8px", borderRadius: 20, fontSize: 10, fontWeight: 700 }}>{severity}</span>
}

function StatusBadge({ status }: { status: string }) {
  const tones: Record<string, { c: string; bg: string }> = {
    OPEN: { c: "var(--hf-danger-text)", bg: "var(--hf-danger-soft)" },
    RESOLVED: { c: "var(--hf-success-text-strong)", bg: "var(--hf-success-soft-strong)" },
    DISMISSED: { c: "var(--hf-text-muted)", bg: "var(--hf-surface-sunken)" },
  }
  const t = tones[status] ?? tones.OPEN
  return <span style={{ background: t.bg, color: t.c, padding: "2px 8px", borderRadius: 20, fontSize: 10, fontWeight: 700 }}>{status}</span>
}

function Empty({ text }: { text: string }) {
  return <div style={{ padding: 40, textAlign: "center" as const, color: FAINT, fontSize: 14, background: "var(--hf-surface)", border: `1px solid ${BORDER}`, borderRadius: 8 }}>{text}</div>
}

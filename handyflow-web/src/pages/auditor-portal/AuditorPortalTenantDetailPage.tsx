// src/pages/auditor-portal/AuditorPortalTenantDetailPage.tsx
import { useEffect, useState } from "react"
import { useParams, useNavigate } from "react-router-dom"
import { auditorPortalApi, type EvidenceItem, type ControlExceptionItem } from "../../api/auditorPortal.api"

const NAVY = "#1B3A6B"
const BORDER = "#E2E8F0"
const CANVAS = "#F8FAFC"
const INK = "#0F172A"
const MUTED = "#64748B"
const FAINT = "#94A3B8"

const fmtD = (d: string) => new Date(d).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric" })
const fmtSize = (bytes: number) => bytes < 1024 * 1024 ? `${(bytes / 1024).toFixed(0)} KB` : `${(bytes / (1024 * 1024)).toFixed(1)} MB`

type Tab = "exceptions" | "evidence"

export function AuditorPortalTenantDetailPage() {
  const { tenantId } = useParams<{ tenantId: string }>()
  const navigate = useNavigate()
  const [tab, setTab] = useState<Tab>("exceptions")
  const [exceptions, setExceptions] = useState<ControlExceptionItem[]>([])
  const [evidence, setEvidence] = useState<EvidenceItem[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    if (!tenantId) return
    setLoading(true)
    Promise.all([
      auditorPortalApi.getControlExceptions(tenantId),
      auditorPortalApi.getEvidence(tenantId),
    ]).then(([exc, ev]) => { setExceptions(exc); setEvidence(ev) })
      .finally(() => setLoading(false))
  }, [tenantId])

  return (
    <div style={{ minHeight: "100vh", background: CANVAS, fontFamily: "'Inter', system-ui, sans-serif" }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", padding: "16px 32px", background: "#fff", borderBottom: `1px solid ${BORDER}` }}>
        <button onClick={() => navigate("/auditor/portal")} style={{ background: "none", border: "none", color: MUTED, fontSize: 13, cursor: "pointer" }}>← Back</button>
        <span style={{ fontSize: 13, color: FAINT }}>Business Access #{tenantId?.slice(0, 8)}</span>
      </div>

      <div style={{ padding: "24px 32px", maxWidth: 800 }}>
        <h1 style={{ fontSize: 18, fontWeight: 800, color: INK, marginBottom: 16 }}>Review</h1>

        <div style={{ display: "flex", gap: 4, borderBottom: `1px solid ${BORDER}`, marginBottom: 20 }}>
          {([["exceptions", "Control Exceptions"], ["evidence", "Evidence"]] as [Tab, string][]).map(([id, label]) => (
            <button key={id} onClick={() => setTab(id)} style={{
              padding: "8px 14px", background: "none", border: "none",
              borderBottom: tab === id ? `2px solid ${NAVY}` : "2px solid transparent",
              color: tab === id ? NAVY : MUTED, fontWeight: tab === id ? 700 : 500, fontSize: 13, cursor: "pointer", marginBottom: -1,
            }}>{label}</button>
          ))}
        </div>

        {loading ? (
          <div style={{ color: FAINT, fontSize: 13 }}>Loading…</div>
        ) : tab === "exceptions" ? (
          exceptions.length === 0 ? (
            <Empty text="No control exceptions on record." />
          ) : (
            <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
              {exceptions.map(e => (
                <div key={e.id} style={{ background: "#fff", border: `1px solid ${BORDER}`, borderRadius: 8, padding: "14px 16px" }}>
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
                </div>
              ))}
            </div>
          )
        ) : (
          evidence.length === 0 ? (
            <Empty text="No evidence on file." />
          ) : (
            <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
              {evidence.map(e => (
                <div key={e.id} style={{ display: "flex", justifyContent: "space-between", alignItems: "center", background: "#fff", border: `1px solid ${BORDER}`, borderRadius: 8, padding: "10px 14px" }}>
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
      </div>
    </div>
  )
}

function SeverityBadge({ severity }: { severity: string }) {
  const tones: Record<string, { c: string; bg: string }> = {
    WARNING: { c: "#D97706", bg: "#FFFBEB" },
    CRITICAL: { c: "#DC2626", bg: "#FEF2F2" },
  }
  const t = tones[severity] ?? tones.WARNING
  return <span style={{ background: t.bg, color: t.c, padding: "2px 8px", borderRadius: 20, fontSize: 10, fontWeight: 700 }}>{severity}</span>
}

function StatusBadge({ status }: { status: string }) {
  const tones: Record<string, { c: string; bg: string }> = {
    OPEN: { c: "#DC2626", bg: "#FEF2F2" },
    RESOLVED: { c: "#166534", bg: "#DCFCE7" },
    DISMISSED: { c: "#64748B", bg: "#F1F5F9" },
  }
  const t = tones[status] ?? tones.OPEN
  return <span style={{ background: t.bg, color: t.c, padding: "2px 8px", borderRadius: 20, fontSize: 10, fontWeight: 700 }}>{status}</span>
}

function Empty({ text }: { text: string }) {
  return <div style={{ padding: 40, textAlign: "center" as const, color: FAINT, fontSize: 14, background: "#fff", border: `1px solid ${BORDER}`, borderRadius: 8 }}>{text}</div>
}

// src/pages/trainingprovider/TrainProvCertificatesTab.tsx
//
// All certificates across every client/session — confirmed via
// TrainProvCertificateController: GET /certificates, POST
// /certificates/{id}/revoke (ADMIN-only), GET /certificates/{id}/pdf.
// CertificateResponse carries a client name snapshot in addition to the
// delegate/course snapshots Module 4a's own certificate DTO has —
// confirmed via TrainProvCertificateService/TrainProvCertificate source
// (client name + unit standard snapshots, per the status doc's own
// domain summary).
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { Award, Download, Ban } from "lucide-react"
import { apiClient } from "../../api/client"

interface CertificateResponse {
  id: string; enrollmentId: string; delegateId: string; clientId: string
  delegateNameSnapshot: string; clientNameSnapshot: string; courseTitleSnapshot: string; unitStandardSnapshot: string | null
  certificateNumber: string; issueDate: string; expiryDate: string | null
  status: "VALID" | "EXPIRED" | "REVOKED"; revokedReason: string | null; createdAt: string
}
interface CertificatePage { content: CertificateResponse[] }

const STATUS_COLORS: Record<string, string> = { VALID: "#059669", EXPIRED: "#D97706", REVOKED: "#DC2626" }
const btnStyle: React.CSSProperties = { background: "none", border: "1px solid #E2E8F0", borderRadius: 7, padding: "6px 10px", fontSize: 11.5, fontWeight: 600, color: "#64748B", cursor: "pointer", display: "flex", alignItems: "center", gap: 4 }

export default function TrainProvCertificatesTab() {
  const qc = useQueryClient()
  const [statusFilter, setStatusFilter] = useState<string>("")

  const { data, isLoading } = useQuery<CertificatePage>({
    queryKey: ["trainprov-certificates", statusFilter],
    queryFn: async () => (await apiClient.get("/api/v1/training-provider/certificates", { params: { status: statusFilter || undefined, size: 100 } })).data,
  })
  const certificates = data?.content ?? []

  // ADMIN-only server-side (TRAININGPROVIDER_ADMIN).
  const revoke = useMutation({
    mutationFn: async (id: string) => {
      const reason = prompt("Reason for revoking this certificate:") ?? undefined
      return apiClient.post(`/api/v1/training-provider/certificates/${id}/revoke`, reason ? { reason } : undefined)
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: ["trainprov-certificates"] }),
  })

  const downloadPdf = async (cert: CertificateResponse) => {
    const res = await apiClient.get(`/api/v1/training-provider/certificates/${cert.id}/pdf`, { responseType: "blob" })
    const url = window.URL.createObjectURL(new Blob([res.data]))
    const a = document.createElement("a")
    a.href = url; a.download = `${cert.certificateNumber}.pdf`
    document.body.appendChild(a); a.click(); a.remove()
    window.URL.revokeObjectURL(url)
  }

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 16 }}>
        <p style={{ fontSize: 13, color: "#94A3B8", margin: 0 }}>{certificates.length} certificate{certificates.length === 1 ? "" : "s"}</p>
        <select value={statusFilter} onChange={e => setStatusFilter(e.target.value)}
          style={{ padding: "6px 10px", border: "1px solid #E2E8F0", borderRadius: 8, fontSize: 12, fontFamily: "inherit" }}>
          <option value="">All statuses</option>
          <option value="VALID">Valid</option>
          <option value="EXPIRED">Expired</option>
          <option value="REVOKED">Revoked</option>
        </select>
      </div>

      <p style={{ fontSize: 11.5, color: "#94A3B8", margin: "0 0 14px" }}>
        Certificates are issued from a completed, passed enrollment on a session's detail page, not here.
      </p>

      {isLoading ? (
        <p style={{ color: "#94A3B8", fontSize: 13 }}>Loading…</p>
      ) : certificates.length === 0 ? (
        <p style={{ color: "#94A3B8", fontSize: 13 }}>No certificates issued yet.</p>
      ) : (
        <div style={{ border: "1px solid #E2E8F0", borderRadius: 12, overflow: "hidden" }}>
          {certificates.map((c, i) => (
            <div key={c.id} style={{ display: "flex", alignItems: "center", justifyContent: "space-between", padding: "13px 16px", borderTop: i === 0 ? "none" : "1px solid #F1F5F9" }}>
              <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
                <div style={{ width: 32, height: 32, borderRadius: 8, background: "#FFFBEB", display: "flex", alignItems: "center", justifyContent: "center" }}>
                  <Award size={15} color="#D97706" />
                </div>
                <div>
                  <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                    <p style={{ fontSize: 13.5, fontWeight: 700, color: "#0F172A", margin: 0 }}>{c.delegateNameSnapshot}</p>
                    <span style={{ fontSize: 10, fontWeight: 700, padding: "2px 8px", borderRadius: 20, background: `${STATUS_COLORS[c.status]}18`, color: STATUS_COLORS[c.status] }}>{c.status}</span>
                  </div>
                  <p style={{ fontSize: 11.5, color: "#94A3B8", margin: 0 }}>
                    {c.clientNameSnapshot} · {c.courseTitleSnapshot}{c.unitStandardSnapshot ? ` (US ${c.unitStandardSnapshot})` : ""} · {c.certificateNumber} · Issued {c.issueDate}{c.expiryDate ? ` · Expires ${c.expiryDate}` : " · No expiry"}
                  </p>
                  {c.revokedReason && <p style={{ fontSize: 11, color: "#DC2626", margin: "2px 0 0" }}>Revoked: {c.revokedReason}</p>}
                </div>
              </div>
              <div style={{ display: "flex", gap: 6 }}>
                <button onClick={() => downloadPdf(c)} style={btnStyle}><Download size={12} /> PDF</button>
                {c.status === "VALID" && (
                  <button onClick={() => revoke.mutate(c.id)} style={{ ...btnStyle, color: "#DC2626" }}><Ban size={12} /> Revoke</button>
                )}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

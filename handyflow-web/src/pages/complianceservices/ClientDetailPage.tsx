// src/pages/complianceservices/ClientDetailPage.tsx
//
// Routed at /complianceservices/clients/:clientId. Fetches the client
// once here and passes clientId down to each tab, matching
// CompliancePage.tsx's own tab-shell convention.
import { useState } from "react"
import { useParams, useNavigate, useSearchParams } from "react-router-dom"
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { ArrowLeft, Building2, ShieldCheck, FileText, CalendarClock, Briefcase, ListChecks } from "lucide-react"
import ClientRegistrationsTab from "./ClientRegistrationsTab"
import ClientDocumentsTab from "./ClientDocumentsTab"
import ClientDeadlinesTab from "./ClientDeadlinesTab"
import ClientTendersTab from "./ClientTendersTab"
import ClientRequirementsTab from "./ClientRequirementsTab"

interface Client {
  id: string; name: string; crmCustomerId: string | null; crmCustomerFound: boolean
  crmCustomerName: string | null; contactEmail: string | null; contactPhone: string | null
  mandateNotes: string | null; status: string
}

type Tab = "registrations" | "documents" | "deadlines" | "tenders" | "requirements"

const TABS: { id: Tab; label: string; icon: React.ElementType }[] = [
  { id: "registrations", label: "Registrations", icon: ShieldCheck   },
  { id: "documents",     label: "Documents",     icon: FileText      },
  { id: "deadlines",     label: "Deadlines",     icon: CalendarClock },
  { id: "tenders",       label: "Tenders",       icon: Briefcase     },
  { id: "requirements",  label: "Requirements",  icon: ListChecks    },
]

const ACCENT = "var(--hf-success-solid-strong)"
const ACCENT_TEXT = "var(--hf-success-text-strong)";
const unwrap = (r: any) => r.data?.data ?? r.data

export default function ClientDetailPage() {
  const { clientId } = useParams<{ clientId: string }>()
  const nav = useNavigate()
  // a link can open a tab directly (?tab=requirements), for example from a client tender's readiness check
  const [searchParams] = useSearchParams()
  const wanted = searchParams.get("tab")
  const [tab, setTab] = useState<Tab>(TABS.some(t => t.id === wanted) ? (wanted as Tab) : "registrations")

  const { data: client, isLoading } = useQuery<Client>({
    queryKey: ["cs-client", clientId],
    queryFn: async () => unwrap(await apiClient.get(`/api/v1/compliance-services/clients/${clientId}`)),
    enabled: !!clientId,
  })

  if (isLoading || !client || !clientId) return <div style={{ padding: 40, textAlign: "center", color: "var(--hf-text-faint)" }}>Loading client...</div>

  return (
    <div style={{ fontFamily: "'Inter', system-ui, sans-serif", maxWidth: 1000, margin: "0 auto", padding: "0 4px" }}>
      <button onClick={() => nav("/complianceservices")} style={{ display: "flex", alignItems: "center", gap: 6, background: "none", border: "none", cursor: "pointer", color: "var(--hf-text-muted)", fontSize: 13, marginBottom: 18, padding: 0 }}>
        <ArrowLeft size={15} /> Back to Clients
      </button>

      <div style={{ display: "flex", alignItems: "center", gap: 14, marginBottom: 24 }}>
        <div style={{ width: 48, height: 48, borderRadius: 12, background: "var(--hf-success-soft)", display: "flex", alignItems: "center", justifyContent: "center" }}>
          <Building2 size={22} style={{ color: ACCENT_TEXT }} />
        </div>
        <div>
          <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
            <h1 style={{ margin: 0, fontSize: 20, fontWeight: 700, color: "var(--hf-text)" }}>{client.name}</h1>
            <span style={{ background: client.status === "ACTIVE" ? "var(--hf-success-soft-strong)" : "var(--hf-surface-sunken)", color: client.status === "ACTIVE" ? "var(--hf-success-text-strong)" : "var(--hf-text-muted)", padding: "4px 12px", borderRadius: 20, fontSize: 12, fontWeight: 700 }}>
              {client.status === "ACTIVE" ? "Active" : "Inactive"}
            </span>
          </div>
          <div style={{ fontSize: 13, color: "var(--hf-text-faint)", marginTop: 3 }}>
            {client.crmCustomerId ? (client.crmCustomerFound ? `Linked to CRM: ${client.crmCustomerName}` : "CRM link no longer available") : "No CRM link"}
            {client.contactEmail ? ` · ${client.contactEmail}` : ""}
          </div>
        </div>
      </div>

      {client.mandateNotes && (
        <div style={{ marginBottom: 20, padding: "12px 16px", background: "var(--hf-surface-muted)", border: "1px solid var(--hf-border)", borderRadius: 10, fontSize: 13, color: "var(--hf-text-secondary)" }}>
          <strong>Mandate:</strong> {client.mandateNotes}
        </div>
      )}

      <div style={{ background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 14, padding: 24 }}>
        <div style={{ display: "flex", gap: 2, borderBottom: "1px solid var(--hf-border)", marginBottom: 28, overflowX: "auto" }}>
          {TABS.map(t => {
            const Icon = t.icon
            const active = tab === t.id
            return (
              <button key={t.id} onClick={() => setTab(t.id)}
                style={{
                  display: "flex", alignItems: "center", gap: 6, padding: "10px 16px",
                  background: "none", border: "none", whiteSpace: "nowrap",
                  borderBottom: active ? `2px solid ${ACCENT}` : "2px solid transparent",
                  color: active ? ACCENT_TEXT : "var(--hf-text-muted)",
                  fontWeight: active ? 600 : 400, fontSize: 13, cursor: "pointer",
                  marginBottom: -1,
                }}>
                <Icon size={14} />{t.label}
              </button>
            )
          })}
        </div>

        {tab === "registrations" && <ClientRegistrationsTab clientId={clientId} />}
        {tab === "documents"     && <ClientDocumentsTab clientId={clientId} />}
        {tab === "deadlines"     && <ClientDeadlinesTab clientId={clientId} />}
        {tab === "tenders"       && <ClientTendersTab clientId={clientId} />}
        {tab === "requirements"  && <ClientRequirementsTab clientId={clientId} />}
      </div>
    </div>
  )
}

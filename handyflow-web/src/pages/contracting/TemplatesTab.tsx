import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { Plus, X, Layout, ChevronDown, ChevronUp, Lock } from "lucide-react"

interface Template {
  id: string
  name: string
  contractType: string
  description: string
  bodyTemplate: string
  variables: Record<string, string>
  isSystem: boolean
}

const CONTRACT_TYPES = [
  "SERVICE_AGREEMENT", "EMPLOYMENT", "NDA", "LEASE", "SUPPLY",
  "PARTNERSHIP", "MAINTENANCE", "CONSULTING", "RETAINER", "OTHER",
]

const TYPE_COLORS: Record<string, string> = {
  SERVICE_AGREEMENT: "#0D9488",
  EMPLOYMENT:        "#1D4ED8",
  NDA:               "#7C3AED",
  LEASE:             "#D97706",
  SUPPLY:            "#166534",
  PARTNERSHIP:       "#DB2777",
  MAINTENANCE:       "#0891B2",
  CONSULTING:        "#DC2626",
  RETAINER:          "#854D0E",
}

export default function TemplatesTab() {
  const qc = useQueryClient()
  const [showCreate, setShowCreate] = useState(false)
  const [expanded, setExpanded]     = useState<string | null>(null)
  const [error, setError]           = useState("")

  const [form, setForm] = useState({
    name: "", contractType: "SERVICE_AGREEMENT",
    description: "", bodyTemplate: "",
  })
  const f = (k: keyof typeof form, v: string) => setForm(p => ({ ...p, [k]: v }))

  const { data: templates = [], isLoading } = useQuery<Template[]>({
    queryKey: ["contract-templates"],
    queryFn: async () => {
      const r = await apiClient.get("/api/v1/contracts/templates")
      return r.data || []
    },
  })

  const createTemplate = useMutation({
    mutationFn: (body: any) => apiClient.post("/api/v1/contracts/templates", body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["contract-templates"] })
      setShowCreate(false)
      setForm({ name: "", contractType: "SERVICE_AGREEMENT", description: "", bodyTemplate: "" })
    },
    onError: (e: any) => setError(e.response?.data?.message || "Failed to create template"),
  })

  const systemTemplates = templates.filter(t => t.isSystem)
  const customTemplates = templates.filter(t => !t.isSystem)

  if (isLoading) return <div style={{ textAlign: "center", padding: 40, color: "#94A3B8" }}>Loading templates...</div>

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 20 }}>
        <div>
          <div style={{ fontSize: 13, color: "#64748B" }}>
            <span style={{ fontWeight: 600, color: "#0F172A" }}>{systemTemplates.length}</span> system templates ·&nbsp;
            <span style={{ fontWeight: 600, color: "#0F172A" }}>{customTemplates.length}</span> custom templates
          </div>
        </div>
        <button onClick={() => { setShowCreate(true); setError("") }} style={btnPrimary}><Plus size={15} /> New Template</button>
      </div>

      {/* System templates */}
      {systemTemplates.length > 0 && (
        <div style={{ marginBottom: 24 }}>
          <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 12 }}>
            <Lock size={13} color="#94A3B8" />
            <span style={{ fontSize: 12, fontWeight: 600, color: "#94A3B8", letterSpacing: "0.05em" }}>SYSTEM TEMPLATES</span>
          </div>
          <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(300px, 1fr))", gap: 12 }}>
            {systemTemplates.map(t => (
              <TemplateCard key={t.id} template={t} expanded={expanded === t.id} onToggle={() => setExpanded(expanded === t.id ? null : t.id)} />
            ))}
          </div>
        </div>
      )}

      {/* Custom templates */}
      {customTemplates.length > 0 && (
        <div>
          <div style={{ fontSize: 12, fontWeight: 600, color: "#94A3B8", letterSpacing: "0.05em", marginBottom: 12 }}>CUSTOM TEMPLATES</div>
          <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(300px, 1fr))", gap: 12 }}>
            {customTemplates.map(t => (
              <TemplateCard key={t.id} template={t} expanded={expanded === t.id} onToggle={() => setExpanded(expanded === t.id ? null : t.id)} />
            ))}
          </div>
        </div>
      )}

      {templates.length === 0 && (
        <div style={{ textAlign: "center", padding: "60px 20px", color: "#94A3B8" }}>
          <Layout size={40} style={{ marginBottom: 12, opacity: 0.4 }} />
          <div style={{ fontWeight: 600, color: "#475569" }}>No templates yet</div>
          <div style={{ fontSize: 14, marginTop: 4 }}>Templates will auto-seed on first contract creation.</div>
        </div>
      )}

      {/* Create template modal */}
      {showCreate && (
        <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000 }}>
          <div style={{ background: "#fff", borderRadius: 14, padding: 28, width: 640, maxHeight: "90vh", overflowY: "auto", boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 22 }}>
              <h3 style={{ margin: 0, fontSize: 17, fontWeight: 700, color: "#0F172A" }}>New Template</h3>
              <button onClick={() => setShowCreate(false)} style={{ background: "none", border: "none", cursor: "pointer", color: "#94A3B8" }}><X size={20} /></button>
            </div>

            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14, marginBottom: 14 }}>
              <div style={{ gridColumn: "1 / -1" }}>
                <Field label="Template Name *"><input value={form.name} onChange={e => f("name", e.target.value)} placeholder="Standard NDA" style={inputStyle} /></Field>
              </div>
              <Field label="Contract Type *">
                <select value={form.contractType} onChange={e => f("contractType", e.target.value)} style={inputStyle}>
                  {CONTRACT_TYPES.map(t => <option key={t} value={t}>{t.replace(/_/g, " ")}</option>)}
                </select>
              </Field>
              <Field label="Description">
                <input value={form.description} onChange={e => f("description", e.target.value)} placeholder="Brief description..." style={inputStyle} />
              </Field>
            </div>

            <Field label="Template Body">
              <div style={{ marginBottom: 6, fontSize: 12, color: "#64748B" }}>
                Use <code style={{ background: "#F1F5F9", padding: "1px 4px", borderRadius: 3 }}>{"{{VARIABLE_NAME}}"}</code> for dynamic fields.
                Common: <code style={{ background: "#F1F5F9", padding: "1px 4px", borderRadius: 3 }}>{"{{PARTY_NAME}}"}</code>&nbsp;
                <code style={{ background: "#F1F5F9", padding: "1px 4px", borderRadius: 3 }}>{"{{START_DATE}}"}</code>&nbsp;
                <code style={{ background: "#F1F5F9", padding: "1px 4px", borderRadius: 3 }}>{"{{CONTRACT_VALUE}}"}</code>
              </div>
              <textarea
                value={form.bodyTemplate}
                onChange={e => f("bodyTemplate", e.target.value)}
                rows={12}
                placeholder={`This agreement is entered into between {{PARTY_NAME}} (hereinafter "the Client") and {{COMPANY_NAME}} (hereinafter "the Service Provider").\n\nCommencement Date: {{START_DATE}}\nContract Value: {{CONTRACT_VALUE}}\n\n...`}
                style={{ ...inputStyle, fontFamily: "monospace", fontSize: 12, resize: "vertical" as const }}
              />
            </Field>

            {error && <div style={{ marginTop: 10, color: "#DC2626", fontSize: 13 }}>{error}</div>}
            <div style={{ display: "flex", justifyContent: "flex-end", gap: 10, marginTop: 20 }}>
              <button onClick={() => setShowCreate(false)} style={btnCancel}>Cancel</button>
              <button onClick={() => createTemplate.mutate(form)} disabled={!form.name || createTemplate.isPending} style={btnPrimary}>
                {createTemplate.isPending ? "Creating..." : "Create Template"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

function TemplateCard({ template, expanded, onToggle }: { template: Template; expanded: boolean; onToggle: () => void }) {
  const color = TYPE_COLORS[template.contractType] || "#64748B"
  const varCount = template.variables ? Object.keys(template.variables).length : 0

  return (
    <div style={{ border: "1px solid #E2E8F0", borderRadius: 10, overflow: "hidden" }}>
      <div style={{ height: 3, background: color }} />
      <div style={{ padding: "14px 16px" }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", marginBottom: 6 }}>
          <div>
            <div style={{ fontWeight: 700, fontSize: 14, color: "#0F172A", marginBottom: 3 }}>{template.name}</div>
            <span style={{ background: `${color}18`, color, padding: "2px 8px", borderRadius: 20, fontSize: 11, fontWeight: 600 }}>
              {template.contractType.replace(/_/g, " ")}
            </span>
          </div>
          {template.isSystem && (
            <span style={{ background: "#F1F5F9", color: "#64748B", padding: "2px 7px", borderRadius: 4, fontSize: 10, fontWeight: 600 }}>SYSTEM</span>
          )}
        </div>

        {template.description && (
          <div style={{ fontSize: 12, color: "#64748B", marginTop: 8, lineHeight: 1.5 }}>{template.description}</div>
        )}

        {varCount > 0 && (
          <div style={{ marginTop: 10, display: "flex", gap: 4, flexWrap: "wrap" }}>
            {Object.keys(template.variables).slice(0, 4).map(v => (
              <span key={v} style={{ background: "#F8FAFC", color: "#475569", padding: "1px 7px", borderRadius: 4, fontSize: 10, fontFamily: "monospace" }}>
                {`{{${v}}}`}
              </span>
            ))}
            {varCount > 4 && <span style={{ fontSize: 10, color: "#94A3B8" }}>+{varCount - 4} more</span>}
          </div>
        )}

        {template.bodyTemplate && (
          <button onClick={onToggle}
            style={{ display: "flex", alignItems: "center", gap: 4, marginTop: 10, background: "none", border: "none", color: "#0D9488", fontSize: 12, cursor: "pointer", padding: 0 }}>
            {expanded ? <ChevronUp size={13} /> : <ChevronDown size={13} />}
            {expanded ? "Hide" : "Preview"} template
          </button>
        )}
      </div>

      {expanded && template.bodyTemplate && (
        <div style={{ borderTop: "1px solid #E2E8F0", padding: "12px 16px", background: "#FAFAFA" }}>
          <pre style={{ margin: 0, fontSize: 11, color: "#475569", fontFamily: "monospace", whiteSpace: "pre-wrap", lineHeight: 1.6, maxHeight: 240, overflowY: "auto" }}>
            {template.bodyTemplate}
          </pre>
        </div>
      )}
    </div>
  )
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return <div><label style={{ display: "block", fontSize: 13, fontWeight: 500, color: "#374151", marginBottom: 5 }}>{label}</label>{children}</div>
}

const btnPrimary: React.CSSProperties = { display: "flex", alignItems: "center", gap: 7, background: "#1B3A6B", color: "#fff", border: "none", borderRadius: 8, padding: "9px 18px", fontSize: 14, fontWeight: 500, cursor: "pointer" }
const btnCancel: React.CSSProperties  = { padding: "9px 18px", border: "1px solid #E2E8F0", borderRadius: 8, background: "#fff", fontSize: 14, cursor: "pointer", color: "#374151" }
const inputStyle: React.CSSProperties = { width: "100%", padding: "9px 12px", border: "1px solid #E2E8F0", borderRadius: 8, fontSize: 14, boxSizing: "border-box" as const, background: "#fff" }

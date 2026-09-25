// src/pages/security/PostOrdersTab.tsx
//
// Post Orders / My Post, per the product owner's own explicit design —
// see PostOrderService's own doc comment (backend) for the fuller
// design context. This is the supervisor-facing management UI; the
// guard-facing "My Post" screen lives in the Shield mobile app, a
// separate repository this session has no access to.

import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import {
  ClipboardList, Plus, ChevronDown, ChevronUp, MapPin, Users2, Phone,
  CheckCircle2, Clock, FileText, Send,
} from "lucide-react"

// ── Types ──────────────────────────────────────────────────────────────────────

interface SiteOption { id: string; name: string }
interface PostOption { id: string; siteId: string; name: string; description: string | null; active: boolean }
interface ContactOption { id: string; siteId: string | null; name: string; role: string; phone: string | null; email: string | null; active: boolean }
interface PostOrderAttachment { id: string; fileUrl: string; fileName: string }
interface PostOrderResp {
  id: string; siteId: string; postId: string | null; postName: string | null
  version: number; status: string; effectiveFrom: string | null; effectiveTo: string | null
  instructions: string | null; duties: string | null; emergencyProcedures: string | null
  restrictedAreas: string | null; accessRules: string | null
  createdBy: string | null; publishedBy: string | null; publishedAt: string | null; createdAt: string
  contacts: ContactOption[]; attachments: PostOrderAttachment[]
}
interface AckResp {
  id: string; postOrderId: string; version: number; guardId: string; guardName: string
  acknowledgedAt: string; deviceHardwareId: string | null; acknowledgedOffline: boolean; syncedAt: string | null
}

const ROLES = ["SITE_MANAGER", "CLIENT_CONTACT", "SECURITY_MANAGER", "CONTROL_ROOM", "POLICE", "AMBULANCE", "FIRE", "OTHER"]

const lbl: React.CSSProperties = { display: "block", fontSize: 13, fontWeight: 600, color: "var(--hf-text-secondary)", marginBottom: 5 }
const inp: React.CSSProperties = { width: "100%", padding: "9px 12px", border: "1.5px solid var(--hf-border)", borderRadius: 8, fontSize: 14, boxSizing: "border-box" as const, background: "var(--hf-surface)", outline: "none" }
const cancelBtn: React.CSSProperties = { padding: "9px 18px", border: "1px solid var(--hf-border)", borderRadius: 9, background: "var(--hf-surface)", fontSize: 14, cursor: "pointer", color: "var(--hf-text-secondary)" }
const submitBtn: React.CSSProperties = { padding: "9px 18px", border: "none", borderRadius: 9, background: "var(--hf-primary)", color: "var(--hf-text-on-solid)", fontSize: 14, fontWeight: 600, cursor: "pointer" }
const modalOverlay: React.CSSProperties = { position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000 }
const modalBox: React.CSSProperties = { background: "var(--hf-surface)", borderRadius: 14, padding: 26, width: 540, maxHeight: "85vh", overflowY: "auto" as const }

function StatusBadge({ status }: { status: string }) {
  const cfg: Record<string, { bg: string; color: string }> = {
    DRAFT: { bg: "var(--hf-surface-sunken)", color: "var(--hf-text-muted)" },
    ACTIVE: { bg: "var(--hf-success-soft-strong)", color: "var(--hf-success-text-strong)" },
    SUPERSEDED: { bg: "var(--hf-warning-soft)", color: "var(--hf-warning-text-strong)" },
    ARCHIVED: { bg: "var(--hf-danger-soft)", color: "var(--hf-danger-text)" },
  }
  const c = cfg[status] ?? cfg.DRAFT
  return <span style={{ fontSize: 11, fontWeight: 700, padding: "3px 10px", borderRadius: 20, background: c.bg, color: c.color }}>{status}</span>
}

export default function PostOrdersTab() {
  const qc = useQueryClient()
  const [siteId, setSiteId] = useState<string>("")
  const [subTab, setSubTab] = useState<"site-order" | "posts" | "contacts">("site-order")

  const { data: sites = [] } = useQuery<SiteOption[]>({
    queryKey: ["po-sites"],
    queryFn: async () => {
      const res = await apiClient.get("/api/v1/security/sites?size=100")
      const payload = res.data?.data ?? res.data
      return ((payload?.content ?? payload) as any[]).map(s => ({ id: s.id, name: s.name }))
    },
  })

  return (
    <div style={{ padding: "24px 28px" }}>
      <div style={{ marginBottom: 18 }}>
        <h1 style={{ margin: 0, fontSize: 22, fontWeight: 800, color: "var(--hf-text)" }}>Post Orders</h1>
        <div style={{ fontSize: 13, color: "var(--hf-text-muted)", marginTop: 2 }}>Site and post instructions — versioned, acknowledgment-tracked. The guard-facing "My Post" view lives in the Shield app.</div>
      </div>

      <div style={{ marginBottom: 18, maxWidth: 320 }}>
        <label style={lbl}>Site</label>
        <select value={siteId} onChange={e => setSiteId(e.target.value)} style={inp}>
          <option value="">Select a site…</option>
          {sites.map(s => <option key={s.id} value={s.id}>{s.name}</option>)}
        </select>
      </div>

      {!siteId ? (
        <div style={{ textAlign: "center", padding: "50px 20px", color: "var(--hf-text-faint)", background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 12 }}>
          <ClipboardList size={30} style={{ marginBottom: 10, opacity: 0.3 }} />
          Select a site to manage its post orders.
        </div>
      ) : (
        <>
          <div style={{ display: "flex", gap: 6, marginBottom: 20, borderBottom: "1px solid var(--hf-border)" }}>
            {([["site-order", "Site-Level Order", MapPin], ["posts", "Posts", Users2], ["contacts", "Contacts", Phone]] as const).map(([id, label, Icon]) => (
              <button key={id} onClick={() => setSubTab(id)}
                style={{ display: "flex", alignItems: "center", gap: 6, padding: "10px 16px", background: "none", border: "none", borderBottom: subTab === id ? "2px solid var(--hf-primary)" : "2px solid transparent", color: subTab === id ? "var(--hf-primary-text)" : "var(--hf-text-muted)", fontWeight: subTab === id ? 700 : 500, fontSize: 13, cursor: "pointer" }}>
                <Icon size={15} /> {label}
              </button>
            ))}
          </div>

          {subTab === "site-order" && <PostOrderPanel siteId={siteId} postId={null} qc={qc} />}
          {subTab === "posts" && <PostsPanel siteId={siteId} qc={qc} />}
          {subTab === "contacts" && <ContactsPanel siteId={siteId} qc={qc} />}
        </>
      )}
    </div>
  )
}

// ── Reusable order panel (site-level when postId=null, post-level otherwise) ───

function PostOrderPanel({ siteId, postId, qc }: { siteId: string; postId: string | null; qc: ReturnType<typeof useQueryClient> }) {
  const [error, setError] = useState("")
  const [showHistory, setShowHistory] = useState(false)
  const [showNewDraft, setShowNewDraft] = useState(false)
  const [showAcks, setShowAcks] = useState(false)
  const [form, setForm] = useState({ instructions: "", duties: "", emergencyProcedures: "", restrictedAreas: "", accessRules: "", contactIds: [] as string[] })

  const historyKey = ["po-history", siteId, postId]
  const { data: history = [], isLoading } = useQuery<PostOrderResp[]>({
    queryKey: historyKey,
    queryFn: async () => (await apiClient.get(`/api/v1/security/sites/${siteId}/post-orders/history${postId ? `?postId=${postId}` : ""}`)).data.data ?? (await apiClient.get(`/api/v1/security/sites/${siteId}/post-orders/history${postId ? `?postId=${postId}` : ""}`)).data,
  })
  const current = history.find(o => o.status === "ACTIVE") ?? null
  const draft = history.find(o => o.status === "DRAFT") ?? null

  const { data: contacts = [] } = useQuery<ContactOption[]>({
    queryKey: ["po-contacts-for-site", siteId],
    queryFn: async () => (await apiClient.get(`/api/v1/security/sites/${siteId}/contacts`)).data.data ?? [],
    enabled: showNewDraft,
  })
  const acksQuery = useQuery<AckResp[]>({
    queryKey: ["po-acks", current?.id],
    queryFn: async () => (await apiClient.get(`/api/v1/security/post-orders/${current!.id}/acknowledgements`)).data.data ?? [],
    enabled: showAcks && !!current,
  })

  const createDraft = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/security/sites/${siteId}/post-orders`, { postId, ...form, contactIds: form.contactIds }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: historyKey }); setShowNewDraft(false); setError("") },
    onError: (e: any) => setError(e.response?.data?.message ?? "Failed to create draft"),
  })
  const publish = useMutation({
    mutationFn: (id: string) => apiClient.post(`/api/v1/security/post-orders/${id}/publish`),
    onSuccess: () => qc.invalidateQueries({ queryKey: historyKey }),
    onError: (e: any) => setError(e.response?.data?.message ?? "Failed to publish"),
  })

  const openNewDraft = () => {
    setForm({
      instructions: current?.instructions ?? "", duties: current?.duties ?? "",
      emergencyProcedures: current?.emergencyProcedures ?? "", restrictedAreas: current?.restrictedAreas ?? "",
      accessRules: current?.accessRules ?? "", contactIds: current?.contacts?.map(c => c.id) ?? [],
    })
    setShowNewDraft(true); setError("")
  }

  return (
    <div>
      {error && <div style={{ padding: "10px 14px", background: "var(--hf-danger-soft)", border: "1px solid var(--hf-danger-border)", borderRadius: 8, color: "var(--hf-danger-text)", fontSize: 13, marginBottom: 14 }}>{error}</div>}

      {isLoading ? (
        <p style={{ color: "var(--hf-text-faint)" }}>Loading…</p>
      ) : (
        <div style={{ background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 12, padding: 20, marginBottom: 16 }}>
          {!current ? (
            <div style={{ textAlign: "center", padding: "20px 0", color: "var(--hf-text-faint)" }}>
              No published order yet{postId ? " for this post" : " for this site"}.
            </div>
          ) : (
            <>
              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 14 }}>
                <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
                  <span style={{ fontWeight: 700, fontSize: 15, color: "var(--hf-text)" }}>Version {current.version}</span>
                  <StatusBadge status={current.status} />
                </div>
                <button onClick={() => setShowAcks(v => !v)}
                  style={{ display: "flex", alignItems: "center", gap: 5, padding: "6px 12px", borderRadius: 7, border: "1px solid var(--hf-border)", background: "var(--hf-surface)", color: "var(--hf-text-muted)", fontSize: 12, fontWeight: 600, cursor: "pointer" }}>
                  <CheckCircle2 size={13} /> Acknowledgments
                </button>
              </div>
              {current.instructions && <Field label="Instructions" value={current.instructions} />}
              {current.duties && <Field label="Duties" value={current.duties} />}
              {current.emergencyProcedures && <Field label="Emergency Procedures" value={current.emergencyProcedures} />}
              {current.restrictedAreas && <Field label="Restricted Areas" value={current.restrictedAreas} />}
              {current.accessRules && <Field label="Access Rules" value={current.accessRules} />}
              {current.contacts.length > 0 && (
                <div style={{ marginTop: 10 }}>
                  <div style={{ fontSize: 11, fontWeight: 700, color: "var(--hf-text-faint)", textTransform: "uppercase" as const, marginBottom: 6 }}>Contacts</div>
                  <div style={{ display: "flex", flexWrap: "wrap" as const, gap: 6 }}>
                    {current.contacts.map(c => (
                      <span key={c.id} style={{ fontSize: 11, padding: "4px 10px", borderRadius: 20, background: "var(--hf-surface-sunken)", color: "var(--hf-text-secondary)" }}>
                        {c.name} ({c.role.replace(/_/g, " ")}){c.phone ? ` — ${c.phone}` : ""}
                      </span>
                    ))}
                  </div>
                </div>
              )}

              {showAcks && (
                <div style={{ marginTop: 16, paddingTop: 14, borderTop: "1px solid var(--hf-border-subtle)" }}>
                  <div style={{ fontSize: 11, fontWeight: 700, color: "var(--hf-text-faint)", textTransform: "uppercase" as const, marginBottom: 8 }}>
                    Acknowledged v{current.version} — {acksQuery.data?.length ?? 0} guards
                  </div>
                  {!acksQuery.data?.length ? (
                    <p style={{ fontSize: 12, color: "var(--hf-text-faint)" }}>No guard has acknowledged this version yet.</p>
                  ) : (
                    <div style={{ display: "flex", flexDirection: "column", gap: 5 }}>
                      {acksQuery.data.map(a => (
                        <div key={a.id} style={{ display: "flex", justifyContent: "space-between", fontSize: 12, padding: "6px 10px", background: "var(--hf-surface-muted)", borderRadius: 6 }}>
                          <span style={{ fontWeight: 600 }}>{a.guardName}</span>
                          <span style={{ color: "var(--hf-text-faint)" }}>
                            {new Date(a.acknowledgedAt).toLocaleString()}{a.acknowledgedOffline && !a.syncedAt ? " (offline, pending sync)" : ""}
                          </span>
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              )}
            </>
          )}
        </div>
      )}

      <div style={{ display: "flex", gap: 10, marginBottom: 16 }}>
        {draft ? (
          <>
            <span style={{ display: "flex", alignItems: "center", gap: 6, fontSize: 12, color: "var(--hf-warning-text-strong)" }}>
              <Clock size={13} /> A draft (v{draft.version}) is waiting to be published.
            </span>
            <button onClick={() => publish.mutate(draft.id)} disabled={publish.isPending}
              style={{ display: "flex", alignItems: "center", gap: 6, padding: "7px 14px", background: "var(--hf-primary)", color: "var(--hf-text-on-solid)", border: "none", borderRadius: 7, fontSize: 12, fontWeight: 600, cursor: "pointer" }}>
              <Send size={13} /> {publish.isPending ? "Publishing…" : "Publish Draft"}
            </button>
          </>
        ) : (
          <button onClick={openNewDraft}
            style={{ display: "flex", alignItems: "center", gap: 6, padding: "8px 16px", background: "var(--hf-primary)", color: "var(--hf-text-on-solid)", border: "none", borderRadius: 8, fontSize: 13, fontWeight: 600, cursor: "pointer" }}>
            <Plus size={14} /> {current ? "New Version" : "Create Order"}
          </button>
        )}
        {history.length > 0 && (
          <button onClick={() => setShowHistory(v => !v)}
            style={{ display: "flex", alignItems: "center", gap: 6, padding: "7px 14px", border: "1px solid var(--hf-border)", background: "var(--hf-surface)", color: "var(--hf-text-muted)", borderRadius: 7, fontSize: 12, fontWeight: 600, cursor: "pointer" }}>
            <FileText size={13} /> History ({history.length})
          </button>
        )}
      </div>

      {showHistory && (
        <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
          {history.map(o => (
            <div key={o.id} style={{ display: "flex", justifyContent: "space-between", padding: "9px 12px", background: "var(--hf-surface-muted)", border: "1px solid var(--hf-border)", borderRadius: 8, fontSize: 12 }}>
              <span>Version {o.version}</span>
              <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                <span style={{ color: "var(--hf-text-faint)" }}>{o.publishedAt ? new Date(o.publishedAt).toLocaleDateString() : "unpublished"}</span>
                <StatusBadge status={o.status} />
              </div>
            </div>
          ))}
        </div>
      )}

      {showNewDraft && (
        <div style={modalOverlay}>
          <div style={modalBox}>
            <h3 style={{ margin: "0 0 6px", fontSize: 16, fontWeight: 700, color: "var(--hf-text)" }}>{postId ? "New Post Order Version" : "New Site Order Version"}</h3>
            <p style={{ fontSize: 11, color: "var(--hf-text-faint)", marginBottom: 16 }}>Prefilled from the current version — edit and save as a draft, then publish separately when ready.</p>
            {error && <p style={{ color: "var(--hf-danger-text)", fontSize: 12, marginBottom: 12 }}>{error}</p>}
            {(["instructions", "duties", "emergencyProcedures", "restrictedAreas", "accessRules"] as const).map(field => (
              <div key={field} style={{ marginBottom: 12 }}>
                <label style={lbl}>{field.replace(/([A-Z])/g, " $1").replace(/^./, c => c.toUpperCase())}</label>
                <textarea value={form[field]} onChange={e => setForm(f => ({ ...f, [field]: e.target.value }))} rows={2} style={{ ...inp, fontFamily: "inherit", resize: "vertical" as const }} />
              </div>
            ))}
            <div style={{ marginBottom: 18 }}>
              <label style={lbl}>Contacts</label>
              <div style={{ display: "flex", flexWrap: "wrap" as const, gap: 6 }}>
                {contacts.map(c => {
                  const selected = form.contactIds.includes(c.id)
                  return (
                    <button key={c.id} type="button"
                      onClick={() => setForm(f => ({ ...f, contactIds: selected ? f.contactIds.filter(id => id !== c.id) : [...f.contactIds, c.id] }))}
                      style={{ fontSize: 11, padding: "5px 11px", borderRadius: 20, border: selected ? "1px solid var(--hf-primary)" : "1px solid var(--hf-border)", background: selected ? "var(--hf-info-soft)" : "var(--hf-surface)", color: selected ? "var(--hf-primary-text)" : "var(--hf-text-muted)", cursor: "pointer", fontWeight: selected ? 700 : 500 }}>
                      {c.name}
                    </button>
                  )
                })}
                {contacts.length === 0 && <span style={{ fontSize: 12, color: "var(--hf-text-faint)" }}>No contacts yet for this site — add some in the Contacts tab.</span>}
              </div>
            </div>
            <div style={{ display: "flex", justifyContent: "flex-end", gap: 10 }}>
              <button onClick={() => setShowNewDraft(false)} style={cancelBtn}>Cancel</button>
              <button onClick={() => createDraft.mutate()} disabled={createDraft.isPending} style={submitBtn}>
                {createDraft.isPending ? "Saving…" : "Save Draft"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

function Field({ label, value }: { label: string; value: string }) {
  return (
    <div style={{ marginBottom: 10 }}>
      <div style={{ fontSize: 11, fontWeight: 700, color: "var(--hf-text-faint)", textTransform: "uppercase" as const, marginBottom: 3 }}>{label}</div>
      <div style={{ fontSize: 13, color: "var(--hf-text-secondary)", whiteSpace: "pre-wrap" as const }}>{value}</div>
    </div>
  )
}

// ── Posts panel ──────────────────────────────────────────────────────────────

function PostsPanel({ siteId, qc }: { siteId: string; qc: ReturnType<typeof useQueryClient> }) {
  const [error, setError] = useState("")
  const [selectedPost, setSelectedPost] = useState<string | null>(null)
  const [showNew, setShowNew] = useState(false)
  const [form, setForm] = useState({ name: "", description: "" })

  const { data: posts = [], isLoading } = useQuery<PostOption[]>({
    queryKey: ["po-posts", siteId],
    queryFn: async () => (await apiClient.get(`/api/v1/security/sites/${siteId}/posts`)).data.data ?? [],
  })

  const createPost = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/security/sites/${siteId}/posts`, form),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["po-posts", siteId] }); setShowNew(false); setError("") },
    onError: (e: any) => setError(e.response?.data?.message ?? "Failed to create post"),
  })

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "flex-end", marginBottom: 12 }}>
        <button onClick={() => { setForm({ name: "", description: "" }); setShowNew(true); setError("") }}
          style={{ display: "flex", alignItems: "center", gap: 6, padding: "8px 16px", background: "var(--hf-primary)", color: "var(--hf-text-on-solid)", border: "none", borderRadius: 8, fontSize: 13, fontWeight: 600, cursor: "pointer" }}>
          <Plus size={14} /> New Post
        </button>
      </div>
      {error && <div style={{ padding: "10px 14px", background: "var(--hf-danger-soft)", border: "1px solid var(--hf-danger-border)", borderRadius: 8, color: "var(--hf-danger-text)", fontSize: 13, marginBottom: 14 }}>{error}</div>}

      {isLoading ? (
        <p style={{ color: "var(--hf-text-faint)" }}>Loading…</p>
      ) : posts.length === 0 ? (
        <div style={{ textAlign: "center", padding: "40px 20px", color: "var(--hf-text-faint)", background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 12 }}>
          No posts yet at this site — e.g. Main Gate, Loading Bay, Control Room.
        </div>
      ) : (
        <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
          {posts.map(p => (
            <div key={p.id} style={{ background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 10 }}>
              <div onClick={() => setSelectedPost(selectedPost === p.id ? null : p.id)}
                style={{ display: "flex", justifyContent: "space-between", alignItems: "center", padding: "12px 16px", cursor: "pointer" }}>
                <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                  {selectedPost === p.id ? <ChevronUp size={14} style={{ color: 'var(--hf-text-faint)' }} /> : <ChevronDown size={14} style={{ color: 'var(--hf-text-faint)' }} />}
                  <span style={{ fontWeight: 700, fontSize: 14, color: "var(--hf-text)" }}>{p.name}</span>
                </div>
                {p.description && <span style={{ fontSize: 12, color: "var(--hf-text-faint)" }}>{p.description}</span>}
              </div>
              {selectedPost === p.id && (
                <div style={{ padding: "0 16px 16px", borderTop: "1px solid var(--hf-border-subtle)" }}>
                  <div style={{ paddingTop: 14 }}>
                    <PostOrderPanel siteId={siteId} postId={p.id} qc={qc} />
                  </div>
                </div>
              )}
            </div>
          ))}
        </div>
      )}

      {showNew && (
        <div style={modalOverlay}>
          <div style={{ ...modalBox, width: 400 }}>
            <h3 style={{ margin: "0 0 16px", fontSize: 16, fontWeight: 700, color: "var(--hf-text)" }}>New Post</h3>
            {error && <p style={{ color: "var(--hf-danger-text)", fontSize: 12, marginBottom: 12 }}>{error}</p>}
            <div style={{ marginBottom: 14 }}>
              <label style={lbl}>Name *</label>
              <input value={form.name} onChange={e => setForm(f => ({ ...f, name: e.target.value }))} placeholder="Main Gate" style={inp} />
            </div>
            <div style={{ marginBottom: 18 }}>
              <label style={lbl}>Description</label>
              <input value={form.description} onChange={e => setForm(f => ({ ...f, description: e.target.value }))} style={inp} />
            </div>
            <div style={{ display: "flex", justifyContent: "flex-end", gap: 10 }}>
              <button onClick={() => setShowNew(false)} style={cancelBtn}>Cancel</button>
              <button onClick={() => createPost.mutate()} disabled={!form.name.trim() || createPost.isPending} style={submitBtn}>
                {createPost.isPending ? "Creating…" : "Create"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

// ── Contacts panel ───────────────────────────────────────────────────────────

function ContactsPanel({ siteId, qc }: { siteId: string; qc: ReturnType<typeof useQueryClient> }) {
  const [error, setError] = useState("")
  const [showNew, setShowNew] = useState(false)
  const [form, setForm] = useState({ name: "", role: "OTHER", phone: "", email: "" })

  const { data: contacts = [], isLoading } = useQuery<ContactOption[]>({
    queryKey: ["po-contacts-for-site", siteId],
    queryFn: async () => (await apiClient.get(`/api/v1/security/sites/${siteId}/contacts`)).data.data ?? [],
  })

  const createContact = useMutation({
    mutationFn: () => apiClient.post("/api/v1/security/contacts", { ...form, siteId }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["po-contacts-for-site", siteId] }); setShowNew(false); setError("") },
    onError: (e: any) => setError(e.response?.data?.message ?? "Failed to create contact"),
  })

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "flex-end", marginBottom: 12 }}>
        <button onClick={() => { setForm({ name: "", role: "OTHER", phone: "", email: "" }); setShowNew(true); setError("") }}
          style={{ display: "flex", alignItems: "center", gap: 6, padding: "8px 16px", background: "var(--hf-primary)", color: "var(--hf-text-on-solid)", border: "none", borderRadius: 8, fontSize: 13, fontWeight: 600, cursor: "pointer" }}>
          <Plus size={14} /> New Contact
        </button>
      </div>
      {error && <div style={{ padding: "10px 14px", background: "var(--hf-danger-soft)", border: "1px solid var(--hf-danger-border)", borderRadius: 8, color: "var(--hf-danger-text)", fontSize: 13, marginBottom: 14 }}>{error}</div>}
      <p style={{ fontSize: 12, color: "var(--hf-text-faint)", marginBottom: 14 }}>Reusable across every post order at this site — update a contact once, every order referencing it stays current.</p>

      {isLoading ? (
        <p style={{ color: "var(--hf-text-faint)" }}>Loading…</p>
      ) : contacts.length === 0 ? (
        <div style={{ textAlign: "center", padding: "40px 20px", color: "var(--hf-text-faint)", background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 12 }}>
          No contacts yet — e.g. Site Manager, Control Room, Police.
        </div>
      ) : (
        <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
          {contacts.map(c => (
            <div key={c.id} style={{ display: "flex", justifyContent: "space-between", alignItems: "center", padding: "12px 16px", background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 10 }}>
              <div>
                <span style={{ fontWeight: 700, fontSize: 14, color: "var(--hf-text)" }}>{c.name}</span>
                <span style={{ marginLeft: 8, fontSize: 11, fontWeight: 700, padding: "2px 8px", borderRadius: 20, background: "var(--hf-surface-sunken)", color: "var(--hf-text-muted)" }}>{c.role.replace(/_/g, " ")}</span>
                {!c.siteId && <span style={{ marginLeft: 6, fontSize: 10, color: "var(--hf-text-faint)" }}>(tenant-wide)</span>}
              </div>
              <span style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>{c.phone}{c.email ? ` · ${c.email}` : ""}</span>
            </div>
          ))}
        </div>
      )}

      {showNew && (
        <div style={modalOverlay}>
          <div style={{ ...modalBox, width: 400 }}>
            <h3 style={{ margin: "0 0 16px", fontSize: 16, fontWeight: 700, color: "var(--hf-text)" }}>New Contact</h3>
            {error && <p style={{ color: "var(--hf-danger-text)", fontSize: 12, marginBottom: 12 }}>{error}</p>}
            <div style={{ marginBottom: 14 }}>
              <label style={lbl}>Name *</label>
              <input value={form.name} onChange={e => setForm(f => ({ ...f, name: e.target.value }))} style={inp} />
            </div>
            <div style={{ marginBottom: 14 }}>
              <label style={lbl}>Role</label>
              <select value={form.role} onChange={e => setForm(f => ({ ...f, role: e.target.value }))} style={inp}>
                {ROLES.map(r => <option key={r} value={r}>{r.replace(/_/g, " ")}</option>)}
              </select>
            </div>
            <div style={{ display: "flex", gap: 12, marginBottom: 18 }}>
              <div style={{ flex: 1 }}>
                <label style={lbl}>Phone</label>
                <input value={form.phone} onChange={e => setForm(f => ({ ...f, phone: e.target.value }))} style={inp} />
              </div>
              <div style={{ flex: 1 }}>
                <label style={lbl}>Email</label>
                <input value={form.email} onChange={e => setForm(f => ({ ...f, email: e.target.value }))} style={inp} />
              </div>
            </div>
            <div style={{ display: "flex", justifyContent: "flex-end", gap: 10 }}>
              <button onClick={() => setShowNew(false)} style={cancelBtn}>Cancel</button>
              <button onClick={() => createContact.mutate()} disabled={!form.name.trim() || createContact.isPending} style={submitBtn}>
                {createContact.isPending ? "Creating…" : "Create"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

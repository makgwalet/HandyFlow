// src/pages/security/PublicApiTab.tsx
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { Key, Webhook, Plus, Trash2, AlertTriangle, RefreshCw, Copy, Check, Eye, EyeOff, ChevronDown, ChevronUp } from "lucide-react"

interface ApiKey {
  id: string; name: string; keyPrefix: string; readOnly: boolean; active: boolean
  scopePrefixes: string | null; branchId: string | null
  lastUsedAt: string | null; expiresAt: string | null; createdAt: string
  revokedAt: string | null; revocationReason: string | null
}
interface WebhookSub {
  id: string; name: string; endpointUrl: string; eventTypesJson: string
  branchId: string | null; active: boolean; failureCount: number
  suspended: boolean; lastSuccessAt: string | null; createdAt: string
}
interface NewKey { id: string; name: string; rawKey: string; keyPrefix: string; readOnly: boolean; expiresAt: string | null; createdAt: string }

const EVENT_TYPES = [
  "ALARM_EVENT","DISPATCH_CREATED","DISPATCH_RESOLVED",
  "INCIDENT_CREATED","INCIDENT_RESOLVED","SHIFT_MISSED",
  "PATROL_ROUND_MISSED","DURESS_TRIGGERED","GUARD_SCREENING_DUE","PSIRA_EXPIRY_WARNING",
]
const fmtDate = (d: string | null) => d ? new Date(d).toLocaleDateString("en-ZA", {day:"numeric",month:"short",year:"numeric"}) : "—"
const fmtDateTime = (d: string | null) => d ? new Date(d).toLocaleString("en-ZA", {day:"numeric",month:"short",hour:"2-digit",minute:"2-digit"}) : "—"

const inp = { width:"100%", padding:"8px 12px", borderRadius:8, border:"1px solid var(--hf-border)", fontSize:13, outline:"none", boxSizing:"border-box" as const }
const btn = (bg: string, color="var(--hf-text-on-solid)") => ({ padding:"8px 16px", borderRadius:8, border:"none", background:bg, color, fontSize:13, cursor:"pointer", fontWeight:600 as const })
const sbtn = { padding:"8px 16px", borderRadius:8, border:"1px solid var(--hf-border)", background:"var(--hf-surface)", fontSize:13, cursor:"pointer", color:"var(--hf-text-secondary)" as const }

export default function PublicApiTab() {
  const qc = useQueryClient()
  const [section, setSection] = useState<"keys"|"webhooks">("keys")
  const [view, setView] = useState<"list"|"create">("list")
  const [err, setErr] = useState("")
  const [newKey, setNewKey] = useState<NewKey | null>(null)
  const [copied, setCopied] = useState(false)
  const [showKey, setShowKey] = useState(false)
  const [expandedSub, setExpandedSub] = useState<string | null>(null)

  // API key form
  const [keyForm, setKeyForm] = useState({ name:"", readOnly:true, scopePrefixesJson:"", expiresAt:"" })
  // Webhook form
  const [whForm, setWhForm] = useState({ name:"", endpointUrl:"", selectedEvents:[] as string[] })

  // ── Queries ────────────────────────────────────────────────────────────────
  const { data: keys = [] } = useQuery<ApiKey[]>({
    queryKey: ["api-keys"],
    enabled: section==="keys",
    queryFn: async () => { const r = await apiClient.get("/api/v1/security/public-api/keys"); return (r.data?.data ?? r.data) as ApiKey[] },
  })
  const { data: webhooks = [] } = useQuery<WebhookSub[]>({
    queryKey: ["webhooks"],
    enabled: section==="webhooks",
    queryFn: async () => { const r = await apiClient.get("/api/v1/security/public-api/webhooks"); return (r.data?.data ?? r.data) as WebhookSub[] },
  })

  // ── Mutations ──────────────────────────────────────────────────────────────
  const createKeyMut = useMutation({
    mutationFn: (b: object) => apiClient.post("/api/v1/security/public-api/keys", b),
    onSuccess: (r) => {
      qc.invalidateQueries({queryKey:["api-keys"]})
      setNewKey(r.data?.data ?? r.data)
      setView("list"); setErr("")
    },
    onError: (e: any) => setErr(e.response?.data?.message ?? "Failed to create key"),
  })
  const revokeKeyMut = useMutation({
    mutationFn: ({id,reason}: {id:string,reason:string}) => apiClient.delete(`/api/v1/security/public-api/keys/${id}?reason=${encodeURIComponent(reason)}`),
    onSuccess: () => { qc.invalidateQueries({queryKey:["api-keys"]}); setErr("") },
    onError: (e: any) => setErr(e.response?.data?.message ?? "Failed to revoke key"),
  })
  const createWhMut = useMutation({
    mutationFn: (b: object) => apiClient.post("/api/v1/security/public-api/webhooks", b),
    onSuccess: () => { qc.invalidateQueries({queryKey:["webhooks"]}); setView("list"); setErr("") },
    onError: (e: any) => setErr(e.response?.data?.message ?? "Failed to create webhook"),
  })
  const deactivateWhMut = useMutation({
    mutationFn: (id: string) => apiClient.delete(`/api/v1/security/public-api/webhooks/${id}`),
    onSuccess: () => { qc.invalidateQueries({queryKey:["webhooks"]}); setErr("") },
    onError: (e: any) => setErr(e.response?.data?.message ?? "Failed"),
  })
  const reactivateWhMut = useMutation({
    mutationFn: (id: string) => apiClient.post(`/api/v1/security/public-api/webhooks/${id}/reactivate`),
    onSuccess: () => { qc.invalidateQueries({queryKey:["webhooks"]}); setErr("") },
    onError: (e: any) => setErr(e.response?.data?.message ?? "Failed"),
  })

  function copyKey() {
    if (!newKey) return
    navigator.clipboard.writeText(newKey.rawKey)
    setCopied(true); setTimeout(()=>setCopied(false), 2000)
  }

  function toggleEvent(evt: string) {
    setWhForm(p => ({
      ...p,
      selectedEvents: p.selectedEvents.includes(evt)
        ? p.selectedEvents.filter(e=>e!==evt)
        : [...p.selectedEvents, evt],
    }))
  }

  const th = (l: string) => (
    <th key={l} style={{textAlign:"left",padding:"9px 14px",fontSize:11,fontWeight:700,color:"var(--hf-text-faint)",textTransform:"uppercase" as const,letterSpacing:"0.5px"}}>{l}</th>
  )

  return (
    <div style={{fontFamily:"'Inter',system-ui,sans-serif"}}>
      {/* Header */}
      <div style={{display:"flex",justifyContent:"space-between",alignItems:"center",marginBottom:20}}>
        <div style={{display:"flex",alignItems:"center",gap:10}}>
          <div style={{background:"var(--hf-violet)",borderRadius:10,padding:8}}><Key size={18} style={{ color: 'var(--hf-text-on-solid)' }}/></div>
          <div>
            <h2 style={{margin:0,fontSize:18,fontWeight:700,color:"var(--hf-text)"}}>Public API & Webhooks</h2>
            <p style={{margin:0,fontSize:12,color:"var(--hf-text-faint)"}}>Machine-to-machine access keys · Event webhooks for client BI tools</p>
          </div>
        </div>
        <div style={{display:"flex",gap:8}}>
          <button style={{...sbtn,fontWeight:section==="keys"?700:400,background:section==="keys"?"var(--hf-info-soft)":"var(--hf-surface)",color:section==="keys"?"var(--hf-info-text)":"var(--hf-text-secondary)",borderColor:section==="keys"?"var(--hf-info-border)":"var(--hf-border)"}}
            onClick={()=>{setSection("keys");setView("list");setErr("")}}>
            <Key size={12} style={{marginRight:5}}/> API Keys
          </button>
          <button style={{...sbtn,fontWeight:section==="webhooks"?700:400,background:section==="webhooks"?"var(--hf-violet-soft)":"var(--hf-surface)",color:section==="webhooks"?"var(--hf-violet-text)":"var(--hf-text-secondary)",borderColor:section==="webhooks"?"var(--hf-violet-border)":"var(--hf-border)"}}
            onClick={()=>{setSection("webhooks");setView("list");setErr("")}}>
            <Webhook size={12} style={{marginRight:5}}/> Webhooks
          </button>
          <button style={{...btn(section==="keys"?"var(--hf-info)":"var(--hf-violet)"),display:"flex",alignItems:"center",gap:6}}
            onClick={()=>{setView("create");setErr("");setNewKey(null)}}>
            <Plus size={14}/> New {section==="keys"?"Key":"Webhook"}
          </button>
        </div>
      </div>

      {err && (
        <div style={{background:"var(--hf-danger-soft)",border:"1px solid var(--hf-danger-border)",borderRadius:8,padding:"10px 14px",marginBottom:16,fontSize:13,color:"var(--hf-danger-text-strong)",display:"flex",alignItems:"center",gap:8}}>
          <AlertTriangle size={14}/> {err}
        </div>
      )}

      {/* ONE-TIME KEY REVEAL */}
      {newKey && (
        <div style={{background:"var(--hf-success-soft)",border:"2px solid var(--hf-success-border)",borderRadius:12,padding:20,marginBottom:20}}>
          <h4 style={{margin:"0 0 6px",fontSize:15,fontWeight:700,color:"var(--hf-success-text-strong)"}}>✓ API Key Created — copy it now</h4>
          <p style={{margin:"0 0 14px",fontSize:13,color:"var(--hf-success-text-strong)"}}>This key is shown <strong>once only</strong>. It cannot be retrieved again after you close this banner.</p>
          <div style={{background:"var(--hf-surface)",border:"1px solid var(--hf-success-border)",borderRadius:8,padding:"10px 14px",fontFamily:"monospace",fontSize:13,letterSpacing:"0.5px",color:"var(--hf-text)",display:"flex",justifyContent:"space-between",alignItems:"center",gap:10}}>
            <span style={{wordBreak:"break-all" as const}}>{showKey ? newKey.rawKey : "hf_live_" + "•".repeat(32)}</span>
            <div style={{display:"flex",gap:6,flexShrink:0}}>
              <button style={{padding:"5px 10px",borderRadius:7,border:"1px solid var(--hf-success-border)",background:"var(--hf-success-soft-strong)",fontSize:12,cursor:"pointer",color:"var(--hf-success-text-strong)",display:"flex",alignItems:"center",gap:4}}
                onClick={()=>setShowKey(v=>!v)}>
                {showKey ? <EyeOff size={12}/> : <Eye size={12}/>} {showKey?"Hide":"Reveal"}
              </button>
              <button style={{padding:"5px 10px",borderRadius:7,border:"none",background:copied?"var(--hf-success-solid-strong)":"var(--hf-info)",fontSize:12,cursor:"pointer",color:"var(--hf-text-on-solid)",display:"flex",alignItems:"center",gap:4}}
                onClick={copyKey}>
                {copied ? <><Check size={12}/> Copied</> : <><Copy size={12}/> Copy</>}
              </button>
            </div>
          </div>
          <p style={{margin:"10px 0 0",fontSize:12,color:"var(--hf-text-muted)"}}>
            Use as: <code style={{background:"var(--hf-surface-sunken)",padding:"1px 5px",borderRadius:4}}>Authorization: ApiKey {newKey.keyPrefix}...</code>
          </p>
          <button style={{marginTop:10,padding:"5px 12px",borderRadius:7,border:"1px solid var(--hf-success-border)",background:"transparent",fontSize:12,cursor:"pointer",color:"var(--hf-success-text-strong)"}}
            onClick={()=>setNewKey(null)}>Dismiss</button>
        </div>
      )}

      {/* ── API KEY FORM ───────────────────────────────────────────────────────── */}
      {section==="keys" && view==="create" && (
        <div style={{background:"var(--hf-surface)",border:"1px solid var(--hf-border)",borderRadius:12,padding:24,marginBottom:20}}>
          <h3 style={{margin:"0 0 20px",fontSize:16,fontWeight:700,color:"var(--hf-text)"}}>New API Key</h3>
          <div style={{display:"grid",gridTemplateColumns:"1fr 1fr",gap:16,marginBottom:16}}>
            <div style={{gridColumn:"1/-1"}}>
              <label style={{display:"block",fontSize:12,fontWeight:600,color:"var(--hf-text-secondary)",marginBottom:5}}>Key Name *</label>
              <input placeholder="e.g. Acme BI Dashboard, SAPS Reporting Feed" value={keyForm.name} onChange={e=>setKeyForm(p=>({...p,name:e.target.value}))} style={inp}/>
            </div>
            <div>
              <label style={{display:"block",fontSize:12,fontWeight:600,color:"var(--hf-text-secondary)",marginBottom:5}}>Scope (path prefixes, JSON array)</label>
              <input placeholder='["/api/v1/security/reports"] or leave empty for read-all' value={keyForm.scopePrefixesJson} onChange={e=>setKeyForm(p=>({...p,scopePrefixesJson:e.target.value}))} style={inp}/>
            </div>
            <div>
              <label style={{display:"block",fontSize:12,fontWeight:600,color:"var(--hf-text-secondary)",marginBottom:5}}>Expires (optional)</label>
              <input type="date" value={keyForm.expiresAt} onChange={e=>setKeyForm(p=>({...p,expiresAt:e.target.value}))} style={inp}/>
            </div>
            <div style={{gridColumn:"1/-1",display:"flex",alignItems:"center",gap:10}}>
              <input type="checkbox" id="readonly" checked={keyForm.readOnly} onChange={e=>setKeyForm(p=>({...p,readOnly:e.target.checked}))} style={{width:16,height:16}}/>
              <label htmlFor="readonly" style={{fontSize:13,color:"var(--hf-text-secondary)"}}>
                <strong>Read-only</strong> — prevents write operations (recommended for BI integrations)
              </label>
            </div>
          </div>
          <div style={{display:"flex",gap:8}}>
            <button style={sbtn} onClick={()=>setView("list")}>Cancel</button>
            <button style={{...btn("var(--hf-info)"),opacity:!keyForm.name?0.5:1}} disabled={!keyForm.name}
              onClick={()=>createKeyMut.mutate({...keyForm,scopePrefixesJson:keyForm.scopePrefixesJson||null,expiresAt:keyForm.expiresAt?new Date(keyForm.expiresAt).toISOString():null})}>
              Create Key
            </button>
          </div>
          <p style={{margin:"12px 0 0",fontSize:12,color:"var(--hf-text-faint)"}}>
            The raw key is shown once after creation. After that only the prefix is visible. Store it in a secrets manager immediately.
          </p>
        </div>
      )}

      {/* ── WEBHOOK FORM ──────────────────────────────────────────────────────── */}
      {section==="webhooks" && view==="create" && (
        <div style={{background:"var(--hf-surface)",border:"1px solid var(--hf-border)",borderRadius:12,padding:24,marginBottom:20}}>
          <h3 style={{margin:"0 0 20px",fontSize:16,fontWeight:700,color:"var(--hf-text)"}}>New Webhook Subscription</h3>
          <div style={{display:"grid",gridTemplateColumns:"1fr 1fr",gap:16,marginBottom:16}}>
            <div>
              <label style={{display:"block",fontSize:12,fontWeight:600,color:"var(--hf-text-secondary)",marginBottom:5}}>Name *</label>
              <input placeholder="e.g. Client Dashboard Events" value={whForm.name} onChange={e=>setWhForm(p=>({...p,name:e.target.value}))} style={inp}/>
            </div>
            <div>
              <label style={{display:"block",fontSize:12,fontWeight:600,color:"var(--hf-text-secondary)",marginBottom:5}}>Endpoint URL *</label>
              <input type="url" placeholder="https://client.example.com/handyflow-webhook" value={whForm.endpointUrl} onChange={e=>setWhForm(p=>({...p,endpointUrl:e.target.value}))} style={inp}/>
            </div>
            <div style={{gridColumn:"1/-1"}}>
              <label style={{display:"block",fontSize:12,fontWeight:600,color:"var(--hf-text-secondary)",marginBottom:8}}>Event Types * (select all that apply)</label>
              <div style={{display:"flex",flexWrap:"wrap" as const,gap:8}}>
                {EVENT_TYPES.map(evt => {
                  const sel = whForm.selectedEvents.includes(evt)
                  return (
                    <button key={evt}
                      style={{padding:"5px 12px",borderRadius:20,border:`1px solid ${sel?"var(--hf-violet)":"var(--hf-border)"}`,background:sel?"var(--hf-violet-soft)":"var(--hf-surface-muted)",fontSize:12,cursor:"pointer",color:sel?"var(--hf-violet-text)":"var(--hf-text-muted)",fontWeight:sel?700:400,transition:"all 0.1s"}}
                      onClick={()=>toggleEvent(evt)}>
                      {evt.replace(/_/g," ")}
                    </button>
                  )
                })}
              </div>
            </div>
          </div>
          <div style={{display:"flex",gap:8}}>
            <button style={sbtn} onClick={()=>setView("list")}>Cancel</button>
            <button style={{...btn("var(--hf-violet)"),opacity:(!whForm.name||!whForm.endpointUrl||!whForm.selectedEvents.length)?0.5:1}}
              disabled={!whForm.name||!whForm.endpointUrl||!whForm.selectedEvents.length}
              onClick={()=>createWhMut.mutate({name:whForm.name,endpointUrl:whForm.endpointUrl,eventTypesJson:JSON.stringify(whForm.selectedEvents)})}>
              Create Webhook
            </button>
          </div>
          <p style={{margin:"12px 0 0",fontSize:12,color:"var(--hf-text-faint)"}}>
            HandyFlow will POST signed payloads to your endpoint with header <code style={{background:"var(--hf-surface-sunken)",padding:"1px 5px",borderRadius:4}}>X-HandyFlow-Signature: sha256=&lt;hmac&gt;</code>.
            The signing secret is generated automatically and shown once. After 10 consecutive delivery failures the subscription is auto-suspended.
          </p>
        </div>
      )}

      {/* ── API KEY LIST ───────────────────────────────────────────────────────── */}
      {section==="keys" && view==="list" && (
        keys.length===0
          ? <div style={{textAlign:"center",padding:"60px 0",color:"var(--hf-text-faint)"}}>
              <Key size={32} strokeWidth={1.5} style={{margin:"0 auto 12px",display:"block"}}/>
              <p style={{margin:"0 0 4px",fontWeight:600,color:"var(--hf-text-secondary)"}}>No API keys yet</p>
              <p style={{margin:0,fontSize:13}}>Create a key to give BI tools or third-party systems read access to security data.</p>
            </div>
          : <div style={{background:"var(--hf-surface)",border:"1px solid var(--hf-border)",borderRadius:12,overflow:"hidden"}}>
              <table style={{width:"100%",borderCollapse:"collapse",fontSize:13}}>
                <thead><tr style={{borderBottom:"2px solid var(--hf-border)",background:"var(--hf-surface-muted)"}}>
                  {["Name","Prefix","Access","Status","Last Used","Expires","Actions"].map(th)}
                </tr></thead>
                <tbody>{keys.map(k=>(
                  <tr key={k.id} style={{borderBottom:"1px solid var(--hf-border-subtle)",opacity:k.active?1:0.5}}>
                    <td style={{padding:"11px 14px",fontWeight:600,color:"var(--hf-text)"}}>{k.name}</td>
                    <td style={{padding:"11px 14px"}}>
                      <code style={{background:"var(--hf-surface-sunken)",padding:"2px 6px",borderRadius:5,fontSize:12,color:"var(--hf-text-secondary)"}}>{k.keyPrefix}…</code>
                    </td>
                    <td style={{padding:"11px 14px"}}>
                      <span style={{background:k.readOnly?"var(--hf-info-soft)":"var(--hf-warning-soft-strong)",color:k.readOnly?"var(--hf-info-text)":"var(--hf-warning-text-deep)",borderRadius:6,padding:"2px 8px",fontSize:11,fontWeight:700}}>
                        {k.readOnly?"Read-only":"Read+Write"}
                      </span>
                    </td>
                    <td style={{padding:"11px 14px"}}>
                      <span style={{background:k.active?"var(--hf-success-soft-strong)":"var(--hf-danger-soft)",color:k.active?"var(--hf-success-text-strong)":"var(--hf-danger-text-strong)",borderRadius:6,padding:"2px 8px",fontSize:11,fontWeight:700}}>
                        {k.active?"Active":"Revoked"}
                      </span>
                    </td>
                    <td style={{padding:"11px 14px",color:"var(--hf-text-muted)",fontSize:12}}>{fmtDateTime(k.lastUsedAt)}</td>
                    <td style={{padding:"11px 14px",color:"var(--hf-text-muted)",fontSize:12}}>{fmtDate(k.expiresAt)}</td>
                    <td style={{padding:"11px 14px"}}>
                      {k.active && (
                        <button style={{display:"flex",alignItems:"center",gap:4,padding:"5px 10px",borderRadius:7,border:"1px solid var(--hf-danger-border)",background:"var(--hf-danger-soft)",fontSize:12,cursor:"pointer",color:"var(--hf-danger-text-strong)"}}
                          onClick={()=>{ if(confirm(`Revoke key "${k.name}"? This cannot be undone.`)) revokeKeyMut.mutate({id:k.id,reason:"Revoked by administrator"}) }}>
                          <Trash2 size={11}/> Revoke
                        </button>
                      )}
                    </td>
                  </tr>
                ))}</tbody>
              </table>
            </div>
      )}

      {/* ── WEBHOOK LIST ──────────────────────────────────────────────────────── */}
      {section==="webhooks" && view==="list" && (
        webhooks.length===0
          ? <div style={{textAlign:"center",padding:"60px 0",color:"var(--hf-text-faint)"}}>
              <Webhook size={32} strokeWidth={1.5} style={{margin:"0 auto 12px",display:"block"}}/>
              <p style={{margin:"0 0 4px",fontWeight:600,color:"var(--hf-text-secondary)"}}>No webhook subscriptions yet</p>
              <p style={{margin:0,fontSize:13}}>Create a webhook to push real-time security events to a client dashboard or SIEM.</p>
            </div>
          : <div style={{display:"flex",flexDirection:"column" as const,gap:10}}>
              {webhooks.map(s => {
                const events: string[] = (() => { try { return JSON.parse(s.eventTypesJson) } catch { return [] } })()
                const expanded = expandedSub === s.id
                return (
                  <div key={s.id} style={{background:"var(--hf-surface)",border:"1px solid var(--hf-border)",borderRadius:12,overflow:"hidden"}}>
                    <div style={{padding:"14px 18px",display:"flex",alignItems:"center",gap:12,cursor:"pointer"}} onClick={()=>setExpandedSub(expanded?null:s.id)}>
                      <div style={{width:36,height:36,borderRadius:8,background:s.suspended?"var(--hf-danger-soft)":s.active?"var(--hf-violet-soft)":"var(--hf-surface-sunken)",display:"flex",alignItems:"center",justifyContent:"center",flexShrink:0}}>
                        <Webhook size={16} style={{ color: s.suspended?"var(--hf-danger-text-strong)":s.active?"var(--hf-violet-text)":"var(--hf-text-muted)" }}/>
                      </div>
                      <div style={{flex:1}}>
                        <div style={{display:"flex",alignItems:"center",gap:8}}>
                          <span style={{fontWeight:700,fontSize:14,color:"var(--hf-text)"}}>{s.name}</span>
                          {s.suspended && <span style={{background:"var(--hf-danger-soft)",color:"var(--hf-danger-text-strong)",borderRadius:6,padding:"1px 7px",fontSize:11,fontWeight:700}}>SUSPENDED</span>}
                          {!s.suspended && !s.active && <span style={{background:"var(--hf-surface-sunken)",color:"var(--hf-text-muted)",borderRadius:6,padding:"1px 7px",fontSize:11,fontWeight:700}}>INACTIVE</span>}
                          {s.active && !s.suspended && <span style={{background:"var(--hf-violet-soft)",color:"var(--hf-violet-text)",borderRadius:6,padding:"1px 7px",fontSize:11,fontWeight:700}}>ACTIVE</span>}
                        </div>
                        <p style={{margin:0,fontSize:12,color:"var(--hf-text-muted)",marginTop:1}}>{s.endpointUrl}</p>
                      </div>
                      <div style={{fontSize:12,color:"var(--hf-text-faint)",textAlign:"right" as const,flexShrink:0}}>
                        <div>{s.failureCount} failures</div>
                        <div>Last success: {fmtDateTime(s.lastSuccessAt)}</div>
                      </div>
                      {expanded ? <ChevronUp size={16} style={{ color: 'var(--hf-text-faint)' }}/> : <ChevronDown size={16} style={{ color: 'var(--hf-text-faint)' }}/>}
                    </div>
                    {expanded && (
                      <div style={{borderTop:"1px solid var(--hf-border-subtle)",padding:"14px 18px",background:"var(--hf-surface-muted)"}}>
                        <div style={{marginBottom:12}}>
                          <p style={{margin:"0 0 6px",fontSize:12,fontWeight:600,color:"var(--hf-text-secondary)"}}>Subscribed Events</p>
                          <div style={{display:"flex",flexWrap:"wrap" as const,gap:6}}>
                            {events.map(e=>(
                              <span key={e} style={{background:"var(--hf-violet-soft)",color:"var(--hf-violet-text)",borderRadius:20,padding:"3px 10px",fontSize:11,fontWeight:600}}>
                                {e.replace(/_/g," ")}
                              </span>
                            ))}
                          </div>
                        </div>
                        <div style={{display:"flex",gap:8}}>
                          {s.active && !s.suspended && (
                            <button style={{display:"flex",alignItems:"center",gap:4,padding:"6px 12px",borderRadius:7,border:"1px solid var(--hf-danger-border)",background:"var(--hf-danger-soft)",fontSize:12,cursor:"pointer",color:"var(--hf-danger-text-strong)"}}
                              onClick={()=>{ if(confirm(`Deactivate webhook "${s.name}"?`)) deactivateWhMut.mutate(s.id) }}>
                              <Trash2 size={11}/> Deactivate
                            </button>
                          )}
                          {(s.suspended || !s.active) && (
                            <button style={{display:"flex",alignItems:"center",gap:4,padding:"6px 12px",borderRadius:7,border:"none",background:"var(--hf-violet)",fontSize:12,cursor:"pointer",color:"var(--hf-text-on-solid)",fontWeight:600}}
                              onClick={()=>reactivateWhMut.mutate(s.id)}>
                              <RefreshCw size={11}/> Reactivate
                            </button>
                          )}
                        </div>
                        {s.suspended && (
                          <p style={{margin:"10px 0 0",fontSize:12,color:"var(--hf-danger-text-strong)"}}>
                            ⚠ Suspended after {s.failureCount} consecutive delivery failures. Fix your endpoint then reactivate.
                          </p>
                        )}
                      </div>
                    )}
                  </div>
                )
              })}
            </div>
      )}

      {/* Deployment note */}
      <div style={{marginTop:20,padding:"14px 16px",background:"var(--hf-surface-muted)",border:"1px solid var(--hf-border)",borderRadius:10,fontSize:12,color:"var(--hf-text-muted)"}}>
        <strong style={{color:"var(--hf-text-secondary)"}}>Deployment note:</strong> API key authentication (<code style={{background:"var(--hf-surface-sunken)",padding:"1px 5px",borderRadius:4}}>Authorization: ApiKey hf_live_...</code>) requires
        <strong> ApiKeyAuthFilter</strong> to be wired into SecurityConfig before JwtAuthFilter. Until then, keys can be created and revoked here but won't authenticate HTTP requests.
        Webhook delivery requires <strong>@EnableAsync</strong> on HandyFlowApplication and <strong>WebhookRetryScheduler</strong> deployed.
      </div>
    </div>
  )
}

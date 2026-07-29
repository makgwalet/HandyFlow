// src/pages/clinic/ConsentTab.tsx
// FIX: "no POPIA consent tracking" gap — a system handling health records
// (POPIA's "special personal information" category) had no consent-capture
// mechanism on the patient record at all.
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { useAuthStore } from "../../store/auth.store"
import { ShieldCheck, ShieldX, ShieldQuestion, Plus, X, Clock, Info } from "lucide-react"

interface Patient { id: string; fullName: string }
interface ConsentStatus {
  consentType: string; status: string; lastActionAt?: string; method?: string; notes?: string
}
interface ConsentEvent {
  id: string; consentType: string; action: string; method?: string
  capturedByName?: string; notes?: string; createdAt: string
}

const NAVY="#1B3A6B"; const TEAL="#0D9488"; const RED="#DC2626"
const GREEN="#166534"; const GRAY="#64748B"; const BORDER="#E2E8F0"; const LIGHT="#F8FAFC"

const TYPE_LABELS: Record<string,string> = {
  TREATMENT: "Treatment / health information processing",
  MEDICAL_AID_SHARING: "Sharing with medical aid scheme",
  THIRD_PARTY_REFERRAL: "Sharing with other healthcare providers (referrals)",
  MARKETING: "Marketing communications",
  RESEARCH: "Anonymised data use in research",
}
const METHOD_LABELS: Record<string,string> = { VERBAL: "Verbal", WRITTEN: "Written", ELECTRONIC: "Electronic" }

const fmtDT = (iso?:string) => iso ? new Date(iso).toLocaleDateString("en-ZA",{day:"numeric",month:"short",year:"numeric",hour:"2-digit",minute:"2-digit"}) : "—"
const unwrap = (r:any) => { const p=r.data?.data??r.data; return Array.isArray(p)?p:[] }

export default function ConsentTab({ patient }: { patient: Patient }) {
  const qc = useQueryClient()
  const [showRecord, setShowRecord] = useState(false)
  const [showHistory, setShowHistory] = useState(false)

  const user = useAuthStore.getState().user as any
  const defaultCapturedBy = [user?.firstName, user?.lastName].filter(Boolean).join(" ")

  const [form, setForm] = useState({
    consentType: "TREATMENT", action: "GRANTED", method: "VERBAL",
    capturedByName: defaultCapturedBy, notes: "",
  })
  const [apiError, setApiError] = useState("")

  const { data: status=[], isLoading } = useQuery<ConsentStatus[]>({
    queryKey: ["clinic-consent-status", patient.id],
    queryFn: async () => unwrap(await apiClient.get(`/api/v1/clinic/patients/${patient.id}/consent`)),
  })

  const { data: history=[] } = useQuery<ConsentEvent[]>({
    queryKey: ["clinic-consent-history", patient.id],
    queryFn: async () => unwrap(await apiClient.get(`/api/v1/clinic/patients/${patient.id}/consent/history`)),
    enabled: showHistory,
  })

  const recordConsent = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/clinic/patients/${patient.id}/consent`, {
      consentType: form.consentType, action: form.action,
      method: form.method || undefined,
      capturedByName: form.capturedByName || undefined,
      notes: form.notes || undefined,
    }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["clinic-consent-status", patient.id] })
      qc.invalidateQueries({ queryKey: ["clinic-consent-history", patient.id] })
      setShowRecord(false)
      setForm(f => ({ ...f, notes: "" }))
      setApiError("")
    },
    onError: (e:any) => setApiError(e.response?.data?.message ?? "Failed to record consent"),
  })

  const cfgFor = (s: string) =>
    s === "GRANTED" ? { color: GREEN, bg: "#F0FDF4", border: "#86EFAC", icon: ShieldCheck, label: "Granted" }
    : s === "REVOKED" ? { color: RED, bg: "#FEF2F2", border: "#FECACA", icon: ShieldX, label: "Revoked" }
    : { color: GRAY, bg: LIGHT, border: BORDER, icon: ShieldQuestion, label: "Not recorded" }

  return (
    <div>
      <div style={{display:"flex",alignItems:"flex-start",gap:10,padding:"12px 16px",background:"#EFF6FF",border:"1px solid #BFDBFE",borderRadius:10,marginBottom:20,fontSize:12,color:"#1E40AF"}}>
        <Info size={15} style={{flexShrink:0,marginTop:1}}/>
        <div>
          Tracks consent for processing this patient's health information under POPIA
          (health data is "special personal information," requiring explicit consent).
          This is a record-keeping tool, not legal advice — it documents what was
          captured, it doesn't itself guarantee your practice's process is compliant.
        </div>
      </div>

      <div style={{display:"flex",justifyContent:"space-between",alignItems:"center",marginBottom:16}}>
        <div style={{fontSize:14,fontWeight:700,color:"#0F172A"}}>Consent status</div>
        <div style={{display:"flex",gap:8}}>
          <button onClick={()=>setShowHistory(true)}
            style={{display:"flex",alignItems:"center",gap:6,padding:"8px 14px",border:`1px solid ${BORDER}`,borderRadius:8,fontSize:13,fontWeight:600,cursor:"pointer",background:"#fff",color:NAVY}}>
            <Clock size={14}/> History
          </button>
          <button onClick={()=>{setShowRecord(true);setApiError("")}}
            style={{display:"flex",alignItems:"center",gap:6,padding:"8px 14px",border:"none",borderRadius:8,fontSize:13,fontWeight:600,cursor:"pointer",background:NAVY,color:"#fff"}}>
            <Plus size={14}/> Record consent
          </button>
        </div>
      </div>

      {isLoading ? (
        <div style={{textAlign:"center",padding:30,color:GRAY}}>Loading...</div>
      ) : (
        <div style={{display:"flex",flexDirection:"column",gap:8}}>
          {status.map(s => {
            const c = cfgFor(s.status)
            const Icon = c.icon
            return (
              <div key={s.consentType} style={{display:"flex",justifyContent:"space-between",alignItems:"center",padding:"12px 16px",background:c.bg,border:`1px solid ${c.border}`,borderRadius:10}}>
                <div style={{display:"flex",alignItems:"center",gap:10}}>
                  <Icon size={18} color={c.color}/>
                  <div>
                    <div style={{fontSize:13,fontWeight:600,color:"#0F172A"}}>{TYPE_LABELS[s.consentType] ?? s.consentType}</div>
                    {s.lastActionAt && (
                      <div style={{fontSize:11,color:GRAY,marginTop:2}}>
                        {fmtDT(s.lastActionAt)}{s.method ? ` · ${METHOD_LABELS[s.method] ?? s.method}` : ""}
                      </div>
                    )}
                  </div>
                </div>
                <span style={{background:c.color,color:"#fff",padding:"3px 10px",borderRadius:20,fontSize:11,fontWeight:700}}>{c.label}</span>
              </div>
            )
          })}
        </div>
      )}

      {showRecord && (
        <div style={{position:"fixed",inset:0,background:"rgba(15,23,42,0.5)",display:"flex",alignItems:"center",justifyContent:"center",zIndex:1000}}>
          <div style={{background:"#fff",borderRadius:16,padding:28,width:460,boxShadow:"0 20px 60px rgba(0,0,0,0.2)"}}>
            <div style={{display:"flex",justifyContent:"space-between",alignItems:"center",marginBottom:20}}>
              <h3 style={{margin:0,fontSize:17,fontWeight:700,color:"#0F172A"}}>Record Consent</h3>
              <button onClick={()=>setShowRecord(false)} style={{background:"none",border:"none",cursor:"pointer",color:"#94A3B8"}}><X size={20}/></button>
            </div>

            <label style={lbl}>Consent type *</label>
            <select value={form.consentType} onChange={e=>setForm(f=>({...f,consentType:e.target.value}))} style={inp}>
              {Object.entries(TYPE_LABELS).map(([k,v])=><option key={k} value={k}>{v}</option>)}
            </select>

            <label style={lbl}>Action *</label>
            <div style={{display:"flex",gap:8,marginBottom:14}}>
              {(["GRANTED","REVOKED"] as const).map(a => (
                <button key={a} onClick={()=>setForm(f=>({...f,action:a}))}
                  style={{flex:1,padding:"9px",borderRadius:8,fontSize:13,fontWeight:600,cursor:"pointer",
                    border: form.action===a ? `2px solid ${a==="GRANTED"?GREEN:RED}` : `1.5px solid ${BORDER}`,
                    background: form.action===a ? (a==="GRANTED"?"#F0FDF4":"#FEF2F2") : "#fff",
                    color: form.action===a ? (a==="GRANTED"?GREEN:RED) : GRAY}}>
                  {a==="GRANTED"?"Grant":"Revoke"}
                </button>
              ))}
            </div>

            <label style={lbl}>Method</label>
            <select value={form.method} onChange={e=>setForm(f=>({...f,method:e.target.value}))} style={inp}>
              {Object.entries(METHOD_LABELS).map(([k,v])=><option key={k} value={k}>{v}</option>)}
            </select>

            <label style={lbl}>Captured by</label>
            <input value={form.capturedByName} onChange={e=>setForm(f=>({...f,capturedByName:e.target.value}))}
              placeholder="Staff member's name" style={inp}/>

            <label style={lbl}>Notes (optional)</label>
            <input value={form.notes} onChange={e=>setForm(f=>({...f,notes:e.target.value}))}
              placeholder="e.g. Signed consent form on file" style={inp}/>

            {apiError && (
              <div style={{marginTop:4,marginBottom:14,padding:"10px 12px",background:"#FEF2F2",border:"1px solid #FECACA",borderRadius:8,fontSize:13,color:RED}}>{apiError}</div>
            )}

            <div style={{display:"flex",gap:10,justifyContent:"flex-end",marginTop:6}}>
              <button onClick={()=>setShowRecord(false)} style={{padding:"9px 18px",border:"1px solid #E2E8F0",borderRadius:9,background:"#fff",fontSize:14,cursor:"pointer"}}>Cancel</button>
              <button onClick={()=>recordConsent.mutate()} disabled={recordConsent.isPending}
                style={{padding:"9px 20px",border:"none",borderRadius:9,background:NAVY,color:"#fff",fontSize:14,fontWeight:600,cursor:"pointer"}}>
                {recordConsent.isPending ? "Saving..." : "Record"}
              </button>
            </div>
          </div>
        </div>
      )}

      {showHistory && (
        <div style={{position:"fixed",inset:0,background:"rgba(15,23,42,0.5)",display:"flex",alignItems:"center",justifyContent:"center",zIndex:1000}}>
          <div style={{background:"#fff",borderRadius:16,padding:28,width:560,maxHeight:"80vh",overflowY:"auto",boxShadow:"0 20px 60px rgba(0,0,0,0.2)"}}>
            <div style={{display:"flex",justifyContent:"space-between",alignItems:"center",marginBottom:20}}>
              <h3 style={{margin:0,fontSize:17,fontWeight:700,color:"#0F172A"}}>Consent History</h3>
              <button onClick={()=>setShowHistory(false)} style={{background:"none",border:"none",cursor:"pointer",color:"#94A3B8"}}><X size={20}/></button>
            </div>
            {history.length === 0 ? (
              <div style={{textAlign:"center",padding:30,color:GRAY,fontSize:13}}>No consent events recorded yet.</div>
            ) : (
              <div style={{display:"flex",flexDirection:"column",gap:8}}>
                {history.map(h => {
                  const c = cfgFor(h.action)
                  const Icon = c.icon
                  return (
                    <div key={h.id} style={{display:"flex",gap:10,padding:"10px 14px",background:LIGHT,borderRadius:8}}>
                      <Icon size={16} color={c.color} style={{flexShrink:0,marginTop:2}}/>
                      <div style={{flex:1}}>
                        <div style={{fontSize:13,fontWeight:600,color:"#0F172A"}}>
                          {TYPE_LABELS[h.consentType] ?? h.consentType} — <span style={{color:c.color}}>{c.label}</span>
                        </div>
                        <div style={{fontSize:11,color:GRAY,marginTop:2}}>
                          {fmtDT(h.createdAt)}
                          {h.method ? ` · ${METHOD_LABELS[h.method] ?? h.method}` : ""}
                          {h.capturedByName ? ` · by ${h.capturedByName}` : ""}
                        </div>
                        {h.notes && <div style={{fontSize:12,color:"#374151",marginTop:4}}>{h.notes}</div>}
                      </div>
                    </div>
                  )
                })}
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  )
}

const lbl: React.CSSProperties = { display:"block", fontSize:12, fontWeight:600, color:"#374151", marginBottom:5, marginTop:12 }
const inp: React.CSSProperties = { width:"100%", padding:"9px 12px", boxSizing:"border-box", border:"1.5px solid #E2E8F0", borderRadius:8, fontSize:14, background:"#fff" }

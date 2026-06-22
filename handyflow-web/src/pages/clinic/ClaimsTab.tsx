// src/pages/clinic/ClaimsTab.tsx
// Medical aid claims — per-consultation builder, full lifecycle management
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import {
  CreditCard, Plus, X, ChevronDown, ChevronUp, AlertCircle,
  CheckCircle, Clock, XCircle, RefreshCw, Send, FileText, Filter,
} from "lucide-react"

// ── Types ─────────────────────────────────────────────────────────────────────

interface Claim {
  id: string; consultationId: string; patientId: string; patientName?: string
  practitionerId: string; practitionerName?: string
  status: string; schemeName: string; memberNumber: string; dependentCode: string
  grossAmount: number; schemePortion: number; patientPortion: number
  submittedAt?: string; referenceNumber?: string; rejectionReason?: string
  createdAt: string; lines?: ClaimLine[]
}
interface ClaimLine {
  id: string; lineType: string; tariffCode?: string; nappiCode?: string
  icd10Code?: string; description: string
  quantity: number; unitPrice: number; grossAmount: number
  schemePortion: number; patientPortion: number
}
interface Consultation { id: string; patientId: string; chiefComplaint: string; consultedAt: string; patientName?: string; practitionerName?: string; diagnosis?: string; icd10Codes?: string[]; billed?: boolean }

// ── Tokens ────────────────────────────────────────────────────────────────────

const NAVY="#1B3A6B"; const TEAL="#0D9488"; const RED="#DC2626"
const GREEN="#166534"; const AMBER="#D97706"; const PURPLE="#7C3AED"
const GRAY="#64748B"; const BORDER="#E2E8F0"; const LIGHT="#F8FAFC"

const STATUS_CFG: Record<string,{color:string;bg:string;border:string;label:string;icon:any}> = {
  DRAFT:     {color:GRAY,  bg:LIGHT,    border:BORDER,    label:"Draft",    icon:FileText},
  SUBMITTED: {color:AMBER, bg:"#FFFBEB",border:"#FDE68A", label:"Submitted",icon:Send},
  ACCEPTED:  {color:TEAL,  bg:"#F0FDF4",border:"#86EFAC", label:"Accepted", icon:CheckCircle},
  REJECTED:  {color:RED,   bg:"#FEF2F2",border:"#FECACA", label:"Rejected", icon:XCircle},
  PAID:      {color:GREEN, bg:"#DCFCE7",border:"#86EFAC", label:"Paid",     icon:CheckCircle},
  PARTIAL:   {color:PURPLE,bg:"#F5F3FF",border:"#DDD6FE", label:"Partial",  icon:Clock},
}

const fmtDT  = (iso?:string) => iso ? new Date(iso).toLocaleDateString("en-ZA",{day:"numeric",month:"short",year:"numeric"}) : "—"
const fmtR   = (v?:number)   => `R ${((v??0)).toLocaleString("en-ZA",{minimumFractionDigits:2})}`
const unwrap = (r:any)       => { const p=r.data?.data??r.data; return Array.isArray(p)?p:(p?.content??[]) }

// ── Component ─────────────────────────────────────────────────────────────────

// Procedure catalogue hook — replaces hardcoded tariff list
function useProcedures(search: string) {
  const [results, setResults] = React.useState<any[]>([])
  React.useEffect(() => {
    const url = search.length > 1
      ? `/api/v1/clinic/procedures?search=${encodeURIComponent(search)}`
      : `/api/v1/clinic/procedures`
    apiClient.get(url).then(r => {
      const d = r.data?.data ?? r.data
      setResults(Array.isArray(d) ? d : [])
    }).catch(() => setResults([]))
  }, [search])
  return results
}

export default function ClaimsTab() {
  const qc = useQueryClient()
  const [statusFilter, setStatusFilter] = useState("all")
  const [expanded, setExpanded]         = useState<string|null>(null)
  const [showCreate, setShowCreate]     = useState(false)
  const [showReject, setShowReject]     = useState<string|null>(null)
  const [rejectReason, setRejectReason] = useState("")
  const [apiError, setApiError]         = useState("")

  const { data: claims=[], isLoading } = useQuery<Claim[]>({
    queryKey: ["clinic-claims", statusFilter],
    queryFn: async () => {
      const p = new URLSearchParams({ size:"100" })
      if (statusFilter!=="all") p.set("status", statusFilter)
      return unwrap(await apiClient.get(`/api/v1/clinic/billing/claims?${p}`))
    },
  })

  const { data: consultations=[] } = useQuery<Consultation[]>({
    queryKey: ["consultations-for-claims"],
    queryFn: async () => unwrap(await apiClient.get("/api/v1/clinic/billing/consultations?size=100&unbilled=true")),
    enabled: showCreate,
  })

  const doAction = useMutation({
    mutationFn: ({id,action,reason}:{id:string;action:string;reason?:string}) => {
      if (action==="submit") return apiClient.post(`/api/v1/clinic/billing/claims/${id}/submit`)
      if (action==="reject") return apiClient.post(`/api/v1/clinic/billing/claims/${id}/reject`,null,{params:{reason}})
      return apiClient.post(`/api/v1/clinic/billing/claims/${id}/${action}`)
    },
    onSuccess: () => { qc.invalidateQueries({queryKey:["clinic-claims"]}); setShowReject(null); setRejectReason("") },
    onError: (e:any) => setApiError(e.response?.data?.message ?? "Action failed"),
  })

  const displayedClaims = claims as Claim[]

  // Summary stats
  const total      = displayedClaims.length
  const outstanding = displayedClaims.filter(c=>["DRAFT","SUBMITTED"].includes(c.status)).reduce((s,c)=>s+(c.schemePortion??0),0)
  const paid       = displayedClaims.filter(c=>c.status==="PAID").reduce((s,c)=>s+(c.grossAmount??0),0)
  const rejected   = displayedClaims.filter(c=>c.status==="REJECTED").length

  return (
    <div>
      {/* ── KPI strip ───────────────────────────────────────────────────── */}
      <div style={{display:"grid",gridTemplateColumns:"repeat(4,1fr)",gap:12,marginBottom:24}}>
        {[
          {label:"Total claims",      value:total,          color:NAVY,  fmt:"num"},
          {label:"Outstanding",       value:outstanding,    color:AMBER, fmt:"rand"},
          {label:"Paid out",          value:paid,           color:GREEN, fmt:"rand"},
          {label:"Rejected",          value:rejected,       color:RED,   fmt:"num"},
        ].map(k=>(
          <div key={k.label} style={{background:LIGHT,border:`1px solid ${BORDER}`,borderRadius:12,padding:"14px 18px"}}>
            <div style={{fontSize:11,fontWeight:700,color:k.color,textTransform:"uppercase",letterSpacing:"0.06em",marginBottom:6}}>{k.label}</div>
            <div style={{fontSize:22,fontWeight:800,color:k.color}}>
              {k.fmt==="rand" ? fmtR(k.value as number) : k.value}
            </div>
          </div>
        ))}
      </div>

      {/* ── Toolbar ─────────────────────────────────────────────────────── */}
      <div style={{display:"flex",justifyContent:"space-between",alignItems:"center",marginBottom:18,flexWrap:"wrap",gap:10}}>
        <div style={{display:"flex",gap:6,flexWrap:"wrap"}}>
          {["all",...Object.keys(STATUS_CFG)].map(s=>{
            const cfg = s==="all" ? null : STATUS_CFG[s]
            return (
              <button key={s} onClick={()=>setStatusFilter(s)}
                style={{padding:"5px 12px",borderRadius:20,border:"none",fontSize:12,cursor:"pointer",
                  fontWeight:statusFilter===s?600:400,
                  background:statusFilter===s?(cfg?.color??NAVY):"#F1F5F9",
                  color:statusFilter===s?"#fff":GRAY}}>
                {s==="all"?"All":cfg?.label}
              </button>
            )
          })}
        </div>
        <button onClick={()=>{setShowCreate(true);setApiError("")}}
          style={{display:"flex",alignItems:"center",gap:6,background:NAVY,color:"#fff",border:"none",borderRadius:9,padding:"9px 16px",fontSize:13,fontWeight:600,cursor:"pointer"}}>
          <Plus size={14}/> New claim
        </button>
      </div>

      {/* ── Claims list ─────────────────────────────────────────────────── */}
      {isLoading ? <div style={{textAlign:"center",padding:40,color:GRAY}}>Loading claims...</div>
      : displayedClaims.length===0 ? (
        <div style={{textAlign:"center",padding:"60px 20px",color:GRAY,border:`1px dashed ${BORDER}`,borderRadius:12}}>
          <CreditCard size={36} style={{marginBottom:12,opacity:0.4}}/>
          <div style={{fontWeight:600,color:"#475569",fontSize:15}}>
            {statusFilter==="all"?"No claims yet":"No "+STATUS_CFG[statusFilter]?.label+" claims"}
          </div>
          <div style={{fontSize:13,marginTop:4}}>Create a claim from a completed consultation.</div>
        </div>
      ) : (
        <div style={{display:"flex",flexDirection:"column",gap:10}}>
          {displayedClaims.map(claim=>{
            const s   = STATUS_CFG[claim.status] ?? STATUS_CFG.DRAFT
            const Icon = s.icon
            const isOpen = expanded===claim.id
            const actions = getClaimActions(claim.status)
            return (
              <div key={claim.id} style={{border:`1px solid ${s.border}`,borderLeft:`4px solid ${s.color}`,borderRadius:10,overflow:"hidden",background:"#fff"}}>
                {/* Header row */}
                <div onClick={()=>setExpanded(isOpen?null:claim.id)}
                  style={{display:"flex",justifyContent:"space-between",alignItems:"center",padding:"14px 18px",cursor:"pointer",background:isOpen?LIGHT:"#fff"}}>
                  <div style={{display:"flex",alignItems:"center",gap:12}}>
                    <div style={{width:36,height:36,borderRadius:8,background:s.bg,border:`1px solid ${s.border}`,display:"flex",alignItems:"center",justifyContent:"center",flexShrink:0}}>
                      <Icon size={16} color={s.color}/>
                    </div>
                    <div>
                      <div style={{display:"flex",alignItems:"center",gap:8,marginBottom:3}}>
                        <span style={{fontWeight:700,fontSize:14,color:"#0F172A"}}>{claim.patientName||"Patient"}</span>
                        <span style={{background:s.bg,color:s.color,padding:"1px 8px",borderRadius:20,fontSize:11,fontWeight:700,border:`1px solid ${s.border}`}}>{s.label}</span>
                        {claim.referenceNumber && <span style={{fontSize:11,color:GRAY}}>Ref: {claim.referenceNumber}</span>}
                      </div>
                      <div style={{fontSize:12,color:GRAY}}>
                        {claim.schemeName||"No scheme"} · {fmtDT(claim.createdAt)}
                        {claim.practitionerName && ` · Dr. ${claim.practitionerName}`}
                      </div>
                    </div>
                  </div>
                  <div style={{display:"flex",alignItems:"center",gap:16}}>
                    <div style={{textAlign:"right"}}>
                      <div style={{fontSize:15,fontWeight:800,color:"#0F172A"}}>{fmtR(claim.grossAmount)}</div>
                      <div style={{fontSize:11,color:GRAY}}>Scheme: {fmtR(claim.schemePortion)} · Patient: {fmtR(claim.patientPortion)}</div>
                    </div>
                    {isOpen ? <ChevronUp size={16} color={GRAY}/> : <ChevronDown size={16} color={GRAY}/>}
                  </div>
                </div>

                {/* Expanded detail */}
                {isOpen && (
                  <div style={{borderTop:`1px solid ${BORDER}`,padding:"16px 18px",background:"#FAFAFA"}}>
                    {/* Rejection reason */}
                    {claim.rejectionReason && (
                      <div style={{marginBottom:14,padding:"10px 14px",background:"#FEF2F2",border:"1px solid #FECACA",borderRadius:8,fontSize:13,color:RED}}>
                        <span style={{fontWeight:700}}>Rejection reason: </span>{claim.rejectionReason}
                      </div>
                    )}

                    {/* Claim lines */}
                    {claim.lines && claim.lines.length > 0 && (
                      <div style={{marginBottom:16}}>
                        <div style={{fontSize:11,fontWeight:700,color:GRAY,textTransform:"uppercase",letterSpacing:"0.06em",marginBottom:8}}>Claim lines</div>
                        <div style={{border:`1px solid ${BORDER}`,borderRadius:8,overflow:"hidden"}}>
                          <table style={{width:"100%",borderCollapse:"collapse"}}>
                            <thead>
                              <tr style={{background:LIGHT}}>
                                {["Type","Description","Code","Qty","Unit","Gross","Scheme","Patient"].map(h=>(
                                  <th key={h} style={{padding:"7px 12px",textAlign:"left",fontSize:11,fontWeight:700,color:GRAY}}>{h}</th>
                                ))}
                              </tr>
                            </thead>
                            <tbody>
                              {claim.lines.map((line,i)=>(
                                <tr key={line.id} style={{borderTop:i>0?`1px solid #F1F5F9`:"none"}}>
                                  <td style={{padding:"8px 12px"}}><span style={{fontSize:11,fontWeight:600,color:NAVY}}>{line.lineType}</span></td>
                                  <td style={{padding:"8px 12px",fontSize:13,color:"#0F172A"}}>{line.description}</td>
                                  <td style={{padding:"8px 12px",fontSize:12,color:GRAY}}>{line.tariffCode||line.nappiCode||"—"}</td>
                                  <td style={{padding:"8px 12px",fontSize:13}}>{line.quantity}</td>
                                  <td style={{padding:"8px 12px",fontSize:13}}>{fmtR(line.unitPrice)}</td>
                                  <td style={{padding:"8px 12px",fontSize:13,fontWeight:600}}>{fmtR(line.grossAmount)}</td>
                                  <td style={{padding:"8px 12px",fontSize:13,color:GREEN}}>{fmtR(line.schemePortion)}</td>
                                  <td style={{padding:"8px 12px",fontSize:13,color:AMBER}}>{fmtR(line.patientPortion)}</td>
                                </tr>
                              ))}
                            </tbody>
                          </table>
                        </div>
                      </div>
                    )}

                    {/* Action buttons */}
                    {apiError && <div style={{marginBottom:10,padding:"8px 12px",background:"#FEF2F2",border:"1px solid #FECACA",borderRadius:8,fontSize:13,color:RED}}>{apiError}</div>}
                    {actions.length > 0 && (
                      <div style={{display:"flex",gap:8,justifyContent:"flex-end"}}>
                        {actions.map(btn=>(
                          <button key={btn.action}
                            onClick={()=>{
                              setApiError("")
                              if (btn.action==="reject") { setShowReject(claim.id); return }
                              doAction.mutate({id:claim.id, action:btn.action})
                            }}
                            disabled={doAction.isPending}
                            style={{padding:"7px 16px",border:"none",borderRadius:8,fontSize:13,fontWeight:600,cursor:"pointer",background:`${btn.color}18`,color:btn.color,display:"flex",alignItems:"center",gap:6}}>
                            {btn.icon && <btn.icon size={13}/>} {btn.label}
                          </button>
                        ))}
                      </div>
                    )}
                  </div>
                )}
              </div>
            )
          })}
        </div>
      )}

      {/* ── Reject reason modal ──────────────────────────────────────────── */}
      {showReject && (
        <div style={{position:"fixed",inset:0,background:"rgba(15,23,42,0.5)",display:"flex",alignItems:"center",justifyContent:"center",zIndex:1000,backdropFilter:"blur(3px)"}}>
          <div style={{background:"#fff",borderRadius:16,padding:28,width:440,boxShadow:"0 20px 60px rgba(0,0,0,0.2)"}}>
            <div style={{display:"flex",justifyContent:"space-between",alignItems:"center",marginBottom:20}}>
              <h3 style={{margin:0,fontSize:16,fontWeight:700,color:"#0F172A"}}>Reject claim</h3>
              <button onClick={()=>setShowReject(null)} style={{background:"none",border:"none",cursor:"pointer",color:GRAY,display:"flex"}}><X size={18}/></button>
            </div>
            <label style={lbl}>Rejection reason *</label>
            <textarea value={rejectReason} onChange={e=>setRejectReason(e.target.value)}
              rows={3} placeholder="State reason for rejection (required for resubmission)..."
              style={{...sinp,resize:"vertical" as const}}/>
            <div style={{display:"flex",gap:10,justifyContent:"flex-end",marginTop:16}}>
              <button onClick={()=>setShowReject(null)} style={btnCancel}>Cancel</button>
              <button onClick={()=>{ if(!rejectReason.trim()){return} doAction.mutate({id:showReject,action:"reject",reason:rejectReason}) }}
                disabled={doAction.isPending||!rejectReason.trim()}
                style={{...btnPrimary,background:RED}}>
                {doAction.isPending?"Rejecting...":"Confirm rejection"}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* ── New claim modal ──────────────────────────────────────────────── */}
      {showCreate && (
        <CreateClaimModal consultations={consultations as Consultation[]} onClose={()=>setShowCreate(false)} onCreated={()=>{ qc.invalidateQueries({queryKey:["clinic-claims"]}); setShowCreate(false) }}/>
      )}
    </div>
  )
}

// ── Create Claim modal ────────────────────────────────────────────────────────

function CreateClaimModal({ consultations, onClose, onCreated }:
  {consultations:Consultation[]; onClose:()=>void; onCreated:()=>void}) {
  const [form, setForm] = useState({
    consultationId:"", schemeName:"", memberNumber:"", dependentCode:"",
    consultationTariffCode:"0191", consultationIcd10Code:"", consultationDescription:"Consultation", consultationRate:"520",
  })
  const [lines, setLines] = useState<{type:string;tariffCode:string;nappiCode:string;icd10Code:string;description:string;quantity:string;unitPrice:string}[]>([])
  const [apiError, setApiError] = useState("")
  const SCHEMES = ["Discovery Health","Momentum Health","Bonitas","Medihelp","Fedhealth","GEMS","Polmed","SAMWUMED"]

  const createClaim = useMutation({
    mutationFn: (body:any) => apiClient.post(`/api/v1/clinic/billing/consultations/${body.consultationId}/claim`, body),
    onSuccess: onCreated,
    onError: (e:any) => setApiError(e.response?.data?.message ?? "Failed to create claim"),
  })

  const selectedConsult = consultations.find(c=>c.id===form.consultationId)

  return (
    <div style={{position:"fixed",inset:0,background:"rgba(15,23,42,0.55)",display:"flex",alignItems:"center",justifyContent:"center",zIndex:1000,backdropFilter:"blur(3px)"}}>
      <div style={{background:"#fff",borderRadius:16,padding:28,width:680,maxHeight:"92vh",overflowY:"auto",boxShadow:"0 24px 64px rgba(0,0,0,0.22)"}}>
        <div style={{display:"flex",justifyContent:"space-between",alignItems:"center",marginBottom:22}}>
          <h3 style={{margin:0,fontSize:18,fontWeight:700,color:"#0F172A"}}>New medical aid claim</h3>
          <button onClick={onClose} style={{background:"none",border:"none",cursor:"pointer",color:GRAY,display:"flex"}}><X size={20}/></button>
        </div>

        <Sect title="Select consultation">
          <select value={form.consultationId} onChange={e=>{ const c=consultations.find(x=>x.id===e.target.value); setForm(f=>({...f,consultationId:e.target.value,consultationIcd10Code:c?.icd10Codes?.[0]||""})) }} style={sinp}>
            <option value="">Select unbilled consultation...</option>
            {consultations.map(c=>(
              <option key={c.id} value={c.id}>{fmtDT(c.consultedAt)} · {c.patientName||"Patient"} · {c.chiefComplaint}</option>
            ))}
          </select>
          {selectedConsult && (
            <div style={{marginTop:8,padding:"8px 12px",background:"#F0FDF4",border:"1px solid #86EFAC",borderRadius:8,fontSize:12,color:GREEN}}>
              ✓ {selectedConsult.chiefComplaint}{selectedConsult.diagnosis?` · Dx: ${selectedConsult.diagnosis}`:""}{selectedConsult.icd10Codes?.length?` · ICD-10: ${selectedConsult.icd10Codes.join(", ")}`:""}
            </div>
          )}
        </Sect>

        <Sect title="Medical aid details">
          <div style={{display:"grid",gridTemplateColumns:"1fr 1fr 1fr",gap:12}}>
            <div style={{gridColumn:"1/-1"}}>
              <label style={lbl}>Scheme name</label>
              <select value={form.schemeName} onChange={e=>setForm(f=>({...f,schemeName:e.target.value}))} style={sinp}>
                <option value="">Select scheme or type...</option>
                {SCHEMES.map(s=><option key={s} value={s}>{s}</option>)}
              </select>
            </div>
            <div>
              <label style={lbl}>Member number</label>
              <input value={form.memberNumber} onChange={e=>setForm(f=>({...f,memberNumber:e.target.value}))} placeholder="e.g. 12345678" style={sinp}/>
            </div>
            <div>
              <label style={lbl}>Dependent code</label>
              <input value={form.dependentCode} onChange={e=>setForm(f=>({...f,dependentCode:e.target.value}))} placeholder="00 = main member" style={sinp}/>
            </div>
          </div>
        </Sect>

        <Sect title="Consultation tariff line">
          <div style={{display:"grid",gridTemplateColumns:"1fr 1fr 1fr 1fr",gap:12}}>
            <div>
              <label style={lbl}>Tariff code</label>
              <select value={form.consultationTariffCode} onChange={e=>setForm(f=>({...f,consultationTariffCode:e.target.value}))} style={sinp}>
                <option value="0190">0190 — Brief (R380)</option>
                <option value="0191">0191 — Intermediate (R520)</option>
                <option value="0192">0192 — Comprehensive (R750)</option>
                <option value="0193">0193 — New patient (R850)</option>
                <option value="0104">0104 — Emergency (R950)</option>
              </select>
            </div>
            <div>
              <label style={lbl}>ICD-10 code</label>
              <input value={form.consultationIcd10Code} onChange={e=>setForm(f=>({...f,consultationIcd10Code:e.target.value}))} placeholder="J06.9" style={sinp}/>
            </div>
            <div>
              <label style={lbl}>Rate (R)</label>
              <input type="number" step="0.01" value={form.consultationRate} onChange={e=>setForm(f=>({...f,consultationRate:e.target.value}))} style={sinp}/>
            </div>
            <div>
              <label style={lbl}>Description</label>
              <input value={form.consultationDescription} onChange={e=>setForm(f=>({...f,consultationDescription:e.target.value}))} style={sinp}/>
            </div>
          </div>
        </Sect>

        {/* Additional procedure lines */}
        <Sect title="Additional procedure / medicine lines">
          {lines.map((line,idx)=>(
            <div key={idx} style={{marginBottom:10,padding:"12px 14px",background:LIGHT,border:`1px solid ${BORDER}`,borderRadius:8,position:"relative"}}>
              <button onClick={()=>setLines(l=>l.filter((_,i)=>i!==idx))}
                style={{position:"absolute",top:8,right:8,background:"none",border:"none",cursor:"pointer",color:GRAY,display:"flex"}}><X size={13}/></button>
              <div style={{display:"grid",gridTemplateColumns:"repeat(6,1fr)",gap:10}}>
                <div>
                  <label style={lbl}>Type</label>
                  <select value={line.type} onChange={e=>setLines(l=>l.map((x,i)=>i===idx?{...x,type:e.target.value}:x))} style={{...sinp,padding:"6px 8px",fontSize:12}}>
                    <option value="PROCEDURE">Procedure</option><option value="MEDICINE">Medicine</option><option value="CONSUMABLE">Consumable</option>
                  </select>
                </div>
                <div style={{gridColumn:"span 2"}}>
                  <label style={lbl}>Description</label>
                  <input value={line.description} onChange={e=>setLines(l=>l.map((x,i)=>i===idx?{...x,description:e.target.value}:x))} placeholder="Description" style={{...sinp,padding:"6px 8px",fontSize:12}}/>
                </div>
                <div>
                  <label style={lbl}>{line.type==="MEDICINE"?"NAPPI":"Tariff"} code</label>
                  <input value={line.type==="MEDICINE"?line.nappiCode:line.tariffCode} onChange={e=>setLines(l=>l.map((x,i)=>i===idx?line.type==="MEDICINE"?{...x,nappiCode:e.target.value}:{...x,tariffCode:e.target.value}:x))} placeholder={line.type==="MEDICINE"?"NAPPI":"0007"} style={{...sinp,padding:"6px 8px",fontSize:12}}/>
                </div>
                <div>
                  <label style={lbl}>Qty</label>
                  <input type="number" step="0.5" value={line.quantity} onChange={e=>setLines(l=>l.map((x,i)=>i===idx?{...x,quantity:e.target.value}:x))} style={{...sinp,padding:"6px 8px",fontSize:12}}/>
                </div>
                <div>
                  <label style={lbl}>Rate (R)</label>
                  <input type="number" step="0.01" value={line.unitPrice} onChange={e=>setLines(l=>l.map((x,i)=>i===idx?{...x,unitPrice:e.target.value}:x))} style={{...sinp,padding:"6px 8px",fontSize:12}}/>
                </div>
              </div>
            </div>
          ))}
          <button onClick={()=>setLines(l=>[...l,{type:"PROCEDURE",tariffCode:"",nappiCode:"",icd10Code:"",description:"",quantity:"1",unitPrice:""}])}
            style={{display:"flex",alignItems:"center",gap:6,padding:"7px 14px",border:`1px dashed ${BORDER}`,borderRadius:8,background:LIGHT,color:GRAY,fontSize:13,cursor:"pointer"}}>
            <Plus size={13}/> Add procedure / medicine line
          </button>
        </Sect>

        {/* Total preview */}
        {form.consultationRate && (
          <div style={{marginBottom:16,padding:"12px 16px",background:NAVY,borderRadius:10,color:"#fff",display:"flex",justifyContent:"space-between",alignItems:"center"}}>
            <span style={{fontSize:13,color:"rgba(255,255,255,0.7)"}}>Estimated claim total</span>
            <span style={{fontSize:20,fontWeight:800}}>
              {fmtR((parseFloat(form.consultationRate)||0) + lines.reduce((s,l)=>(parseFloat(l.unitPrice)||0)*(parseFloat(l.quantity)||1)+s,0))}
            </span>
          </div>
        )}

        {apiError && <div style={{marginBottom:14,padding:"10px 12px",background:"#FEF2F2",border:"1px solid #FECACA",borderRadius:8,fontSize:13,color:RED,display:"flex",alignItems:"center",gap:8}}><AlertCircle size={14}/>{apiError}</div>}

        <div style={{display:"flex",gap:10,justifyContent:"flex-end"}}>
          <button onClick={onClose} style={btnCancel}>Cancel</button>
          <button onClick={()=>{
            if (!form.consultationId) { setApiError("Please select a consultation"); return }
            createClaim.mutate({
              consultationId: form.consultationId,
              schemeName: form.schemeName||null,
              memberNumber: form.memberNumber||null,
              dependentCode: form.dependentCode||null,
              consultationTariffCode: form.consultationTariffCode,
              consultationIcd10Code: form.consultationIcd10Code||null,
              consultationDescription: form.consultationDescription,
              consultationRate: parseFloat(form.consultationRate)||520,
              procedures: lines.filter(l=>l.description).map(l=>({
                tariffCode:l.type!=="MEDICINE"?l.tariffCode||null:null,
                nappiCode:l.type==="MEDICINE"?l.nappiCode||null:null,
                icd10Code:l.icd10Code||null,
                description:l.description,
                quantity:parseFloat(l.quantity)||1,
                unitPrice:parseFloat(l.unitPrice)||0,
              }))
            })
          }} disabled={createClaim.isPending} style={btnPrimary}>
            {createClaim.isPending ? "Creating claim..." : "Create claim"}
          </button>
        </div>
      </div>
    </div>
  )
}

// ── Claim action config ────────────────────────────────────────────────────────

function getClaimActions(status: string) {
  switch(status) {
    case "DRAFT":     return [{action:"submit",  label:"Submit to scheme", color:AMBER,  icon:Send}]
    case "SUBMITTED": return [{action:"accept",  label:"Mark accepted",   color:TEAL,   icon:CheckCircle},{action:"reject",label:"Reject",color:RED,icon:XCircle}]
    case "ACCEPTED":  return [{action:"paid",    label:"Mark paid",       color:GREEN,  icon:CheckCircle},{action:"partial",label:"Partial payment",color:PURPLE,icon:Clock}]
    case "REJECTED":  return [{action:"submit",  label:"Resubmit",        color:AMBER,  icon:RefreshCw}]
    default: return []
  }
}

// ── Shared ────────────────────────────────────────────────────────────────────

function Sect({title,children}:{title:string;children:React.ReactNode}) {
  return <div style={{marginBottom:20}}><div style={{fontSize:10,fontWeight:700,color:GRAY,letterSpacing:"0.07em",textTransform:"uppercase" as const,marginBottom:12,paddingBottom:8,borderBottom:`1px solid ${BORDER}`}}>{title}</div>{children}</div>
}
const lbl:React.CSSProperties      = {display:"block",fontSize:13,fontWeight:600,color:"#374151",marginBottom:5}
const sinp:React.CSSProperties     = {width:"100%",padding:"9px 12px",boxSizing:"border-box" as const,border:`1.5px solid ${BORDER}`,borderRadius:8,fontSize:14,outline:"none",background:"#fff"}
const btnPrimary:React.CSSProperties = {background:NAVY,color:"#fff",border:"none",borderRadius:9,padding:"9px 20px",fontSize:13,fontWeight:600,cursor:"pointer"}
const btnCancel:React.CSSProperties  = {padding:"9px 18px",border:`1px solid ${BORDER}`,borderRadius:9,background:"#fff",fontSize:13,cursor:"pointer",color:"#374151"}

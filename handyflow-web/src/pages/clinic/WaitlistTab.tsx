// src/pages/clinic/WaitlistTab.tsx
// FIX: "no waitlist" gap — cancellations/no-shows had no mechanism to
// backfill from a waiting list.
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { ListPlus, X, Phone, CheckCircle, Calendar, Trash2, Users } from "lucide-react"

interface WaitlistEntry {
  id: string; patientId: string; patientName: string
  practitionerId?: string; practitionerName?: string
  appointmentType?: string; notes?: string; status: string; createdAt: string
}
interface PatientOption { id: string; fullName: string; phone?: string }
interface PractitionerOption { id: string; fullName: string }

const NAVY="#1B3A6B"; const TEAL="#0D9488"; const GRAY="#64748B"
const BORDER="#E2E8F0"; const LIGHT="#F8FAFC"

const fmtDT = (iso?:string) => iso ? new Date(iso).toLocaleDateString("en-ZA",{day:"numeric",month:"short"}) : "—"
const unwrap = (r:any) => { const p=r.data?.data??r.data; return Array.isArray(p)?p:(p?.content??[]) }

export default function WaitlistTab() {
  const qc = useQueryClient()
  const [showAdd, setShowAdd] = useState(false)
  const [patientSearch, setPatientSearch] = useState("")
  const [form, setForm] = useState({ patientId:"", practitionerId:"", appointmentType:"", notes:"" })
  const [apiError, setApiError] = useState("")

  const { data: entries=[], isLoading } = useQuery<WaitlistEntry[]>({
    queryKey: ["clinic-waitlist"],
    queryFn: async () => unwrap(await apiClient.get("/api/v1/clinic/waitlist")),
  })

  const { data: patientOptions=[] } = useQuery<PatientOption[]>({
    queryKey: ["waitlist-patient-search", patientSearch],
    queryFn: async () => unwrap(await apiClient.get(`/api/v1/clinic/patients?search=${encodeURIComponent(patientSearch)}&size=10`)),
    enabled: showAdd && patientSearch.length > 1,
  })

  const { data: practitioners=[] } = useQuery<PractitionerOption[]>({
    queryKey: ["clinic-practitioners-list"],
    queryFn: async () => { const r = await apiClient.get("/api/v1/clinic/practitioners/list"); return (r.data?.data ?? r.data) as PractitionerOption[] },
    enabled: showAdd,
  })

  const addEntry = useMutation({
    mutationFn: () => apiClient.post("/api/v1/clinic/waitlist", {
      patientId: form.patientId,
      practitionerId: form.practitionerId || undefined,
      appointmentType: form.appointmentType || undefined,
      notes: form.notes || undefined,
    }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["clinic-waitlist"] })
      setShowAdd(false); setForm({ patientId:"", practitionerId:"", appointmentType:"", notes:"" }); setPatientSearch(""); setApiError("")
    },
    onError: (e:any) => setApiError(e.response?.data?.message ?? "Failed to add to waitlist"),
  })

  const contactAction = useMutation({
    mutationFn: (id:string) => apiClient.post(`/api/v1/clinic/waitlist/${id}/contacted`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["clinic-waitlist"] }),
  })
  const scheduledAction = useMutation({
    mutationFn: (id:string) => apiClient.post(`/api/v1/clinic/waitlist/${id}/scheduled`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["clinic-waitlist"] }),
  })
  const removeAction = useMutation({
    mutationFn: (id:string) => apiClient.delete(`/api/v1/clinic/waitlist/${id}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["clinic-waitlist"] }),
  })

  const selectedPatient = patientOptions.find(p => p.id === form.patientId)

  return (
    <div>
      <div style={{display:"flex",justifyContent:"space-between",alignItems:"center",marginBottom:20}}>
        <div style={{fontSize:14,color:GRAY}}>{entries.length} patient{entries.length!==1?"s":""} waiting</div>
        <button onClick={()=>{setShowAdd(true);setApiError("")}}
          style={{display:"flex",alignItems:"center",gap:6,background:NAVY,color:"#fff",border:"none",borderRadius:9,padding:"9px 16px",fontSize:13,fontWeight:600,cursor:"pointer"}}>
          <ListPlus size={15}/> Add to waitlist
        </button>
      </div>

      {isLoading ? (
        <div style={{textAlign:"center",padding:40,color:GRAY}}>Loading waitlist...</div>
      ) : entries.length === 0 ? (
        <div style={{textAlign:"center",padding:"60px 20px",color:GRAY,border:`1px dashed ${BORDER}`,borderRadius:12}}>
          <Users size={36} style={{marginBottom:12,opacity:0.4}}/>
          <div style={{fontWeight:600,color:"#475569",fontSize:15}}>No one on the waitlist</div>
          <div style={{fontSize:13,marginTop:4}}>Add a patient here so you can backfill a cancellation or no-show.</div>
        </div>
      ) : (
        <div style={{display:"flex",flexDirection:"column",gap:10}}>
          {entries.map(e => (
            <div key={e.id} style={{border:`1px solid ${BORDER}`,borderRadius:10,padding:"14px 18px",background:"#fff",display:"flex",justifyContent:"space-between",alignItems:"center",gap:12,flexWrap:"wrap"}}>
              <div>
                <div style={{display:"flex",alignItems:"center",gap:8,marginBottom:3}}>
                  <span style={{fontWeight:700,fontSize:14,color:"#0F172A"}}>{e.patientName}</span>
                  {e.status === "CONTACTED" && (
                    <span style={{background:"#FFFBEB",color:"#D97706",padding:"1px 8px",borderRadius:20,fontSize:11,fontWeight:700}}>Contacted</span>
                  )}
                </div>
                <div style={{fontSize:12,color:GRAY}}>
                  {e.appointmentType || "Any appointment type"}
                  {e.practitionerName ? ` · Prefers Dr. ${e.practitionerName}` : " · Any practitioner"}
                  {" · Added " + fmtDT(e.createdAt)}
                  {e.notes && ` · ${e.notes}`}
                </div>
              </div>
              <div style={{display:"flex",gap:8}}>
                {e.status !== "CONTACTED" && (
                  <button onClick={()=>contactAction.mutate(e.id)}
                    style={{display:"flex",alignItems:"center",gap:5,padding:"6px 12px",background:LIGHT,color:NAVY,border:`1px solid ${BORDER}`,borderRadius:7,fontSize:12,fontWeight:600,cursor:"pointer"}}>
                    <Phone size={12}/> Mark contacted
                  </button>
                )}
                <button onClick={()=>scheduledAction.mutate(e.id)}
                  style={{display:"flex",alignItems:"center",gap:5,padding:"6px 12px",background:"#F0FDF4",color:"#166534",border:"1px solid #86EFAC",borderRadius:7,fontSize:12,fontWeight:600,cursor:"pointer"}}>
                  <Calendar size={12}/> Scheduled
                </button>
                <button onClick={()=>removeAction.mutate(e.id)}
                  style={{display:"flex",alignItems:"center",gap:5,padding:"6px 10px",background:"#FEF2F2",color:"#DC2626",border:"1px solid #FECACA",borderRadius:7,fontSize:12,cursor:"pointer"}}>
                  <Trash2 size={12}/>
                </button>
              </div>
            </div>
          ))}
        </div>
      )}

      {showAdd && (
        <div style={{position:"fixed",inset:0,background:"rgba(15,23,42,0.5)",display:"flex",alignItems:"center",justifyContent:"center",zIndex:1000}}>
          <div style={{background:"#fff",borderRadius:16,padding:28,width:480,boxShadow:"0 20px 60px rgba(0,0,0,0.2)"}}>
            <div style={{display:"flex",justifyContent:"space-between",alignItems:"center",marginBottom:20}}>
              <h3 style={{margin:0,fontSize:17,fontWeight:700,color:"#0F172A"}}>Add to Waitlist</h3>
              <button onClick={()=>setShowAdd(false)} style={{background:"none",border:"none",cursor:"pointer",color:"#94A3B8"}}><X size={20}/></button>
            </div>

            <label style={{display:"block",fontSize:13,fontWeight:600,color:"#374151",marginBottom:5}}>Patient *</label>
            {selectedPatient ? (
              <div style={{display:"flex",justifyContent:"space-between",alignItems:"center",padding:"9px 12px",border:"1.5px solid #E2E8F0",borderRadius:8,marginBottom:14}}>
                <span style={{fontSize:14}}>{selectedPatient.fullName}</span>
                <button onClick={()=>{setForm(f=>({...f,patientId:""}));setPatientSearch("")}} style={{background:"none",border:"none",cursor:"pointer",color:GRAY}}><X size={14}/></button>
              </div>
            ) : (
              <div style={{marginBottom:14}}>
                <input value={patientSearch} onChange={e=>setPatientSearch(e.target.value)} placeholder="Search patient by name..."
                  style={{width:"100%",padding:"9px 12px",boxSizing:"border-box",border:"1.5px solid #E2E8F0",borderRadius:8,fontSize:14}} autoFocus/>
                {patientOptions.length > 0 && (
                  <div style={{border:"1px solid #E2E8F0",borderRadius:8,marginTop:4,maxHeight:160,overflowY:"auto"}}>
                    {patientOptions.map(p => (
                      <div key={p.id} onClick={()=>{setForm(f=>({...f,patientId:p.id}))}}
                        style={{padding:"8px 12px",fontSize:13,cursor:"pointer",borderBottom:"1px solid #F1F5F9"}}
                        onMouseEnter={e=>(e.currentTarget.style.background=LIGHT)} onMouseLeave={e=>(e.currentTarget.style.background="#fff")}>
                        {p.fullName}
                      </div>
                    ))}
                  </div>
                )}
              </div>
            )}

            <label style={{display:"block",fontSize:13,fontWeight:600,color:"#374151",marginBottom:5}}>Preferred practitioner (optional)</label>
            <select value={form.practitionerId} onChange={e=>setForm(f=>({...f,practitionerId:e.target.value}))}
              style={{width:"100%",padding:"9px 12px",boxSizing:"border-box",border:"1.5px solid #E2E8F0",borderRadius:8,fontSize:14,background:"#fff",marginBottom:14}}>
              <option value="">Any practitioner</option>
              {practitioners.map(p => <option key={p.id} value={p.id}>Dr. {p.fullName}</option>)}
            </select>

            <label style={{display:"block",fontSize:13,fontWeight:600,color:"#374151",marginBottom:5}}>Appointment type (optional)</label>
            <input value={form.appointmentType} onChange={e=>setForm(f=>({...f,appointmentType:e.target.value}))} placeholder="e.g. Follow-up consultation"
              style={{width:"100%",padding:"9px 12px",boxSizing:"border-box",border:"1.5px solid #E2E8F0",borderRadius:8,fontSize:14,marginBottom:14}}/>

            <label style={{display:"block",fontSize:13,fontWeight:600,color:"#374151",marginBottom:5}}>Notes (optional)</label>
            <input value={form.notes} onChange={e=>setForm(f=>({...f,notes:e.target.value}))} placeholder="e.g. Available weekday mornings"
              style={{width:"100%",padding:"9px 12px",boxSizing:"border-box",border:"1.5px solid #E2E8F0",borderRadius:8,fontSize:14,marginBottom:14}}/>

            {apiError && (
              <div style={{marginBottom:14,padding:"10px 12px",background:"#FEF2F2",border:"1px solid #FECACA",borderRadius:8,fontSize:13,color:"#DC2626"}}>{apiError}</div>
            )}

            <div style={{display:"flex",gap:10,justifyContent:"flex-end"}}>
              <button onClick={()=>setShowAdd(false)} style={{padding:"9px 18px",border:"1px solid #E2E8F0",borderRadius:9,background:"#fff",fontSize:14,cursor:"pointer"}}>Cancel</button>
              <button onClick={()=>addEntry.mutate()} disabled={!form.patientId || addEntry.isPending}
                style={{display:"flex",alignItems:"center",gap:7,background:NAVY,color:"#fff",border:"none",borderRadius:9,padding:"9px 20px",fontSize:14,fontWeight:600,cursor:!form.patientId?"not-allowed":"pointer",opacity:!form.patientId?0.6:1}}>
                {addEntry.isPending ? "Adding..." : <><CheckCircle size={15}/> Add to waitlist</>}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

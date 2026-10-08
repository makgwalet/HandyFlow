// src/pages/clinic/WaitlistTab.tsx
// FIX: "no waitlist" gap — cancellations/no-shows had no mechanism to
// backfill from a waiting list.
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { ListPlus, Phone, CheckCircle, Calendar, Trash2, Users } from "lucide-react"
import ModalShell from "./ModalShell"
import PatientPicker, { type PickerPatient } from "./PatientPicker"
import { useDialogs } from "./dialogs"

interface WaitlistEntry {
  id: string; patientId: string; patientName: string
  practitionerId?: string; practitionerName?: string
  appointmentType?: string; notes?: string; status: string; createdAt: string
}
interface PractitionerOption { id: string; fullName: string }

const NAVY="var(--hf-primary)";
const NAVY_TEXT = "var(--hf-primary-text)"; const GRAY="var(--hf-text-muted)"
const BORDER="var(--hf-border)"; const LIGHT="var(--hf-surface-muted)"

const fmtDT = (iso?:string) => iso ? new Date(iso).toLocaleDateString("en-ZA",{day:"numeric",month:"short"}) : "—"
const unwrap = (r:any) => { const p=r.data?.data??r.data; return Array.isArray(p)?p:(p?.content??[]) }

export default function WaitlistTab() {
  const qc = useQueryClient()
  const [showAdd, setShowAdd] = useState(false)
  const [patient, setPatient] = useState<PickerPatient | null>(null)
  const { confirm, dialogs } = useDialogs()
  const [form, setForm] = useState({ patientId:"", practitionerId:"", appointmentType:"", notes:"" })
  const [apiError, setApiError] = useState("")

  const { data: entries=[], isLoading } = useQuery<WaitlistEntry[]>({
    queryKey: ["clinic-waitlist"],
    queryFn: async () => unwrap(await apiClient.get("/api/v1/clinic/waitlist")),
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
      setShowAdd(false); setForm({ patientId:"", practitionerId:"", appointmentType:"", notes:"" }); setPatient(null); setApiError("")
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

  const remove = async (e: WaitlistEntry) => {
    if (await confirm({ title: `Remove ${e.patientName} from the cancellation list?`, body: "Use Booked instead if they have been given a slot.", confirmLabel: "Remove", danger: true }))
      removeAction.mutate(e.id)
  }

  return (
    <div>
      <div style={{display:"flex",justifyContent:"space-between",alignItems:"center",marginBottom:20}}>
        <div style={{fontSize:14,color:GRAY}}>{entries.length} patient{entries.length!==1?"s":""} waiting for a cancelled slot</div>
        <button onClick={()=>{setShowAdd(true);setApiError("")}}
          style={{display:"flex",alignItems:"center",gap:6,background:NAVY,color:"var(--hf-text-on-solid)",border:"none",borderRadius:9,padding:"9px 16px",fontSize:13,fontWeight:600,cursor:"pointer"}}>
          <ListPlus size={15}/> Add to cancellation list
        </button>
      </div>

      {isLoading ? (
        <div style={{textAlign:"center",padding:40,color:GRAY}}>Loading…</div>
      ) : entries.length === 0 ? (
        <div style={{textAlign:"center",padding:"60px 20px",color:GRAY,border:`1px dashed ${BORDER}`,borderRadius:12}}>
          <Users size={36} style={{marginBottom:12,opacity:0.4}}/>
          <div style={{fontWeight:600,color:"var(--hf-text-tertiary)",fontSize:15}}>No one on the cancellation list</div>
          <div style={{fontSize:13,marginTop:4}}>When a slot opens up (cancellation or no-show), call these patients first. Patients who just need a follow-up are on the Recalls screen.</div>
        </div>
      ) : (
        <div style={{display:"flex",flexDirection:"column",gap:10}}>
          {entries.map(e => (
            <div key={e.id} style={{border:`1px solid ${BORDER}`,borderRadius:10,padding:"14px 18px",background:"var(--hf-surface)",display:"flex",justifyContent:"space-between",alignItems:"center",gap:12,flexWrap:"wrap"}}>
              <div>
                <div style={{display:"flex",alignItems:"center",gap:8,marginBottom:3}}>
                  <span style={{fontWeight:700,fontSize:14,color:"var(--hf-text)"}}>{e.patientName}</span>
                  {e.status === "CONTACTED" && (
                    <span style={{background:"var(--hf-warning-soft)",color:"var(--hf-warning-text)",padding:"1px 8px",borderRadius:20,fontSize:11,fontWeight:700}}>Contacted</span>
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
                    style={{display:"flex",alignItems:"center",gap:5,padding:"6px 12px",background:LIGHT,color:NAVY_TEXT,border:`1px solid ${BORDER}`,borderRadius:7,fontSize:12,fontWeight:600,cursor:"pointer"}}>
                    <Phone size={12}/> Mark contacted
                  </button>
                )}
                <button onClick={()=>scheduledAction.mutate(e.id)}
                  style={{display:"flex",alignItems:"center",gap:5,padding:"6px 12px",background:"var(--hf-success-soft)",color:"var(--hf-success-text-strong)",border:"1px solid var(--hf-success-border)",borderRadius:7,fontSize:12,fontWeight:600,cursor:"pointer"}}>
                  <Calendar size={12}/> Booked
                </button>
                <button aria-label={`Remove ${e.patientName}`} onClick={()=>remove(e)}
                  style={{display:"flex",alignItems:"center",gap:5,padding:"6px 10px",background:"var(--hf-danger-soft)",color:"var(--hf-danger-text)",border:"1px solid var(--hf-danger-border)",borderRadius:7,fontSize:12,cursor:"pointer"}}>
                  <Trash2 size={12}/>
                </button>
              </div>
            </div>
          ))}
        </div>
      )}

      {dialogs}
      {showAdd && (
        <ModalShell title="Add to cancellation list" onClose={()=>setShowAdd(false)} width={480}
          footer={<>
            <button onClick={()=>setShowAdd(false)} style={{padding:"9px 18px",border:"1px solid var(--hf-border)",borderRadius:9,background:"var(--hf-surface)",fontSize:14,cursor:"pointer"}}>Cancel</button>
            <button onClick={()=>addEntry.mutate()} disabled={!form.patientId || addEntry.isPending}
              style={{display:"flex",alignItems:"center",gap:7,background:NAVY,color:"var(--hf-text-on-solid)",border:"none",borderRadius:9,padding:"9px 20px",fontSize:14,fontWeight:600,cursor:!form.patientId?"not-allowed":"pointer",opacity:!form.patientId?0.6:1}}>
              {addEntry.isPending ? "Adding..." : <><CheckCircle size={15}/> Add</>}
            </button></>}>
          <label style={{display:"block",fontSize:13,fontWeight:600,color:"var(--hf-text-secondary)",marginBottom:5}}>Patient *</label>
          <div style={{marginBottom:14}}>
            <PatientPicker value={patient} onChange={p=>{ setPatient(p); setForm(f=>({...f,patientId:p?.id??""})) }} />
          </div>

          <label style={{display:"block",fontSize:13,fontWeight:600,color:"var(--hf-text-secondary)",marginBottom:5}}>Preferred practitioner (optional)</label>
          <select value={form.practitionerId} onChange={e=>setForm(f=>({...f,practitionerId:e.target.value}))}
            style={{width:"100%",padding:"9px 12px",boxSizing:"border-box",border:"1.5px solid var(--hf-border)",borderRadius:8,fontSize:14,background:"var(--hf-surface)",marginBottom:14}}>
            <option value="">Any practitioner</option>
            {practitioners.map(p => <option key={p.id} value={p.id}>Dr. {p.fullName}</option>)}
          </select>

          <label style={{display:"block",fontSize:13,fontWeight:600,color:"var(--hf-text-secondary)",marginBottom:5}}>Appointment type (optional)</label>
          <input value={form.appointmentType} onChange={e=>setForm(f=>({...f,appointmentType:e.target.value}))} placeholder="e.g. Follow-up consultation"
            style={{width:"100%",padding:"9px 12px",boxSizing:"border-box",border:"1.5px solid var(--hf-border)",borderRadius:8,fontSize:14,marginBottom:14}}/>

          <label style={{display:"block",fontSize:13,fontWeight:600,color:"var(--hf-text-secondary)",marginBottom:5}}>Notes (optional)</label>
          <input value={form.notes} onChange={e=>setForm(f=>({...f,notes:e.target.value}))} placeholder="e.g. Available weekday mornings"
            style={{width:"100%",padding:"9px 12px",boxSizing:"border-box",border:"1.5px solid var(--hf-border)",borderRadius:8,fontSize:14}}/>

          {apiError && (
            <div role="alert" style={{marginTop:14,padding:"10px 12px",background:"var(--hf-danger-soft)",border:"1px solid var(--hf-danger-border)",borderRadius:8,fontSize:13,color:"var(--hf-danger-text)"}}>{apiError}</div>
          )}
        </ModalShell>
      )}
    </div>
  )
}

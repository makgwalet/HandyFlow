// src/pages/clinic/RecallsTab.tsx
// FIX: "no recall/follow-up dashboard" gap — followUpDays was captured on
// every consultation but never surfaced anywhere.
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { CalendarClock, Phone, AlertTriangle, Stethoscope } from "lucide-react"

interface Recall {
  consultationId: string; patientId: string; patientName: string; patientPhone?: string
  practitionerId?: string; practitionerName?: string
  consultedAt: string; followUpDays: number; dueDate: string; overdueDays: number
  diagnosis?: string
}

const NAVY="#1B3A6B"; const TEAL="#0D9488"; const RED="#DC2626"
const AMBER="#D97706"; const GRAY="#64748B"; const BORDER="#E2E8F0"; const LIGHT="#F8FAFC"

const fmtDT = (iso?:string) => iso ? new Date(iso).toLocaleDateString("en-ZA",{day:"numeric",month:"short",year:"numeric"}) : "—"
const unwrap = (r:any) => { const p=r.data?.data??r.data; return Array.isArray(p)?p:(p?.content??[]) }

export default function RecallsTab() {
  const { data: recalls=[], isLoading } = useQuery<Recall[]>({
    queryKey: ["clinic-recalls"],
    queryFn: async () => unwrap(await apiClient.get("/api/v1/clinic/recalls")),
  })

  const overdue = recalls.filter(r => r.overdueDays > 0)
  const dueToday = recalls.filter(r => r.overdueDays === 0)

  return (
    <div>
      <div style={{display:"grid",gridTemplateColumns:"repeat(3,1fr)",gap:12,marginBottom:24}}>
        {[
          {label:"Due for follow-up", value:recalls.length, color:NAVY, bg:LIGHT},
          {label:"Due today",         value:dueToday.length, color:TEAL, bg:"#F0FDFA"},
          {label:"Overdue",           value:overdue.length,  color:RED,  bg:"#FEF2F2"},
        ].map(k => (
          <div key={k.label} style={{background:k.bg,border:`1px solid ${BORDER}`,borderRadius:12,padding:"14px 18px"}}>
            <div style={{fontSize:11,fontWeight:700,color:k.color,textTransform:"uppercase",letterSpacing:"0.06em",marginBottom:6}}>{k.label}</div>
            <div style={{fontSize:22,fontWeight:800,color:k.color}}>{k.value}</div>
          </div>
        ))}
      </div>

      {isLoading ? (
        <div style={{textAlign:"center",padding:40,color:GRAY}}>Loading recalls...</div>
      ) : recalls.length === 0 ? (
        <div style={{textAlign:"center",padding:"60px 20px",color:GRAY,border:`1px dashed ${BORDER}`,borderRadius:12}}>
          <CalendarClock size={36} style={{marginBottom:12,opacity:0.4}}/>
          <div style={{fontWeight:600,color:"#475569",fontSize:15}}>No patients due for follow-up</div>
          <div style={{fontSize:13,marginTop:4}}>Patients appear here once their consultation's follow-up window is reached.</div>
        </div>
      ) : (
        <div style={{display:"flex",flexDirection:"column",gap:10}}>
          {recalls.map(r => (
            <div key={r.consultationId} style={{border:`1px solid ${BORDER}`,borderLeft:`4px solid ${r.overdueDays>0?RED:AMBER}`,borderRadius:10,padding:"14px 18px",background:"#fff",display:"flex",justifyContent:"space-between",alignItems:"center",gap:12}}>
              <div>
                <div style={{display:"flex",alignItems:"center",gap:8,marginBottom:4}}>
                  <span style={{fontWeight:700,fontSize:14,color:"#0F172A"}}>{r.patientName}</span>
                  {r.overdueDays > 0 ? (
                    <span style={{display:"flex",alignItems:"center",gap:3,background:"#FEF2F2",color:RED,padding:"1px 8px",borderRadius:20,fontSize:11,fontWeight:700}}>
                      <AlertTriangle size={10}/> {r.overdueDays}d overdue
                    </span>
                  ) : (
                    <span style={{background:"#FFFBEB",color:AMBER,padding:"1px 8px",borderRadius:20,fontSize:11,fontWeight:700}}>Due today</span>
                  )}
                </div>
                <div style={{fontSize:12,color:GRAY,display:"flex",alignItems:"center",gap:10,flexWrap:"wrap"}}>
                  <span>Seen {fmtDT(r.consultedAt)} · follow-up in {r.followUpDays}d (due {fmtDT(r.dueDate)})</span>
                  {r.practitionerName && <span style={{display:"flex",alignItems:"center",gap:3}}><Stethoscope size={11}/> Dr. {r.practitionerName}</span>}
                  {r.diagnosis && <span>· {r.diagnosis}</span>}
                </div>
              </div>
              {r.patientPhone && (
                <a href={`tel:${r.patientPhone}`} onClick={e=>e.stopPropagation()}
                  style={{display:"flex",alignItems:"center",gap:6,padding:"7px 14px",background:LIGHT,color:NAVY,border:`1px solid ${BORDER}`,borderRadius:8,fontSize:12,fontWeight:600,textDecoration:"none",flexShrink:0}}>
                  <Phone size={13}/> {r.patientPhone}
                </a>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

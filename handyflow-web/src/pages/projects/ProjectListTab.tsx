// src/pages/projects/ProjectListTab.tsx
import { useState } from "react"
import { useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { Search, Plus, ChevronRight } from "lucide-react"
import { CreateProjectModal } from "./CreateProjectModal"

interface Project { id:string; projectNumber:string; name:string; status:string; health:string; clientName:string|null; projectType:string; budgetTotal:number; budgetSpent:number; endDate:string|null; taskCount:number; completedTaskCount:number; openRiskCount:number; projectManagerName:string|null }

function unwrap<T>(r:any):T[] { const d=r?.data?.data??r?.data??[]; return Array.isArray(d)?d as T[]:d?.content??[] }
const fmtR  = (n:number) => `R ${Number(n??0).toLocaleString("en-ZA",{minimumFractionDigits:0,maximumFractionDigits:0})}`
const fmtDate = (d:string|null) => d ? new Date(d).toLocaleDateString("en-ZA") : "—"

const HEALTH: Record<string,{dot:string;text:string}> = {
  GREEN:{dot:"#16A34A",text:"#16A34A"}, AMBER:{dot:"#D97706",text:"#D97706"}, RED:{dot:"#DC2626",text:"#DC2626"},
}
const STATUS_BADGE: Record<string,{bg:string;color:string}> = {
  PLANNING:{bg:"#F1F5F9",color:"#475569"}, ACTIVE:{bg:"#DBEAFE",color:"#1D4ED8"},
  ON_HOLD:{bg:"#FEF3C7",color:"#92400E"}, COMPLETED:{bg:"#DCFCE7",color:"#166534"}, CANCELLED:{bg:"#FEE2E2",color:"#DC2626"},
}
const STATUSES = ["", "PLANNING", "ACTIVE", "ON_HOLD", "COMPLETED"]

export function ProjectListTab({ onOpen }: { onOpen:(id:string)=>void }) {
  const [status, setStatus] = useState("")
  const [search, setSearch] = useState("")
  const [showCreate, setShowCreate] = useState(false)
  const qc = useQueryClient()

  const { data:projects=[], isLoading } = useQuery<Project[]>({
    queryKey: ["pm-projects", status],
    queryFn: async () => { const url=status?`/api/v1/projects?status=${status}&size=50`:"/api/v1/projects?size=50"; const r=await apiClient.get(url); return unwrap<Project>(r) },
    staleTime: 30_000,
  })

  const filtered = projects.filter(p =>
    !search || p.name.toLowerCase().includes(search.toLowerCase()) ||
    p.projectNumber.toLowerCase().includes(search.toLowerCase()) ||
    (p.clientName??"").toLowerCase().includes(search.toLowerCase())
  )

  return (
    <div>
      {/* Toolbar */}
      <div style={{ display:"flex", justifyContent:"space-between", alignItems:"center", marginBottom:16, gap:10, flexWrap:"wrap" }}>
        <div style={{ display:"flex", gap:6, alignItems:"center", flexWrap:"wrap" }}>
          {STATUSES.map(s => (
            <button key={s} onClick={() => setStatus(s)}
              style={{ padding:"5px 12px", borderRadius:20, border:status===s?"1.5px solid #1B3A6B":"1px solid #E2E8F0", background:status===s?"#EFF6FF":"#fff", color:status===s?"#1B3A6B":"#64748B", fontSize:12, fontWeight:status===s?700:400, cursor:"pointer" }}>
              {s || "All"}
            </button>
          ))}
          <div style={{ position:"relative" }}>
            <Search size={13} style={{ position:"absolute", left:9, top:"50%", transform:"translateY(-50%)", color:"#94A3B8" }}/>
            <input value={search} onChange={e=>setSearch(e.target.value)} placeholder="Search…"
              style={{ padding:"6px 10px 6px 28px", border:"1px solid #E2E8F0", borderRadius:8, fontSize:13, outline:"none", width:180 }}/>
          </div>
        </div>
        <button onClick={() => setShowCreate(true)}
          style={{ display:"flex", alignItems:"center", gap:5, padding:"7px 14px", background:"#1B3A6B", color:"#fff", border:"none", borderRadius:8, fontSize:13, fontWeight:600, cursor:"pointer" }}>
          <Plus size={14}/> New Project
        </button>
      </div>

      {/* Table */}
      {isLoading ? (
        <div style={{ padding:"40px 0", textAlign:"center", color:"#94A3B8", fontSize:13 }}>Loading…</div>
      ) : filtered.length === 0 ? (
        <div style={{ padding:"40px 0", textAlign:"center", color:"#94A3B8" }}>
          <div style={{ fontWeight:600, color:"#475569", marginBottom:4 }}>No projects found</div>
          <div style={{ fontSize:13 }}>Try a different filter or create a new project</div>
        </div>
      ) : (
        <div style={{ border:"1px solid #E2E8F0", borderRadius:10, overflow:"hidden" }}>
          <table style={{ width:"100%", borderCollapse:"collapse" }}>
            <thead>
              <tr style={{ background:"#F8FAFC" }}>
                {["Number","Project","Client","Status","Health","Budget","Tasks","End Date",""].map(h => (
                  <th key={h} style={{ padding:"10px 14px", textAlign:"left", fontSize:11, fontWeight:700, color:"#94A3B8", textTransform:"uppercase", letterSpacing:"0.05em" }}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {filtered.map((p, i) => {
                const h  = HEALTH[p.health] ?? HEALTH.GREEN
                const st = STATUS_BADGE[p.status] ?? STATUS_BADGE.PLANNING
                const taskPct = p.taskCount > 0 ? Math.round((p.completedTaskCount/p.taskCount)*100) : 0
                const spentPct = p.budgetTotal > 0 ? Math.round((p.budgetSpent/p.budgetTotal)*100) : 0
                return (
                  <tr key={p.id} onClick={() => onOpen(p.id)}
                    style={{ borderTop:"1px solid #F1F5F9", background:i%2===0?"#fff":"#FAFAFA", cursor:"pointer" }}
                    onMouseEnter={e => e.currentTarget.style.background="#F0F7FF"}
                    onMouseLeave={e => e.currentTarget.style.background=i%2===0?"#fff":"#FAFAFA"}>
                    <td style={{ padding:"10px 14px", fontSize:12, color:"#94A3B8", fontWeight:500, whiteSpace:"nowrap" }}>{p.projectNumber}</td>
                    <td style={{ padding:"10px 14px" }}>
                      <div style={{ fontSize:13, fontWeight:600, color:"#0F172A" }}>{p.name}</div>
                      {p.projectManagerName && <div style={{ fontSize:11, color:"#94A3B8" }}>{p.projectManagerName}</div>}
                    </td>
                    <td style={{ padding:"10px 14px", fontSize:12, color:"#64748B" }}>{p.clientName??"—"}</td>
                    <td style={{ padding:"10px 14px" }}><span style={{ background:st.bg, color:st.color, fontSize:10, fontWeight:700, padding:"2px 8px", borderRadius:20 }}>{p.status.replace("_"," ")}</span></td>
                    <td style={{ padding:"10px 14px" }}>
                      <div style={{ display:"flex", alignItems:"center", gap:5 }}>
                        <span style={{ width:8, height:8, borderRadius:"50%", background:h.dot, display:"inline-block" }}/>
                        <span style={{ fontSize:11, fontWeight:700, color:h.text }}>{p.health}</span>
                      </div>
                    </td>
                    <td style={{ padding:"10px 14px" }}>
                      <div style={{ fontSize:12, color:"#0F172A", fontWeight:600 }}>{fmtR(p.budgetSpent)}</div>
                      <div style={{ fontSize:11, color:"#94A3B8" }}>/ {fmtR(p.budgetTotal)}</div>
                      <div style={{ height:3, background:"#F1F5F9", borderRadius:2, marginTop:3, width:60 }}>
                        <div style={{ height:"100%", width:`${Math.min(spentPct,100)}%`, background:spentPct>90?"#EF4444":spentPct>75?"#F59E0B":"#22C55E", borderRadius:2 }}/>
                      </div>
                    </td>
                    <td style={{ padding:"10px 14px", fontSize:12, color:"#64748B" }}>{p.completedTaskCount}/{p.taskCount} <span style={{ color:"#CBD5E1" }}>({taskPct}%)</span></td>
                    <td style={{ padding:"10px 14px", fontSize:12, color:"#64748B", whiteSpace:"nowrap" }}>{fmtDate(p.endDate)}</td>
                    <td style={{ padding:"10px 14px" }}><ChevronRight size={15} color="#CBD5E1"/></td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}

      {showCreate && <CreateProjectModal onClose={() => setShowCreate(false)} onCreated={(id) => { setShowCreate(false); onOpen(id) }}/>}
    </div>
  )
}

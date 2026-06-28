// src/pages/projects/ProjectDashboard.tsx
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { TrendingUp, AlertTriangle, Clock, CheckCircle, FolderOpen, Plus } from "lucide-react"
import { useState } from "react"
import { CreateProjectModal } from "./CreateProjectModal"

interface Summary { activeProjects:number; redProjects:number; amberProjects:number; pendingTimeApprovals:number; openRedRisks:number }
interface Project { id:string; projectNumber:string; name:string; status:string; health:string; clientName:string|null; budgetTotal:number; budgetSpent:number; endDate:string|null; taskCount:number; completedTaskCount:number; openRiskCount:number }

function unwrap<T>(r:any):T[] { const d=r?.data?.data??r?.data??[]; return Array.isArray(d)?d as T[]:d?.content??[] }
const fmtR = (n:number) => `R ${Number(n??0).toLocaleString("en-ZA",{minimumFractionDigits:0,maximumFractionDigits:0})}`
const fmtDate = (d:string|null) => d ? new Date(d).toLocaleDateString("en-ZA") : "—"

const HEALTH: Record<string,{dot:string;label:string}> = {
  GREEN: {dot:"#16A34A",label:"On Track"},
  AMBER: {dot:"#D97706",label:"Watch"},
  RED:   {dot:"#DC2626",label:"At Risk"},
}

const STATUS_BADGE: Record<string,{bg:string;color:string}> = {
  PLANNING:  {bg:"#F1F5F9",color:"#475569"},
  ACTIVE:    {bg:"#DBEAFE",color:"#1D4ED8"},
  ON_HOLD:   {bg:"#FEF3C7",color:"#92400E"},
  COMPLETED: {bg:"#DCFCE7",color:"#166534"},
  CANCELLED: {bg:"#FEE2E2",color:"#DC2626"},
}

export function ProjectDashboard({ onOpen, onList }: { onOpen:(id:string)=>void; onList:()=>void }) {
  const [showCreate, setShowCreate] = useState(false)

  const { data: summary } = useQuery<Summary>({
    queryKey: ["pm-summary"],
    queryFn: async () => { const r = await apiClient.get("/api/v1/projects/summary"); return r.data?.data??r.data },
    staleTime: 30_000,
  })
  const { data: projects = [] } = useQuery<Project[]>({
    queryKey: ["pm-projects-active"],
    queryFn: async () => { const r = await apiClient.get("/api/v1/projects?status=ACTIVE&size=20"); return unwrap<Project>(r) },
    staleTime: 30_000,
  })
  const redAmber = projects.filter(p => p.health === "RED" || p.health === "AMBER")

  const KPIs = [
    { label:"Active Projects",  value: summary?.activeProjects??0,       icon:FolderOpen,    color:"#1D4ED8", bg:"#EFF6FF" },
    { label:"At Risk (Red)",    value: summary?.redProjects??0,           icon:AlertTriangle, color:"#DC2626", bg:"#FEF2F2" },
    { label:"Watch (Amber)",    value: summary?.amberProjects??0,         icon:TrendingUp,    color:"#D97706", bg:"#FFFBEB" },
    { label:"Time Approvals",   value: summary?.pendingTimeApprovals??0,  icon:Clock,         color:"#7C3AED", bg:"#F5F3FF" },
    { label:"Open Red Risks",   value: summary?.openRedRisks??0,          icon:AlertTriangle, color:"#DC2626", bg:"#FEF2F2" },
  ]

  return (
    <div>
      {/* KPI row */}
      <div style={{ display:"grid", gridTemplateColumns:"repeat(5,1fr)", gap:12, marginBottom:28 }}>
        {KPIs.map(k => (
          <div key={k.label} style={{ background:k.bg, borderRadius:12, padding:"14px 16px" }}>
            <div style={{ display:"flex", alignItems:"center", gap:8, marginBottom:8 }}>
              <k.icon size={15} color={k.color} />
              <span style={{ fontSize:11, fontWeight:600, color:"#94A3B8", textTransform:"uppercase", letterSpacing:"0.05em" }}>{k.label}</span>
            </div>
            <div style={{ fontSize:28, fontWeight:800, color:k.color }}>{k.value}</div>
          </div>
        ))}
      </div>

      {/* Actions */}
      <div style={{ display:"flex", justifyContent:"space-between", alignItems:"center", marginBottom:16 }}>
        <h3 style={{ margin:0, fontSize:15, fontWeight:700, color:"#0F172A" }}>
          {redAmber.length > 0 ? `${redAmber.length} project${redAmber.length!==1?"s":""} need attention` : "Active Projects"}
        </h3>
        <div style={{ display:"flex", gap:8 }}>
          <button onClick={onList}
            style={{ padding:"7px 14px", border:"1px solid #E2E8F0", borderRadius:8, background:"#fff", fontSize:13, cursor:"pointer", color:"#64748B" }}>
            View All
          </button>
          <button onClick={() => setShowCreate(true)}
            style={{ display:"flex", alignItems:"center", gap:5, padding:"7px 14px", background:"#1B3A6B", color:"#fff", border:"none", borderRadius:8, fontSize:13, fontWeight:600, cursor:"pointer" }}>
            <Plus size={14}/> New Project
          </button>
        </div>
      </div>

      {/* Project cards */}
      {projects.length === 0 ? (
        <div style={{ textAlign:"center", padding:"48px 0", color:"#94A3B8" }}>
          <FolderOpen size={40} style={{ marginBottom:12, opacity:.3 }}/>
          <div style={{ fontWeight:600, color:"#475569", marginBottom:4 }}>No active projects</div>
          <div style={{ fontSize:13 }}>Create your first project to get started</div>
        </div>
      ) : (
        <div style={{ display:"grid", gridTemplateColumns:"repeat(auto-fill, minmax(320px,1fr))", gap:12 }}>
          {projects.map(p => {
            const h = HEALTH[p.health] ?? HEALTH.GREEN
            const st = STATUS_BADGE[p.status] ?? STATUS_BADGE.PLANNING
            const spentPct = p.budgetTotal > 0 ? Math.min(100,(p.budgetSpent/p.budgetTotal)*100) : 0
            const taskPct  = p.taskCount  > 0 ? Math.round((p.completedTaskCount/p.taskCount)*100) : 0
            return (
              <div key={p.id} onClick={() => onOpen(p.id)}
                style={{ border:"1px solid #E2E8F0", borderRadius:12, padding:"16px 18px", cursor:"pointer", transition:"box-shadow 0.15s" }}
                onMouseEnter={e => e.currentTarget.style.boxShadow="0 4px 16px rgba(0,0,0,0.08)"}
                onMouseLeave={e => e.currentTarget.style.boxShadow="none"}>
                <div style={{ display:"flex", justifyContent:"space-between", alignItems:"flex-start", marginBottom:10 }}>
                  <div style={{ flex:1, minWidth:0 }}>
                    <div style={{ display:"flex", alignItems:"center", gap:6, marginBottom:4 }}>
                      <span style={{ fontSize:11, color:"#94A3B8", fontWeight:500 }}>{p.projectNumber}</span>
                      <span style={{ background:st.bg, color:st.color, fontSize:10, fontWeight:700, padding:"1px 7px", borderRadius:20 }}>{p.status.replace("_"," ")}</span>
                    </div>
                    <div style={{ fontSize:14, fontWeight:700, color:"#0F172A", marginBottom:2, overflow:"hidden", textOverflow:"ellipsis", whiteSpace:"nowrap" }}>{p.name}</div>
                    {p.clientName && <div style={{ fontSize:12, color:"#64748B" }}>{p.clientName}</div>}
                  </div>
                  <div style={{ display:"flex", alignItems:"center", gap:5, flexShrink:0, marginLeft:10 }}>
                    <span style={{ width:8, height:8, borderRadius:"50%", background:h.dot, display:"inline-block" }}/>
                    <span style={{ fontSize:11, fontWeight:700, color:h.dot }}>{h.label}</span>
                  </div>
                </div>
                {/* Task progress */}
                <div style={{ marginBottom:10 }}>
                  <div style={{ display:"flex", justifyContent:"space-between", fontSize:11, color:"#94A3B8", marginBottom:3 }}>
                    <span>Tasks {p.completedTaskCount}/{p.taskCount}</span><span>{taskPct}%</span>
                  </div>
                  <div style={{ height:5, background:"#F1F5F9", borderRadius:3 }}>
                    <div style={{ height:"100%", width:`${taskPct}%`, background: p.health==="RED"?"#EF4444":p.health==="AMBER"?"#F59E0B":"#22C55E", borderRadius:3 }}/>
                  </div>
                </div>
                <div style={{ display:"flex", justifyContent:"space-between", fontSize:12, color:"#64748B" }}>
                  <span>{fmtR(p.budgetSpent)} <span style={{ color:"#94A3B8" }}>/ {fmtR(p.budgetTotal)}</span></span>
                  <span>📅 {fmtDate(p.endDate)}</span>
                </div>
                {p.openRiskCount > 0 && (
                  <div style={{ marginTop:6, fontSize:11, color:"#DC2626", fontWeight:600 }}>⚠ {p.openRiskCount} open risk{p.openRiskCount!==1?"s":""}</div>
                )}
              </div>
            )
          })}
        </div>
      )}

      {showCreate && <CreateProjectModal onClose={() => setShowCreate(false)} onCreated={(id) => { setShowCreate(false); onOpen(id) }} />}
    </div>
  )
}

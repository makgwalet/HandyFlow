// src/pages/projects/tabs/OverviewTab.tsx
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../../api/client"
import type { Project } from "../ProjectDetailTab"
import { CheckCircle } from "lucide-react"

function unwrap<T>(r:any):T[] { const d=r?.data?.data??r?.data??[]; return Array.isArray(d)?d as T[]:d?.content??[] }
const fmtR = (n:number) => `R ${Number(n??0).toLocaleString("en-ZA",{minimumFractionDigits:0,maximumFractionDigits:0})}`
const fmtDate = (d:string|null) => d ? new Date(d).toLocaleDateString("en-ZA") : "—"

const RATING_STYLE: Record<string,{bg:string;color:string}> = {
  RED:{bg:"#FEF2F2",color:"#DC2626"}, AMBER:{bg:"#FEF3C7",color:"#92400E"}, GREEN:{bg:"#DCFCE7",color:"#166534"},
}

interface Task  { id:string; title:string; status:string; plannedEnd:string|null; isMilestone:boolean; progressPct:number; isCritical:boolean }
interface Risk  { id:string; title:string; rating:string; riskScore:number; status:string; category:string|null }
interface EVM   { spi:number; cpi:number; eac:number; etc:number; totalActual:number; totalCommitted:number; completionPct:number }

export function OverviewTab({ project }: { project: Project }) {
  const { data:milestones=[] } = useQuery<Task[]>({ queryKey:["pm-milestones",project.id], queryFn:async()=>{ const r=await apiClient.get(`/api/v1/projects/${project.id}/milestones`); return unwrap<Task>(r) }, staleTime:30_000 })
  const { data:risks=[] }      = useQuery<Risk[]>({ queryKey:["pm-risks",project.id], queryFn:async()=>{ const r=await apiClient.get(`/api/v1/projects/${project.id}/risks`); return unwrap<Risk>(r) }, staleTime:30_000 })
  const taskPct = project.taskCount>0 ? Math.round((project.completedTaskCount/project.taskCount)*100) : 0

  // planPct omitted — server auto-computes from project baseline dates (H-8 fix)
  const { data:evm } = useQuery<EVM>({ queryKey:["pm-evm",project.id,taskPct], queryFn:async()=>{ const r=await apiClient.get(`/api/v1/projects/${project.id}/budget/evm?earnedPct=${taskPct}`); return r.data?.data??r.data }, staleTime:60_000 })

  const spentPct = project.budgetTotal>0 ? (project.budgetSpent/project.budgetTotal)*100 : 0
  const openRisks = risks.filter(r=>r.status==="OPEN")

  return (
    <div style={{ display:"grid", gridTemplateColumns:"1fr 1fr", gap:16 }}>

      {/* Budget card */}
      <Section title="Budget & Cost">
        <div style={{ display:"grid", gridTemplateColumns:"1fr 1fr", gap:12, marginBottom:14 }}>
          <Stat label="Total Budget"  value={fmtR(project.budgetTotal)}    />
          <Stat label="Spent"         value={fmtR(project.budgetSpent)}     color={spentPct>100?"#DC2626":undefined} />
          <Stat label="Committed"     value={fmtR(project.budgetCommitted)} color="#D97706" />
          <Stat label="Variance"      value={fmtR(project.budgetVariance)}  color={project.budgetVariance<0?"#DC2626":"#059669"} />
        </div>
        <ProgressBar pct={spentPct} label="Budget used" color={spentPct>90?"#EF4444":spentPct>75?"#F59E0B":"#22C55E"}/>
        {evm && (
          <div style={{ display:"grid", gridTemplateColumns:"repeat(4,1fr)", gap:8, marginTop:14, paddingTop:14, borderTop:"1px solid #F1F5F9" }}>
            <EVMStat label="SPI" value={evm.spi?.toFixed(2)??"—"} good={(evm.spi??1)>=1}/>
            <EVMStat label="CPI" value={evm.cpi?.toFixed(2)??"—"} good={(evm.cpi??1)>=1}/>
            <EVMStat label="EAC" value={fmtR(evm.eac)}/>
            <EVMStat label="ETC" value={fmtR(evm.etc)}/>
          </div>
        )}
      </Section>

      {/* Schedule card */}
      <Section title="Schedule">
        <div style={{ display:"grid", gridTemplateColumns:"1fr 1fr", gap:12, marginBottom:14 }}>
          <Stat label="Start"        value={fmtDate(project.startDate)}/>
          <Stat label="End"          value={fmtDate(project.endDate)}/>
          <Stat label="Baseline End" value={fmtDate(project.baselineEnd)}/>
          <Stat label="Contract Ref" value={project.contractRef??"—"}/>
        </div>
        <ProgressBar pct={taskPct} label={`Tasks ${project.completedTaskCount}/${project.taskCount}`} color="#1B3A6B"/>
        {(project.cidbGrade||project.nhbrcNumber) && (
          <div style={{ display:"flex", gap:8, marginTop:14, paddingTop:14, borderTop:"1px solid #F1F5F9" }}>
            {project.cidbGrade && <Tag label="CIDB" value={project.cidbGrade}/>}
            {project.nhbrcNumber && <Tag label="NHBRC" value={project.nhbrcNumber}/>}
          </div>
        )}
      </Section>

      {/* Milestones */}
      <Section title={`Milestones (${milestones.length})`}>
        {milestones.length===0
          ? <Empty text="No milestones — add MILESTONE tasks in the Tasks tab"/>
          : milestones.slice(0,6).map(m=>(
            <div key={m.id} style={{ display:"flex", alignItems:"center", gap:10, padding:"8px 0", borderBottom:"1px solid #F8FAFC" }}>
              {m.status==="COMPLETED"
                ? <CheckCircle size={14} color="#16A34A" style={{ flexShrink:0 }}/>
                : <div style={{ width:14, height:14, borderRadius:"50%", border:`2px solid ${m.isCritical?"#EF4444":"#CBD5E1"}`, flexShrink:0 }}/>}
              <div style={{ flex:1, minWidth:0 }}>
                <div style={{ fontSize:13, fontWeight:600, color:"#0F172A", overflow:"hidden", textOverflow:"ellipsis", whiteSpace:"nowrap" }}>{m.title}</div>
                <div style={{ fontSize:11, color:"#94A3B8" }}>{fmtDate(m.plannedEnd)}</div>
              </div>
              <span style={{ fontSize:12, fontWeight:700, color:m.status==="COMPLETED"?"#16A34A":"#64748B", flexShrink:0 }}>
                {m.status==="COMPLETED" ? "Done" : `${m.progressPct?.toFixed(0)??0}%`}
              </span>
            </div>
          ))
        }
      </Section>

      {/* Risks */}
      <Section title={`Open Risks (${openRisks.length})`}>
        {openRisks.length===0
          ? <Empty text="No open risks — use the Risks tab to log issues"/>
          : openRisks.slice(0,5).map(r=>{
            const rt = RATING_STYLE[r.rating]??RATING_STYLE.GREEN
            return (
              <div key={r.id} style={{ display:"flex", alignItems:"center", gap:10, padding:"8px 0", borderBottom:"1px solid #F8FAFC" }}>
                <span style={{ background:rt.bg, color:rt.color, fontSize:10, fontWeight:700, padding:"2px 7px", borderRadius:12, flexShrink:0 }}>{r.rating}</span>
                <div style={{ flex:1, minWidth:0 }}>
                  <div style={{ fontSize:13, fontWeight:600, color:"#0F172A", overflow:"hidden", textOverflow:"ellipsis", whiteSpace:"nowrap" }}>{r.title}</div>
                  {r.category && <div style={{ fontSize:11, color:"#94A3B8" }}>{r.category}</div>}
                </div>
                <span style={{ fontSize:14, fontWeight:800, color:rt.color, flexShrink:0 }}>{r.riskScore}</span>
              </div>
            )
          })
        }
      </Section>
    </div>
  )
}

function Section({ title, children }:{ title:string; children:React.ReactNode }) {
  return (
    <div style={{ border:"1px solid #E2E8F0", borderRadius:10, padding:"16px 18px" }}>
      <div style={{ fontSize:13, fontWeight:700, color:"#0F172A", marginBottom:14 }}>{title}</div>
      {children}
    </div>
  )
}
function Stat({ label, value, color }:{ label:string; value:string; color?:string }) {
  return (
    <div>
      <div style={{ fontSize:11, color:"#94A3B8", fontWeight:600, textTransform:"uppercase", letterSpacing:"0.04em", marginBottom:3 }}>{label}</div>
      <div style={{ fontSize:15, fontWeight:700, color:color??"#0F172A" }}>{value}</div>
    </div>
  )
}
function ProgressBar({ pct, label, color }:{ pct:number; label:string; color:string }) {
  return (
    <div>
      <div style={{ display:"flex", justifyContent:"space-between", fontSize:11, color:"#94A3B8", marginBottom:4 }}>
        <span>{label}</span><span>{pct.toFixed(1)}%</span>
      </div>
      <div style={{ height:6, background:"#F1F5F9", borderRadius:3 }}>
        <div style={{ height:"100%", width:`${Math.min(pct,100)}%`, background:color, borderRadius:3 }}/>
      </div>
    </div>
  )
}
function EVMStat({ label, value, good }:{ label:string; value:string; good?:boolean }) {
  return (
    <div style={{ textAlign:"center" }}>
      <div style={{ fontSize:10, color:"#94A3B8", fontWeight:600, marginBottom:2 }}>{label}</div>
      <div style={{ fontSize:13, fontWeight:700, color:good===undefined?"#0F172A":good?"#059669":"#DC2626" }}>{value}</div>
    </div>
  )
}
function Tag({ label, value }:{ label:string; value:string }) {
  return <div style={{ background:"#EFF6FF", borderRadius:8, padding:"5px 10px" }}><div style={{ fontSize:10, color:"#64748B", fontWeight:600 }}>{label}</div><div style={{ fontSize:13, fontWeight:700, color:"#1B3A6B" }}>{value}</div></div>
}
function Empty({ text }:{ text:string }) {
  return <div style={{ fontSize:13, color:"#94A3B8", padding:"8px 0" }}>{text}</div>
}

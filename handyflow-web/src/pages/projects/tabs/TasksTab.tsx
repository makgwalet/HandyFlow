// src/pages/projects/tabs/TasksTab.tsx
import React, { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Plus, Flag } from 'lucide-react'
import { apiClient } from '../../../api/client'

interface Task {
  id:string; taskNumber:string; title:string; status:string; priority:string
  taskType:string; assigneeName:string|null; plannedStart:string|null; plannedEnd:string|null
  progressPct:number; estimatedHours:number|null; actualHours:number; isCritical:boolean; isMilestone:boolean
  phaseId:string|null; notes:string|null
}
interface Phase { id:string; name:string; status:string }

function unwrap<T>(res:any):T[] { const d=res?.data?.data??res?.data??[]; return Array.isArray(d)?d as T[]:d?.content??[] }
const fmtDate=(d:string|null)=>d?new Date(d).toLocaleDateString('en-ZA'):'—'
const inp:React.CSSProperties={width:'100%',padding:'9px 12px',border:'1.5px solid var(--hf-border)',borderRadius:9,fontSize:14,boxSizing:'border-box' as const,outline:'none',background:'var(--hf-surface)'}

const PRIORITY_COLOR:Record<string,{bg:string;color:string}>={
  LOW:{bg:'var(--hf-surface-sunken)',color:'var(--hf-text-tertiary)'},MEDIUM:{bg:'var(--hf-info-soft-strong)',color:'var(--hf-info-text)'},
  HIGH:{bg:'var(--hf-warning-soft-strong)',color:'var(--hf-warning-text-deep)'},CRITICAL:{bg:'var(--hf-danger-soft)',color:'var(--hf-danger-text)'},
}
const STATUS_COLS=['NOT_STARTED','IN_PROGRESS','BLOCKED','COMPLETED']
const STATUS_LABELS:Record<string,{label:string;bg:string;color:string}>={
  NOT_STARTED:{label:'Not Started',bg:'var(--hf-surface-sunken)',color:'var(--hf-text-tertiary)'},
  IN_PROGRESS:{label:'In Progress',bg:'var(--hf-info-soft-strong)',color:'var(--hf-info-text)'},
  BLOCKED:{label:'Blocked',bg:'var(--hf-danger-soft)',color:'var(--hf-danger-text)'},
  COMPLETED:{label:'Completed',bg:'var(--hf-success-soft-strong)',color:'var(--hf-success-text-strong)'},
  CANCELLED:{label:'Cancelled',bg:'var(--hf-surface-sunken)',color:'var(--hf-text-faint)'},
}

export function TasksTab({projectId}:{projectId:string}) {
  const qc=useQueryClient()
  const [view,setView]=useState<'kanban'|'list'>('list')
  const [showCreate,setShowCreate]=useState(false)
  const [statusFilter,setFilter]=useState('')
  const [err,setErr]=useState('')

  const initF=()=>({title:'',taskType:'TASK',priority:'MEDIUM',phaseId:'',assigneeName:'',plannedStart:'',plannedEnd:'',estimatedHours:'',notes:'',requiresInspection:false})
  const [form,setForm]=useState(initF())
  const sf=(k:string,v:any)=>setForm(p=>({...p,[k]:v}))

  const {data:tasks=[],isLoading}=useQuery<Task[]>({
    queryKey:['pm-tasks',projectId],
    queryFn:async()=>{const r=await apiClient.get(`/api/v1/projects/${projectId}/tasks`);return unwrap<Task>(r)},
    staleTime:30_000,
  })
  const {data:phases=[]}=useQuery<Phase[]>({
    queryKey:['pm-phases',projectId],
    queryFn:async()=>{const r=await apiClient.get(`/api/v1/projects/${projectId}/phases`);return unwrap<Phase>(r)},
    staleTime:60_000,
  })

  const createMut=useMutation({
    mutationFn:(body:any)=>apiClient.post(`/api/v1/projects/${projectId}/tasks`,body),
    onSuccess:()=>{qc.invalidateQueries({queryKey:['pm-tasks',projectId]});qc.invalidateQueries({queryKey:['pm-project']});setShowCreate(false);setForm(initF());setErr('')},
    onError:(e:any)=>setErr(e.response?.data?.message||'Failed to create task'),
  })

  const statusMut=useMutation({
    mutationFn:({taskId,action,pct}:{taskId:string;action:string;pct?:number})=>
      apiClient.post(`/api/v1/projects/tasks/${taskId}/status`,{action,progressPct:pct??null}),
    onSuccess:()=>qc.invalidateQueries({queryKey:['pm-tasks',projectId]}),
  })

  const filtered=statusFilter?tasks.filter(t=>t.status===statusFilter):tasks

  return (
    <div>
      {/* Toolbar */}
      <div style={{display:'flex',justifyContent:'space-between',alignItems:'center',marginBottom:16,gap:10,flexWrap:'wrap' as const}}>
        <div style={{display:'flex',gap:6}}>
          {['','NOT_STARTED','IN_PROGRESS','BLOCKED','COMPLETED'].map(s=>(
            <button key={s} onClick={()=>setFilter(s)}
              style={{padding:'6px 12px',borderRadius:20,border:statusFilter===s?'1.5px solid var(--hf-primary)':'1px solid var(--hf-border)',background:statusFilter===s?'var(--hf-info-soft)':'var(--hf-surface)',color:statusFilter===s?'var(--hf-primary-text)':'var(--hf-text-muted)',fontSize:12,fontWeight:statusFilter===s?700:400,cursor:'pointer'}}>
              {s?s.replace('_',' '):'All'}
            </button>
          ))}
        </div>
        <div style={{display:'flex',gap:8}}>
          {(['list','kanban'] as const).map(v=>(
            <button key={v} onClick={()=>setView(v)}
              style={{padding:'6px 12px',borderRadius:8,border:view===v?'1.5px solid var(--hf-primary)':'1px solid var(--hf-border)',background:view===v?'var(--hf-info-soft)':'var(--hf-surface)',color:view===v?'var(--hf-primary-text)':'var(--hf-text-muted)',fontSize:12,cursor:'pointer',fontWeight:view===v?700:400}}>
              {v==='list'?'≡ List':'⊞ Kanban'}
            </button>
          ))}
          <button onClick={()=>{setShowCreate(true);setErr('')}}
            style={{display:'flex',alignItems:'center',gap:5,padding:'8px 14px',background:'var(--hf-primary)',color:'var(--hf-text-on-solid)',border:'none',borderRadius:9,fontSize:13,fontWeight:600,cursor:'pointer'}}>
            <Plus size={14}/> Add Task
          </button>
        </div>
      </div>

      {isLoading ? <div style={{padding:40,textAlign:'center',color:'var(--hf-text-faint)'}}>Loading…</div>
        : view==='kanban' ? <KanbanView tasks={filtered} onAction={statusMut.mutate}/>
        : <ListView tasks={filtered} onAction={statusMut.mutate}/>
      }

      {/* Create Task Modal */}
      {showCreate && (
        <div style={{position:'fixed',inset:0,background:'rgba(15,23,42,0.5)',display:'flex',alignItems:'center',justifyContent:'center',zIndex:1000}}>
          <div style={{background:'var(--hf-surface)',borderRadius:14,padding:28,width:580,maxHeight:'90vh',overflowY:'auto',boxShadow:'0 20px 60px rgba(0,0,0,0.2)'}}>
            <div style={{display:'flex',justifyContent:'space-between',alignItems:'center',marginBottom:20}}>
              <h3 style={{margin:0,fontSize:16,fontWeight:700}}>Add Task</h3>
              <button onClick={()=>setShowCreate(false)} style={{background:'none',border:'none',cursor:'pointer',color:'var(--hf-text-faint)',fontSize:20}}>×</button>
            </div>
            <div style={{display:'grid',gridTemplateColumns:'1fr 1fr',gap:12}}>
              <Fld label="Title *" span={2}><input value={form.title} onChange={e=>sf('title',e.target.value)} placeholder="Task description" style={inp} autoFocus/></Fld>
              <Fld label="Type">
                <select value={form.taskType} onChange={e=>sf('taskType',e.target.value)} style={inp}>
                  {['TASK','MILESTONE','SUMMARY'].map(t=><option key={t} value={t}>{t}</option>)}
                </select>
              </Fld>
              <Fld label="Priority">
                <select value={form.priority} onChange={e=>sf('priority',e.target.value)} style={inp}>
                  {['LOW','MEDIUM','HIGH','CRITICAL'].map(p=><option key={p} value={p}>{p}</option>)}
                </select>
              </Fld>
              {phases.length>0 && (
                <Fld label="Phase" span={2}>
                  <select value={form.phaseId} onChange={e=>sf('phaseId',e.target.value)} style={inp}>
                    <option value="">No phase</option>
                    {phases.map(p=><option key={p.id} value={p.id}>{p.name}</option>)}
                  </select>
                </Fld>
              )}
              <Fld label="Assignee"><input value={form.assigneeName} onChange={e=>sf('assigneeName',e.target.value)} placeholder="Name" style={inp}/></Fld>
              <Fld label="Estimated Hours"><input type="number" value={form.estimatedHours} onChange={e=>sf('estimatedHours',e.target.value)} placeholder="0" style={inp}/></Fld>
              <Fld label="Planned Start"><input type="date" value={form.plannedStart} onChange={e=>sf('plannedStart',e.target.value)} style={inp}/></Fld>
              <Fld label="Planned End"><input type="date" value={form.plannedEnd} onChange={e=>sf('plannedEnd',e.target.value)} style={inp}/></Fld>
              <Fld label="Notes" span={2}><textarea value={form.notes} onChange={e=>sf('notes',e.target.value)} style={{...inp,minHeight:50,resize:'vertical' as const}}/></Fld>
            </div>
            {err&&<div style={{marginTop:10,padding:'8px 12px',background:'var(--hf-danger-soft)',border:'1px solid var(--hf-danger-border)',borderRadius:8,color:'var(--hf-danger-text)',fontSize:13}}>{err}</div>}
            <div style={{display:'flex',justifyContent:'flex-end',gap:10,marginTop:20}}>
              <button onClick={()=>setShowCreate(false)} style={{padding:'9px 16px',border:'1px solid var(--hf-border)',borderRadius:9,background:'var(--hf-surface)',fontSize:13,cursor:'pointer'}}>Cancel</button>
              <button onClick={()=>{
                if(!form.title.trim()){setErr('Title is required');return}
                createMut.mutate({title:form.title.trim(),taskType:form.taskType,priority:form.priority,phaseId:form.phaseId||null,assigneeName:form.assigneeName||null,plannedStart:form.plannedStart||null,plannedEnd:form.plannedEnd||null,estimatedHours:form.estimatedHours?parseFloat(form.estimatedHours):null,notes:form.notes||null,requiresInspection:form.requiresInspection})
              }} disabled={createMut.isPending}
                style={{padding:'9px 16px',background:'var(--hf-primary)',color:'var(--hf-text-on-solid)',border:'none',borderRadius:9,fontSize:13,fontWeight:600,cursor:'pointer',opacity:createMut.isPending?.6:1}}>
                {createMut.isPending?'Saving…':'Add Task'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

function ListView({tasks,onAction}:{tasks:Task[];onAction:(a:any)=>void}) {
  if(!tasks.length) return <div style={{textAlign:'center',padding:'50px 20px',color:'var(--hf-text-faint)'}}><div style={{fontWeight:600,color:'var(--hf-text-tertiary)',marginBottom:4}}>No tasks</div><div style={{fontSize:13}}>Click "Add Task" to create your first task</div></div>
  return (
    <div style={{border:'1px solid var(--hf-border)',borderRadius:12,overflow:'hidden'}}>
      <table style={{width:'100%',borderCollapse:'collapse'}}>
        <thead><tr style={{background:'var(--hf-surface-muted)'}}>
          {['#','Title','Assignee','Priority','Status','Dates','Progress',''].map(h=>(
            <th key={h} style={{padding:'10px 14px',textAlign:'left' as const,fontSize:11,fontWeight:700,color:'var(--hf-text-faint)',textTransform:'uppercase' as const,letterSpacing:'0.05em'}}>{h}</th>
          ))}
        </tr></thead>
        <tbody>
          {tasks.map((t,i)=>{
            const pr=PRIORITY_COLOR[t.priority]??PRIORITY_COLOR.MEDIUM
            const st=STATUS_LABELS[t.status]??STATUS_LABELS.NOT_STARTED
            return (
              <tr key={t.id} style={{borderTop:'1px solid var(--hf-border-subtle)',background:i%2===0?'var(--hf-surface)':'var(--hf-surface-muted)'}}>
                <td style={{padding:'10px 14px',fontSize:12,color:'var(--hf-text-faint)',whiteSpace:'nowrap' as const}}>{t.taskNumber}{t.isCritical&&' 🔴'}{t.isMilestone&&' ◆'}</td>
                <td style={{padding:'10px 14px',fontSize:13,fontWeight:600,color:'var(--hf-text)',maxWidth:220,overflow:'hidden',textOverflow:'ellipsis',whiteSpace:'nowrap' as const}}>{t.title}</td>
                <td style={{padding:'10px 14px',fontSize:12,color:'var(--hf-text-muted)'}}>{t.assigneeName??'—'}</td>
                <td style={{padding:'10px 14px'}}><span style={{background:pr.bg,color:pr.color,fontSize:10,fontWeight:700,padding:'2px 8px',borderRadius:20}}>{t.priority}</span></td>
                <td style={{padding:'10px 14px'}}><span style={{background:st.bg,color:st.color,fontSize:10,fontWeight:700,padding:'2px 8px',borderRadius:20}}>{st.label}</span></td>
                <td style={{padding:'10px 14px',fontSize:11,color:'var(--hf-text-muted)',whiteSpace:'nowrap' as const}}>{fmtDate(t.plannedStart)} → {fmtDate(t.plannedEnd)}</td>
                <td style={{padding:'10px 14px',minWidth:80}}>
                  <div style={{height:6,background:'var(--hf-surface-sunken)',borderRadius:3}}>
                    <div style={{height:'100%',width:`${t.progressPct??0}%`,background:'var(--hf-info)',borderRadius:3}}/>
                  </div>
                  <div style={{fontSize:10,color:'var(--hf-text-faint)',marginTop:2}}>{t.progressPct?.toFixed(0)??0}%</div>
                </td>
                <td style={{padding:'10px 14px'}}>
                  <div style={{display:'flex',gap:4}}>
                    {t.status==='NOT_STARTED'&&<TinyBtn onClick={()=>onAction({taskId:t.id,action:'START'})} color="#1D4ED8" bg="#DBEAFE">Start</TinyBtn>}
                    {t.status==='IN_PROGRESS'&&<TinyBtn onClick={()=>onAction({taskId:t.id,action:'COMPLETE'})} color="#166534" bg="#DCFCE7">Done</TinyBtn>}
                    {t.status==='IN_PROGRESS'&&<TinyBtn onClick={()=>onAction({taskId:t.id,action:'BLOCK'})} color="#DC2626" bg="#FEF2F2">Block</TinyBtn>}
                    {t.status==='BLOCKED'&&<TinyBtn onClick={()=>onAction({taskId:t.id,action:'START'})} color="#1D4ED8" bg="#DBEAFE">Resume</TinyBtn>}
                  </div>
                </td>
              </tr>
            )
          })}
        </tbody>
      </table>
    </div>
  )
}

function KanbanView({tasks,onAction}:{tasks:Task[];onAction:(a:any)=>void}) {
  const cols=STATUS_COLS
  return (
    <div style={{display:'grid',gridTemplateColumns:'repeat(4,1fr)',gap:12}}>
      {cols.map(col=>{
        const st=STATUS_LABELS[col]
        const colTasks=tasks.filter(t=>t.status===col)
        return (
          <div key={col} style={{background:'var(--hf-surface-muted)',borderRadius:10,padding:12}}>
            <div style={{display:'flex',justifyContent:'space-between',alignItems:'center',marginBottom:10}}>
              <span style={{fontSize:12,fontWeight:700,color:st.color}}>{st.label}</span>
              <span style={{background:st.bg,color:st.color,borderRadius:20,fontSize:11,fontWeight:700,padding:'2px 8px'}}>{colTasks.length}</span>
            </div>
            <div style={{display:'flex',flexDirection:'column' as const,gap:8}}>
              {colTasks.map(t=>{
                const pr=PRIORITY_COLOR[t.priority]??PRIORITY_COLOR.MEDIUM
                return (
                  <div key={t.id} style={{background:'var(--hf-surface)',border:'1px solid var(--hf-border)',borderRadius:8,padding:'10px 12px'}}>
                    <div style={{display:'flex',justifyContent:'space-between',marginBottom:6}}>
                      <span style={{fontSize:10,color:'var(--hf-text-faint)'}}>{t.taskNumber}</span>
                      <span style={{background:pr.bg,color:pr.color,fontSize:9,fontWeight:700,padding:'1px 6px',borderRadius:20}}>{t.priority}</span>
                    </div>
                    <div style={{fontSize:13,fontWeight:600,color:'var(--hf-text)',marginBottom:6}}>{t.title}</div>
                    {t.assigneeName&&<div style={{fontSize:11,color:'var(--hf-text-muted)',marginBottom:6}}>👤 {t.assigneeName}</div>}
                    <div style={{height:4,background:'var(--hf-surface-sunken)',borderRadius:2,marginBottom:8}}>
                      <div style={{height:'100%',width:`${t.progressPct??0}%`,background:'var(--hf-info)',borderRadius:2}}/>
                    </div>
                    <div style={{display:'flex',gap:4}}>
                      {t.status==='NOT_STARTED'&&<TinyBtn onClick={()=>onAction({taskId:t.id,action:'START'})} color="#1D4ED8" bg="#DBEAFE">Start</TinyBtn>}
                      {t.status==='IN_PROGRESS'&&<TinyBtn onClick={()=>onAction({taskId:t.id,action:'COMPLETE'})} color="#166534" bg="#DCFCE7">Done</TinyBtn>}
                    </div>
                  </div>
                )
              })}
            </div>
          </div>
        )
      })}
    </div>
  )
}

function TinyBtn({onClick,color,bg,children}:any) {
  return <button onClick={onClick} style={{padding:'3px 8px',fontSize:10,fontWeight:700,color,background:bg,border:'none',borderRadius:6,cursor:'pointer'}}>{children}</button>
}
function Fld({label,children,span}:{label:string;children:React.ReactNode;span?:number}) {
  return <div style={span?{gridColumn:`span ${span}`}:undefined}><label style={{display:'block',fontSize:12,fontWeight:600,color:'var(--hf-text-secondary)',marginBottom:5}}>{label}</label>{children}</div>
}

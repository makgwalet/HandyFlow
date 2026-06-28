// src/pages/projects/tabs/ResourcesTab.tsx
import React, { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Plus, Users, Truck, Wrench, Building2 } from 'lucide-react'
import { apiClient } from '../../../api/client'

interface Resource { id:string; resourceType:string; resourceName:string; role:string|null; allocationPct:number; startDate:string|null; endDate:string|null; plannedHours:number|null; actualHours:number; hourlyRate:number|null; dailyRate:number|null }

function unwrap<T>(r:any):T[]{const d=r?.data?.data??r?.data??[];return Array.isArray(d)?d as T[]:d?.content??[]}
const fmtDate=(d:string|null)=>d?new Date(d).toLocaleDateString('en-ZA'):'—'
const fmtR=(n:number|null)=>n!=null?`R${Number(n).toLocaleString('en-ZA',{minimumFractionDigits:0})}`:'—'
const inp:React.CSSProperties={width:'100%',padding:'9px 12px',border:'1.5px solid #E2E8F0',borderRadius:9,fontSize:14,boxSizing:'border-box' as const,outline:'none',background:'#fff'}

const TYPE_ICON:Record<string,React.ElementType>={HUMAN:Users,EQUIPMENT:Wrench,VEHICLE:Truck,SUBCONTRACTOR:Building2}
const TYPE_COLOR:Record<string,{bg:string;color:string}>={
  HUMAN:{bg:'#DBEAFE',color:'#1D4ED8'},EQUIPMENT:{bg:'#FEF3C7',color:'#92400E'},
  VEHICLE:{bg:'#DCFCE7',color:'#166534'},SUBCONTRACTOR:{bg:'#EDE9FE',color:'#7C3AED'},
}

export function ResourcesTab({projectId}:{projectId:string}) {
  const qc=useQueryClient()
  const [showAdd,setShowAdd]=useState(false)
  const [err,setErr]=useState('')
  const initF=()=>({resourceType:'HUMAN',resourceName:'',role:'',allocationPct:'100',startDate:'',endDate:'',hourlyRate:'',dailyRate:'',plannedHours:''})
  const [form,setForm]=useState(initF())
  const sf=(k:string,v:string)=>setForm(p=>({...p,[k]:v}))

  const {data:resources=[],isLoading}=useQuery<Resource[]>({
    queryKey:['pm-resources',projectId],
    queryFn:async()=>{const r=await apiClient.get(`/api/v1/projects/${projectId}/resources`);return unwrap<Resource>(r)},
    staleTime:30_000,
  })

  const addMut=useMutation({
    mutationFn:(body:any)=>apiClient.post(`/api/v1/projects/${projectId}/resources`,body),
    onSuccess:()=>{qc.invalidateQueries({queryKey:['pm-resources',projectId]});setShowAdd(false);setForm(initF());setErr('')},
    onError:(e:any)=>setErr(e.response?.data?.message||'Failed to assign resource'),
  })
  const removeMut=useMutation({
    mutationFn:(id:string)=>apiClient.delete(`/api/v1/projects/resources/${id}`),
    onSuccess:()=>qc.invalidateQueries({queryKey:['pm-resources',projectId]}),
  })

  const grouped=resources.reduce((acc,r)=>{if(!acc[r.resourceType])acc[r.resourceType]=[];acc[r.resourceType].push(r);return acc},{} as Record<string,Resource[]>)
  const totalPlanned=resources.reduce((s,r)=>s+(r.plannedHours??0),0)
  const totalActual=resources.reduce((s,r)=>s+r.actualHours,0)

  return (
    <div>
      <div style={{display:'flex',justifyContent:'space-between',alignItems:'center',marginBottom:16}}>
        <div style={{fontSize:13,color:'#64748B'}}>
          {resources.length} resource{resources.length!==1?'s':''} · {totalActual.toFixed(1)} hrs logged / {totalPlanned.toFixed(0)} planned
        </div>
        <button onClick={()=>{setShowAdd(true);setErr('')}} style={{display:'flex',alignItems:'center',gap:5,padding:'8px 14px',background:'#1B3A6B',color:'#fff',border:'none',borderRadius:9,fontSize:13,fontWeight:600,cursor:'pointer'}}>
          <Plus size={14}/> Assign Resource
        </button>
      </div>

      {isLoading?<div style={{padding:40,textAlign:'center',color:'#94A3B8'}}>Loading…</div>
        :resources.length===0?<Empty text="No resources assigned"/>
        :Object.entries(grouped).map(([type,items])=>{
          const tc=TYPE_COLOR[type]??{bg:'#F1F5F9',color:'#475569'}
          const Icon=TYPE_ICON[type]??Users
          return (
            <div key={type} style={{marginBottom:20}}>
              <div style={{display:'flex',alignItems:'center',gap:8,marginBottom:8}}>
                <div style={{width:26,height:26,borderRadius:6,background:tc.bg,display:'flex',alignItems:'center',justifyContent:'center'}}>
                  <Icon size={13} color={tc.color}/>
                </div>
                <span style={{fontSize:12,fontWeight:700,color:tc.color,textTransform:'uppercase' as const,letterSpacing:'0.05em'}}>{type}</span>
                <span style={{fontSize:11,color:'#94A3B8'}}>({items.length})</span>
              </div>
              <div style={{border:'1px solid #E2E8F0',borderRadius:10,overflow:'hidden'}}>
                <table style={{width:'100%',borderCollapse:'collapse'}}>
                  <thead><tr style={{background:'#F8FAFC'}}>
                    {['Name','Role','Allocation','Dates','Rate','Planned h','Actual h',''].map(h=>(
                      <th key={h} style={{padding:'9px 12px',textAlign:'left' as const,fontSize:10,fontWeight:700,color:'#94A3B8',textTransform:'uppercase' as const,letterSpacing:'0.04em'}}>{h}</th>
                    ))}
                  </tr></thead>
                  <tbody>
                    {items.map((r,i)=>(
                      <tr key={r.id} style={{borderTop:'1px solid #F1F5F9',background:i%2===0?'#fff':'#FAFAFA'}}>
                        <td style={{padding:'10px 12px',fontSize:13,fontWeight:600,color:'#0F172A'}}>{r.resourceName}</td>
                        <td style={{padding:'10px 12px',fontSize:12,color:'#64748B'}}>{r.role??'—'}</td>
                        <td style={{padding:'10px 12px'}}>
                          <div style={{display:'flex',alignItems:'center',gap:6}}>
                            <div style={{width:50,height:5,background:'#F1F5F9',borderRadius:3}}>
                              <div style={{height:'100%',width:`${Math.min(r.allocationPct,100)}%`,background:r.allocationPct>100?'#EF4444':'#3B82F6',borderRadius:3}}/>
                            </div>
                            <span style={{fontSize:11,color:'#64748B'}}>{r.allocationPct}%</span>
                          </div>
                        </td>
                        <td style={{padding:'10px 12px',fontSize:11,color:'#64748B',whiteSpace:'nowrap' as const}}>{fmtDate(r.startDate)} – {fmtDate(r.endDate)}</td>
                        <td style={{padding:'10px 12px',fontSize:12,color:'#64748B'}}>{r.hourlyRate?`${fmtR(r.hourlyRate)}/hr`:r.dailyRate?`${fmtR(r.dailyRate)}/day`:'—'}</td>
                        <td style={{padding:'10px 12px',fontSize:12,color:'#64748B'}}>{r.plannedHours?.toFixed(1)??'—'}</td>
                        <td style={{padding:'10px 12px',fontSize:12,fontWeight:600,color:r.actualHours>(r.plannedHours??Infinity)?'#DC2626':'#0F172A'}}>{r.actualHours.toFixed(1)}</td>
                        <td style={{padding:'10px 12px'}}>
                          <button onClick={()=>removeMut.mutate(r.id)} style={{fontSize:11,color:'#DC2626',background:'none',border:'none',cursor:'pointer'}}>Remove</button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )
        })
      }

      {showAdd&&(
        <Modal title="Assign Resource" onClose={()=>setShowAdd(false)}>
          <div style={{display:'grid',gridTemplateColumns:'1fr 1fr',gap:12}}>
            <Fld label="Resource Type">
              <select value={form.resourceType} onChange={e=>sf('resourceType',e.target.value)} style={inp}>
                {['HUMAN','EQUIPMENT','VEHICLE','SUBCONTRACTOR'].map(t=><option key={t} value={t}>{t}</option>)}
              </select>
            </Fld>
            <Fld label="Name *"><input value={form.resourceName} onChange={e=>sf('resourceName',e.target.value)} placeholder="Employee/equipment name" style={inp}/></Fld>
            <Fld label="Role"><input value={form.role} onChange={e=>sf('role',e.target.value)} placeholder="Site foreman" style={inp}/></Fld>
            <Fld label="Allocation %"><input type="number" value={form.allocationPct} onChange={e=>sf('allocationPct',e.target.value)} placeholder="100" style={inp}/></Fld>
            <Fld label="Start Date"><input type="date" value={form.startDate} onChange={e=>sf('startDate',e.target.value)} style={inp}/></Fld>
            <Fld label="End Date"><input type="date" value={form.endDate} onChange={e=>sf('endDate',e.target.value)} style={inp}/></Fld>
            <Fld label="Hourly Rate (R)"><input type="number" value={form.hourlyRate} onChange={e=>sf('hourlyRate',e.target.value)} placeholder="0.00" style={inp}/></Fld>
            <Fld label="Planned Hours"><input type="number" value={form.plannedHours} onChange={e=>sf('plannedHours',e.target.value)} placeholder="0" style={inp}/></Fld>
          </div>
          {err&&<Err msg={err}/>}
          <MF onCancel={()=>setShowAdd(false)} onConfirm={()=>{
            if(!form.resourceName.trim()){setErr('Name is required');return}
            addMut.mutate({resourceType:form.resourceType,resourceName:form.resourceName.trim(),role:form.role||null,allocationPct:parseFloat(form.allocationPct)||100,startDate:form.startDate||null,endDate:form.endDate||null,hourlyRate:form.hourlyRate?parseFloat(form.hourlyRate):null,plannedHours:form.plannedHours?parseFloat(form.plannedHours):null})
          }} label={addMut.isPending?'Saving…':'Assign'} loading={addMut.isPending}/>
        </Modal>
      )}
    </div>
  )
}

function Empty({text}:{text:string}){return<div style={{textAlign:'center',padding:'50px 20px',color:'#94A3B8'}}><Users size={36} style={{marginBottom:10,opacity:.3}}/><div style={{fontWeight:600,color:'#475569'}}>{text}</div></div>}
function Fld({label,children}:{label:string;children:React.ReactNode}){return<div><label style={{display:'block',fontSize:12,fontWeight:600,color:'#374151',marginBottom:5}}>{label}</label>{children}</div>}
function Err({msg}:{msg:string}){return<div style={{marginTop:10,padding:'8px 12px',background:'#FEF2F2',border:'1px solid #FECACA',borderRadius:8,color:'#DC2626',fontSize:13}}>{msg}</div>}
function Modal({title,children,onClose}:{title:string;children:React.ReactNode;onClose:()=>void}){return<div style={{position:'fixed',inset:0,background:'rgba(15,23,42,0.5)',display:'flex',alignItems:'center',justifyContent:'center',zIndex:1000}}><div style={{background:'#fff',borderRadius:14,padding:28,width:560,maxHeight:'90vh',overflowY:'auto',boxShadow:'0 20px 60px rgba(0,0,0,0.2)'}}><div style={{display:'flex',justifyContent:'space-between',alignItems:'center',marginBottom:20}}><h3 style={{margin:0,fontSize:16,fontWeight:700}}>{title}</h3><button onClick={onClose} style={{background:'none',border:'none',cursor:'pointer',color:'#94A3B8',fontSize:20}}>×</button></div>{children}</div></div>}
function MF({onCancel,onConfirm,label,loading}:{onCancel:()=>void;onConfirm:()=>void;label:string;loading?:boolean}){return<div style={{display:'flex',justifyContent:'flex-end',gap:10,marginTop:20}}><button onClick={onCancel} style={{padding:'9px 16px',border:'1px solid #E2E8F0',borderRadius:9,background:'#fff',fontSize:13,cursor:'pointer'}}>Cancel</button><button onClick={onConfirm} disabled={loading} style={{padding:'9px 16px',background:'#1B3A6B',color:'#fff',border:'none',borderRadius:9,fontSize:13,fontWeight:600,cursor:'pointer',opacity:loading?.6:1}}>{label}</button></div>}

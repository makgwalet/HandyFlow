// src/pages/projects/tabs/BudgetTab.tsx
import React, { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Plus, TrendingUp } from 'lucide-react'
import { apiClient } from '../../../api/client'
import type { Project } from '../ProjectDetailTab'

interface BudgetLine { id:string; category:string; description:string; budgetedAmount:number; committedAmount:number; actualAmount:number; variance:number; isProvisional:boolean; isPrimeCost:boolean }
interface EVM { totalBudget:number; totalActual:number; totalCommitted:number; totalVariance:number; completionPct:number; plannedValue:number; earnedValue:number; actualCost:number; spi:number; cpi:number; eac:number; etc:number }

function unwrap<T>(r:any):T[]{const d=r?.data?.data??r?.data??[];return Array.isArray(d)?d as T[]:d?.content??[]}
const fmtR=(n:number)=>`R ${Number(n??0).toLocaleString('en-ZA',{minimumFractionDigits:0,maximumFractionDigits:0})}`
const inp:React.CSSProperties={width:'100%',padding:'9px 12px',border:'1.5px solid #E2E8F0',borderRadius:9,fontSize:14,boxSizing:'border-box' as const,outline:'none',background:'#fff'}
const CATS=['LABOUR','MATERIALS','SUBCONTRACT','EQUIPMENT','OVERHEAD','CONTINGENCY']
const CAT_COLOR:Record<string,{bg:string;color:string}>={
  LABOUR:{bg:'#DBEAFE',color:'#1D4ED8'},MATERIALS:{bg:'#FEF3C7',color:'#92400E'},
  SUBCONTRACT:{bg:'#EDE9FE',color:'#7C3AED'},EQUIPMENT:{bg:'#DCFCE7',color:'#166534'},
  OVERHEAD:{bg:'#F1F5F9',color:'#475569'},CONTINGENCY:{bg:'#FEF2F2',color:'#DC2626'},
}

export function BudgetTab({projectId,project}:{projectId:string;project:Project}) {
  const qc=useQueryClient()
  const [showAdd,setShowAdd]=useState(false)
  const [err,setErr]=useState('')
  const taskPct=project.taskCount>0?Math.round((project.completedTaskCount/project.taskCount)*100):0
  const initF=()=>({category:'LABOUR',description:'',budgetedAmount:'',isProvisional:false,isPrimeCost:false})
  const [form,setForm]=useState(initF())
  const sf=(k:string,v:any)=>setForm(p=>({...p,[k]:v}))

  const {data:lines=[],isLoading}=useQuery<BudgetLine[]>({
    queryKey:['pm-budget',projectId],
    queryFn:async()=>{const r=await apiClient.get(`/api/v1/projects/${projectId}/budget`);return unwrap<BudgetLine>(r)},
    staleTime:30_000,
  })

  // planPct omitted — server auto-computes from project dates (H-8 fix)
  const {data:evm}=useQuery<EVM>({
    queryKey:['pm-evm',projectId,taskPct],
    queryFn:async()=>{const r=await apiClient.get(`/api/v1/projects/${projectId}/budget/evm?earnedPct=${taskPct}`);return r.data?.data??r.data},
    staleTime:60_000,
  })

  const addMut=useMutation({
    mutationFn:(body:any)=>apiClient.post(`/api/v1/projects/${projectId}/budget`,body),
    onSuccess:()=>{qc.invalidateQueries({queryKey:['pm-budget',projectId]});setShowAdd(false);setForm(initF());setErr('')},
    onError:(e:any)=>setErr(e.response?.data?.message||'Failed to add budget line'),
  })
  const delMut=useMutation({
    mutationFn:(id:string)=>apiClient.delete(`/api/v1/projects/budget/${id}`),
    onSuccess:()=>qc.invalidateQueries({queryKey:['pm-budget',projectId]}),
  })

  const totalBudget=lines.reduce((s,l)=>s+l.budgetedAmount,0)
  const totalActual=lines.reduce((s,l)=>s+l.actualAmount,0)
  const totalCommitted=lines.reduce((s,l)=>s+l.committedAmount,0)
  const totalVariance=lines.reduce((s,l)=>s+l.variance,0)
  const spentPct=totalBudget>0?(totalActual/totalBudget)*100:0

  return (
    <div>
      {/* EVM summary */}
      {evm && (
        <div style={{display:'grid',gridTemplateColumns:'repeat(6,1fr)',gap:10,marginBottom:20,padding:'16px 20px',background:'#F8FAFC',borderRadius:12,border:'1px solid #E2E8F0'}}>
          <EVMStat label="Total Budget" value={fmtR(evm.totalBudget)} color="#0F172A"/>
          <EVMStat label="Actual Cost" value={fmtR(evm.totalActual)} color={evm.totalActual>evm.totalBudget?'#DC2626':'#059669'}/>
          <EVMStat label="Committed" value={fmtR(evm.totalCommitted)} color="#D97706"/>
          <EVMStat label="SPI" value={evm.spi?.toFixed(2)??'—'} color={(evm.spi??1)>=1?'#059669':'#DC2626'} sub="Schedule"/>
          <EVMStat label="CPI" value={evm.cpi?.toFixed(2)??'—'} color={(evm.cpi??1)>=1?'#059669':'#DC2626'} sub="Cost"/>
          <EVMStat label="EAC" value={fmtR(evm.eac)} color="#0F172A" sub="Est at completion"/>
        </div>
      )}

      <div style={{display:'flex',justifyContent:'space-between',alignItems:'center',marginBottom:12}}>
        <div style={{fontSize:13,color:'#64748B'}}>{lines.length} budget line{lines.length!==1?'s':''}</div>
        <button onClick={()=>{setShowAdd(true);setErr('')}} style={{display:'flex',alignItems:'center',gap:5,padding:'8px 14px',background:'#1B3A6B',color:'#fff',border:'none',borderRadius:9,fontSize:13,fontWeight:600,cursor:'pointer'}}>
          <Plus size={14}/> Add Budget Line
        </button>
      </div>

      {isLoading?<div style={{padding:40,textAlign:'center',color:'#94A3B8'}}>Loading…</div>
        :lines.length===0?<div style={{textAlign:'center',padding:'50px 20px',color:'#94A3B8'}}><TrendingUp size={36} style={{marginBottom:10,opacity:.3}}/><div style={{fontWeight:600,color:'#475569'}}>No budget lines</div><div style={{fontSize:13}}>Add line items to track costs by category</div></div>
        :(
        <div style={{border:'1px solid #E2E8F0',borderRadius:12,overflow:'hidden'}}>
          <table style={{width:'100%',borderCollapse:'collapse'}}>
            <thead><tr style={{background:'#F8FAFC'}}>
              {['Category','Description','Budget','Committed','Actual','Variance','Flags',''].map(h=>(
                <th key={h} style={{padding:'10px 14px',textAlign:'left' as const,fontSize:11,fontWeight:700,color:'#94A3B8',textTransform:'uppercase' as const,letterSpacing:'0.04em'}}>{h}</th>
              ))}
            </tr></thead>
            <tbody>
              {lines.map((l,i)=>{
                const cc=CAT_COLOR[l.category]??{bg:'#F1F5F9',color:'#475569'}
                const overBudget=l.variance<0
                return (
                  <tr key={l.id} style={{borderTop:'1px solid #F1F5F9',background:i%2===0?'#fff':'#FAFAFA'}}>
                    <td style={{padding:'10px 14px'}}><span style={{background:cc.bg,color:cc.color,fontSize:10,fontWeight:700,padding:'2px 8px',borderRadius:20}}>{l.category}</span></td>
                    <td style={{padding:'10px 14px',fontSize:13,color:'#374151'}}>{l.description}</td>
                    <td style={{padding:'10px 14px',fontSize:13,fontWeight:600,color:'#0F172A'}}>{fmtR(l.budgetedAmount)}</td>
                    <td style={{padding:'10px 14px',fontSize:13,color:'#D97706'}}>{fmtR(l.committedAmount)}</td>
                    <td style={{padding:'10px 14px',fontSize:13,color:overBudget?'#DC2626':'#374151'}}>{fmtR(l.actualAmount)}</td>
                    <td style={{padding:'10px 14px',fontSize:13,fontWeight:700,color:overBudget?'#DC2626':'#059669'}}>{overBudget?'':'+' }{fmtR(l.variance)}</td>
                    <td style={{padding:'10px 14px',fontSize:11,color:'#64748B'}}>
                      {l.isProvisional&&<span style={{background:'#FEF3C7',color:'#92400E',padding:'1px 6px',borderRadius:20,fontSize:10,fontWeight:700,marginRight:4}}>PS</span>}
                      {l.isPrimeCost&&<span style={{background:'#EDE9FE',color:'#7C3AED',padding:'1px 6px',borderRadius:20,fontSize:10,fontWeight:700}}>PC</span>}
                    </td>
                    <td style={{padding:'10px 14px'}}><button onClick={()=>delMut.mutate(l.id)} style={{fontSize:11,color:'#DC2626',background:'none',border:'none',cursor:'pointer'}}>×</button></td>
                  </tr>
                )
              })}
              {/* Totals row */}
              <tr style={{borderTop:'2px solid #E2E8F0',background:'#F8FAFC'}}>
                <td colSpan={2} style={{padding:'10px 14px',fontSize:13,fontWeight:700,color:'#0F172A'}}>TOTAL</td>
                <td style={{padding:'10px 14px',fontSize:13,fontWeight:700,color:'#0F172A'}}>{fmtR(totalBudget)}</td>
                <td style={{padding:'10px 14px',fontSize:13,fontWeight:700,color:'#D97706'}}>{fmtR(totalCommitted)}</td>
                <td style={{padding:'10px 14px',fontSize:13,fontWeight:700,color:totalActual>totalBudget?'#DC2626':'#374151'}}>{fmtR(totalActual)}</td>
                <td style={{padding:'10px 14px',fontSize:13,fontWeight:700,color:totalVariance<0?'#DC2626':'#059669'}}>{totalVariance<0?'':'+' }{fmtR(totalVariance)}</td>
                <td colSpan={2}/>
              </tr>
            </tbody>
          </table>
          {/* Spend bar */}
          <div style={{padding:'12px 16px',borderTop:'1px solid #E2E8F0'}}>
            <div style={{display:'flex',justifyContent:'space-between',fontSize:11,color:'#94A3B8',marginBottom:4}}>
              <span>Budget utilisation</span><span>{spentPct.toFixed(1)}%</span>
            </div>
            <div style={{height:8,background:'#F1F5F9',borderRadius:4}}>
              <div style={{height:'100%',width:`${Math.min(spentPct,100)}%`,background:spentPct>100?'#EF4444':spentPct>85?'#F59E0B':'#22C55E',borderRadius:4}}/>
            </div>
          </div>
        </div>
      )}

      {showAdd&&(
        <div style={{position:'fixed',inset:0,background:'rgba(15,23,42,0.5)',display:'flex',alignItems:'center',justifyContent:'center',zIndex:1000}}>
          <div style={{background:'#fff',borderRadius:14,padding:28,width:500,boxShadow:'0 20px 60px rgba(0,0,0,0.2)'}}>
            <div style={{display:'flex',justifyContent:'space-between',alignItems:'center',marginBottom:20}}>
              <h3 style={{margin:0,fontSize:16,fontWeight:700}}>Add Budget Line</h3>
              <button onClick={()=>setShowAdd(false)} style={{background:'none',border:'none',cursor:'pointer',color:'#94A3B8',fontSize:20}}>×</button>
            </div>
            <div style={{display:'flex',flexDirection:'column' as const,gap:12}}>
              <div><label style={{display:'block',fontSize:12,fontWeight:600,color:'#374151',marginBottom:5}}>Category</label>
                <select value={form.category} onChange={e=>sf('category',e.target.value)} style={inp}>
                  {CATS.map(c=><option key={c} value={c}>{c}</option>)}
                </select>
              </div>
              <div><label style={{display:'block',fontSize:12,fontWeight:600,color:'#374151',marginBottom:5}}>Description *</label><input value={form.description} onChange={e=>sf('description',e.target.value)} placeholder="Labour for Phase 1 excavation" style={inp}/></div>
              <div><label style={{display:'block',fontSize:12,fontWeight:600,color:'#374151',marginBottom:5}}>Budgeted Amount (R) *</label><input type="number" value={form.budgetedAmount} onChange={e=>sf('budgetedAmount',e.target.value)} placeholder="0.00" style={inp}/></div>
              <div style={{display:'flex',gap:16}}>
                <label style={{display:'flex',alignItems:'center',gap:6,fontSize:13,color:'#374151',cursor:'pointer'}}><input type="checkbox" checked={form.isProvisional} onChange={e=>sf('isProvisional',e.target.checked)}/> Provisional Sum (PS)</label>
                <label style={{display:'flex',alignItems:'center',gap:6,fontSize:13,color:'#374151',cursor:'pointer'}}><input type="checkbox" checked={form.isPrimeCost} onChange={e=>sf('isPrimeCost',e.target.checked)}/> Prime Cost (PC)</label>
              </div>
            </div>
            {err&&<div style={{marginTop:10,padding:'8px 12px',background:'#FEF2F2',border:'1px solid #FECACA',borderRadius:8,color:'#DC2626',fontSize:13}}>{err}</div>}
            <div style={{display:'flex',justifyContent:'flex-end',gap:10,marginTop:20}}>
              <button onClick={()=>setShowAdd(false)} style={{padding:'9px 16px',border:'1px solid #E2E8F0',borderRadius:9,background:'#fff',fontSize:13,cursor:'pointer'}}>Cancel</button>
              <button onClick={()=>{if(!form.description.trim()||!form.budgetedAmount){setErr('Description and amount required');return}addMut.mutate({category:form.category,description:form.description.trim(),budgetedAmount:parseFloat(form.budgetedAmount),isProvisional:form.isProvisional,isPrimeCost:form.isPrimeCost})}} disabled={addMut.isPending} style={{padding:'9px 16px',background:'#1B3A6B',color:'#fff',border:'none',borderRadius:9,fontSize:13,fontWeight:600,cursor:'pointer',opacity:addMut.isPending?.6:1}}>{addMut.isPending?'Saving…':'Add Line'}</button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

function EVMStat({label,value,color,sub}:{label:string;value:string;color:string;sub?:string}){
  return <div style={{textAlign:'center' as const}}>
    <div style={{fontSize:10,color:'#94A3B8',fontWeight:600,textTransform:'uppercase' as const,letterSpacing:'0.04em',marginBottom:4}}>{label}</div>
    <div style={{fontSize:16,fontWeight:800,color}}>{value}</div>
    {sub&&<div style={{fontSize:10,color:'#CBD5E1',marginTop:2}}>{sub}</div>}
  </div>
}

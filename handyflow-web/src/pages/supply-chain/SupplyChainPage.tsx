// src/pages/supply-chain/SupplyChainPage.tsx
// Full implementation: Suppliers, POs with line items, GR workflow,
// Inventory with catalogue search, Supplier Invoices with 3-way match
import React, { useState, useEffect, useCallback } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  Truck, Plus, Search, X, Package, ShoppingCart, CheckCircle,
  AlertTriangle, FileText, Building2, Phone, Mail, Clock,
  ChevronDown, ChevronRight, Trash2, Edit2, Eye, MoreVertical,
  ArrowRight, TrendingUp, RefreshCw, AlertCircle, Hash,
} from 'lucide-react'
import { apiClient } from '../../api/client'

// ── Response unwrap ───────────────────────────────────────────────────────────
function unwrapList<T>(res: any): T[] {
  const d = res?.data?.data ?? res?.data ?? []
  if (Array.isArray(d)) return d as T[]
  if (d && Array.isArray(d.content)) return d.content as T[]
  return []
}

// ── Types ─────────────────────────────────────────────────────────────────────
interface Summary { totalSuppliers:number; openPurchaseOrders:number; pendingInvoices:number; invoicesForApproval:number; lowStockItems:number; overdueInvoices:number }
interface Supplier { id:string; name:string; contactName:string|null; contactEmail:string|null; contactPhone:string|null; bbbeeLevel:number|null; paymentTermsDays:number; status:string; onTimeRate:number|null; totalOrders:number; city:string|null; province:string|null; vatNumber:string|null; registrationNumber:string|null; bankName:string|null; bankAccount:string|null; notes:string|null }
interface PurchaseOrder { id:string; orderNumber:string; supplierName:string; supplierId:string; status:string; totalAmount:number; currency:string; requiredByDate:string|null; projectRef:string|null; deliverToLocation:string|null; notes:string|null; lines?:PoLine[] }
interface PoLine { id:string; purchaseOrderId:string; itemName:string; supplierSku:string|null; qtyOrdered:number; unitCost:number; lineTotal:number; lineTotalIncl:number; vatAmount:number; vatRate:number; catalogueItemId:string|null; isFullyReceived:boolean }
interface InventoryItem { id:string; catalogueItemId:string; qtyOnHand:number; reorderPoint:number; reorderQty:number; avgCost:number; binLocation:string|null; lowStock:boolean }
interface StockMovement { id:string; movementType:string; qty:number; costPerUnit:number; reference:string|null; movedAt:string; movedByName:string|null }
interface StockLocation { id:string; name:string; locationType:string; isDefault:boolean }
interface SupplierInvoice { id:string; invoiceNumber:string|null; supplierInvoiceRef:string|null; supplierId:string; totalAmount:number; dueDate:string; status:string; matchStatus:string; overdue:boolean }
interface GoodsReceipt { id:string; receiptNumber:string; purchaseOrderId:string; status:string; deliveryNoteRef:string|null; receivedByName:string|null; createdAt:string }
interface CatalogueItem { id:string; name:string; code:string|null; description:string|null; unitPrice:number|null; unit:string|null }

// ── Helpers ───────────────────────────────────────────────────────────────────
const fmtR = (n:number) => `R ${Number(n??0).toLocaleString('en-ZA',{minimumFractionDigits:2,maximumFractionDigits:2})}`
const fmtDate = (d:string|null) => d ? new Date(d).toLocaleDateString('en-ZA') : '—'

const SCOL: Record<string,{bg:string;color:string}> = {
  DRAFT:{bg:'#F1F5F9',color:'#475569'}, PENDING_APPROVAL:{bg:'#FEF3C7',color:'#92400E'},
  APPROVED:{bg:'#DCFCE7',color:'#166534'}, SENT:{bg:'#DBEAFE',color:'#1D4ED8'},
  PARTIALLY_RECEIVED:{bg:'#EDE9FE',color:'#7C3AED'}, FULLY_RECEIVED:{bg:'#DCFCE7',color:'#166534'},
  INVOICED:{bg:'#F3E8FF',color:'#7C3AED'}, CANCELLED:{bg:'#FEF2F2',color:'#DC2626'},
  RECEIVED:{bg:'#F1F5F9',color:'#475569'}, UNDER_REVIEW:{bg:'#FEF3C7',color:'#92400E'},
  PAID:{bg:'#DCFCE7',color:'#166534'}, DISPUTED:{bg:'#FEF2F2',color:'#DC2626'},
  ACTIVE:{bg:'#DCFCE7',color:'#166534'}, INACTIVE:{bg:'#F1F5F9',color:'#475569'},
  BLACKLISTED:{bg:'#FEF2F2',color:'#DC2626'}, MATCHED:{bg:'#DCFCE7',color:'#166534'},
  PO_MATCHED:{bg:'#DBEAFE',color:'#1D4ED8'}, PARTIAL_MATCH:{bg:'#FEF3C7',color:'#92400E'},
  NO_PO:{bg:'#FEF2F2',color:'#DC2626'}, POSTED:{bg:'#DCFCE7',color:'#166534'},
}

const Badge = ({status}:{status:string}) => {
  const s = SCOL[status]??{bg:'#F1F5F9',color:'#475569'}
  return <span style={{background:s.bg,color:s.color,fontSize:11,fontWeight:700,padding:'3px 10px',borderRadius:20,whiteSpace:'nowrap' as const}}>{status.replace(/_/g,' ')}</span>
}

const inp:React.CSSProperties = {width:'100%',padding:'9px 12px',border:'1.5px solid #E2E8F0',borderRadius:9,fontSize:14,boxSizing:'border-box' as const,outline:'none',background:'#fff'}
const th:React.CSSProperties  = {padding:'11px 16px',textAlign:'left' as const,fontSize:11,fontWeight:700,color:'#94A3B8',textTransform:'uppercase' as const,letterSpacing:'0.05em'}
const td:React.CSSProperties  = {padding:'12px 16px',fontSize:13,color:'#374151',verticalAlign:'middle' as const}

const Btn = ({children,onClick,disabled,variant='primary',size='md',icon:Icon}:{children:React.ReactNode;onClick?:()=>void;disabled?:boolean;variant?:'primary'|'secondary'|'danger'|'ghost';size?:'sm'|'md';icon?:React.ElementType}) => {
  const styles:{[k:string]:React.CSSProperties} = {
    primary:{background:'#1B3A6B',color:'#fff',border:'none'},
    secondary:{background:'#fff',color:'#374151',border:'1px solid #E2E8F0'},
    danger:{background:'#FEF2F2',color:'#DC2626',border:'1px solid #FECACA'},
    ghost:{background:'none',color:'#64748B',border:'none'},
  }
  const sz = size==='sm' ? {padding:'5px 10px',fontSize:12} : {padding:'9px 16px',fontSize:13}
  return (
    <button onClick={onClick} disabled={disabled}
      style={{...sz,...styles[variant],borderRadius:9,fontWeight:600,cursor:disabled?'not-allowed':'pointer',opacity:disabled?.6:1,display:'flex',alignItems:'center',gap:6}}>
      {Icon && <Icon size={size==='sm'?12:15}/>}{children}
    </button>
  )
}

const filterBtn = (active:boolean):React.CSSProperties => ({
  padding:'6px 14px',borderRadius:20,border:active?'1.5px solid #1B3A6B':'1px solid #E2E8F0',
  background:active?'#EFF6FF':'#fff',color:active?'#1B3A6B':'#64748B',fontSize:12,fontWeight:active?700:400,cursor:'pointer',
})

type Tab = 'overview'|'suppliers'|'purchase-orders'|'inventory'|'invoices'

// ── Main ──────────────────────────────────────────────────────────────────────
export function SupplyChainPage() {
  const [tab, setTab] = useState<Tab>('overview')
  return (
    <div style={{fontFamily:"'Inter',system-ui,sans-serif"}}>
      <div style={{marginBottom:24}}>
        <div style={{display:'flex',alignItems:'center',gap:10,marginBottom:4}}>
          <div style={{width:36,height:36,borderRadius:10,background:'#FEF3C7',display:'flex',alignItems:'center',justifyContent:'center'}}>
            <Truck size={20} color="#D97706"/>
          </div>
          <h1 style={{fontSize:22,fontWeight:800,color:'#0F172A',margin:0}}>Supply Chain</h1>
        </div>
        <p style={{fontSize:13,color:'#94A3B8',margin:0,paddingLeft:46}}>Suppliers · Purchase orders · Inventory · AP invoices</p>
      </div>
      <div style={{display:'flex',gap:4,marginBottom:24,borderBottom:'1px solid #E2E8F0'}}>
        {([
          {key:'overview',label:'Overview',icon:Truck},
          {key:'suppliers',label:'Suppliers',icon:Building2},
          {key:'purchase-orders',label:'Purchase Orders',icon:ShoppingCart},
          {key:'inventory',label:'Inventory',icon:Package},
          {key:'invoices',label:'Supplier Invoices',icon:FileText},
        ] as {key:Tab;label:string;icon:React.ElementType}[]).map(t=>(
          <button key={t.key} onClick={()=>setTab(t.key)} style={{display:'flex',alignItems:'center',gap:6,padding:'9px 16px',border:'none',background:'none',cursor:'pointer',fontSize:13,fontWeight:tab===t.key?700:500,color:tab===t.key?'#1B3A6B':'#64748B',borderBottom:tab===t.key?'2px solid #1B3A6B':'2px solid transparent',marginBottom:-1}}>
            <t.icon size={15}/>{t.label}
          </button>
        ))}
      </div>
      {tab==='overview'        && <OverviewTab onNav={setTab}/>}
      {tab==='suppliers'       && <SuppliersTab/>}
      {tab==='purchase-orders' && <PurchaseOrdersTab/>}
      {tab==='inventory'       && <InventoryTab/>}
      {tab==='invoices'        && <InvoicesTab/>}
    </div>
  )
}

// ── Overview ──────────────────────────────────────────────────────────────────
function OverviewTab({onNav}:{onNav:(t:Tab)=>void}) {
  const {data:summary,isLoading} = useQuery<Summary>({
    queryKey:['scm-summary'],
    queryFn:async()=>{ const r=await apiClient.get('/api/v1/supply-chain/summary'); return r.data?.data??r.data??{} },
    staleTime:30_000,
  })
  if(isLoading) return <Spinner/>
  return (
    <div>
      {/* KPIs */}
      <div style={{display:'grid',gridTemplateColumns:'repeat(4,1fr)',gap:14,marginBottom:24}}>
        {[
          {label:'Active Suppliers',value:summary?.totalSuppliers??0,icon:Building2,bg:'#DBEAFE',color:'#1D4ED8',tab:'suppliers' as Tab},
          {label:'Open Purchase Orders',value:summary?.openPurchaseOrders??0,icon:ShoppingCart,bg:'#FEF3C7',color:'#D97706',tab:'purchase-orders' as Tab},
          {label:'Pending Invoices',value:summary?.pendingInvoices??0,icon:FileText,bg:'#EDE9FE',color:'#7C3AED',tab:'invoices' as Tab},
          {label:'Low Stock Items',value:summary?.lowStockItems??0,icon:AlertTriangle,bg:'#FEF2F2',color:'#DC2626',tab:'inventory' as Tab},
        ].map(s=>(
          <div key={s.label} onClick={()=>onNav(s.tab)}
            style={{background:'#fff',border:'1px solid #E8EDF5',borderRadius:14,padding:'20px',display:'flex',alignItems:'center',justifyContent:'space-between',cursor:'pointer',transition:'box-shadow 0.15s'}}
            onMouseEnter={e=>(e.currentTarget.style.boxShadow='0 4px 16px rgba(0,0,0,0.08)')}
            onMouseLeave={e=>(e.currentTarget.style.boxShadow='none')}>
            <div>
              <p style={{fontSize:11,color:'#94A3B8',margin:'0 0 6px',fontWeight:600,textTransform:'uppercase' as const,letterSpacing:'0.05em'}}>{s.label}</p>
              <p style={{fontSize:28,fontWeight:800,color:'#0F172A',margin:0}}>{s.value}</p>
            </div>
            <div style={{width:44,height:44,borderRadius:12,background:s.bg,display:'flex',alignItems:'center',justifyContent:'center'}}>
              <s.icon size={20} color={s.color}/>
            </div>
          </div>
        ))}
      </div>
      {/* Alerts */}
      {!!summary?.overdueInvoices && <InfoBanner variant="error" icon={AlertTriangle}>{summary.overdueInvoices} overdue supplier invoice{summary.overdueInvoices!==1?'s':''} — action required</InfoBanner>}
      {!!summary?.invoicesForApproval && <InfoBanner variant="warning" icon={Clock}>{summary.invoicesForApproval} invoice{summary.invoicesForApproval!==1?'s':''} awaiting approval</InfoBanner>}
      {!!summary?.lowStockItems && <InfoBanner variant="info" icon={Package}>{summary.lowStockItems} item{summary.lowStockItems!==1?'s':''} below reorder point — consider raising a purchase order</InfoBanner>}
      {/* Quick actions */}
      <div style={{display:'grid',gridTemplateColumns:'repeat(3,1fr)',gap:12,marginTop:24}}>
        {[
          {label:'Add Supplier',   sub:'Register a new supplier',          icon:Building2,  tab:'suppliers' as Tab,    color:'#1D4ED8',bg:'#DBEAFE'},
          {label:'New Purchase Order', sub:'Create a procurement order',   icon:ShoppingCart,tab:'purchase-orders' as Tab,color:'#D97706',bg:'#FEF3C7'},
          {label:'Record Invoice', sub:'Log a supplier invoice for payment',icon:FileText,   tab:'invoices' as Tab,     color:'#7C3AED',bg:'#EDE9FE'},
        ].map(a=>(
          <div key={a.label} onClick={()=>onNav(a.tab)}
            style={{background:'#fff',border:'1px solid #E2E8F0',borderRadius:12,padding:'18px 20px',cursor:'pointer',display:'flex',alignItems:'center',gap:14}}>
            <div style={{width:40,height:40,borderRadius:10,background:a.bg,display:'flex',alignItems:'center',justifyContent:'center',flexShrink:0}}>
              <a.icon size={18} color={a.color}/>
            </div>
            <div>
              <div style={{fontWeight:700,fontSize:14,color:'#0F172A'}}>{a.label}</div>
              <div style={{fontSize:12,color:'#94A3B8',marginTop:2}}>{a.sub}</div>
            </div>
            <ArrowRight size={16} color="#CBD5E1" style={{marginLeft:'auto',flexShrink:0}}/>
          </div>
        ))}
      </div>
    </div>
  )
}

// ── Suppliers ─────────────────────────────────────────────────────────────────
function SuppliersTab() {
  const qc = useQueryClient()
  const [search,setSearch] = useState('')
  const [showAdd,setShowAdd] = useState(false)
  const [editSupplier,setEditSupplier] = useState<Supplier|null>(null)
  const [err,setErr] = useState('')
  const initF = ()=>({name:'',contactName:'',contactEmail:'',contactPhone:'',bbbeeLevel:'',paymentTermsDays:'30',city:'',province:'',vatNumber:'',registrationNumber:'',bankName:'',bankAccount:'',bankBranchCode:'',notes:''})
  const [form,setForm] = useState(initF())
  const sf = (k:string,v:string)=>setForm(p=>({...p,[k]:v}))

  const {data:suppliers=[],isLoading} = useQuery<Supplier[]>({
    queryKey:['scm-suppliers',search],
    queryFn:async()=>{ const r=await apiClient.get(search?`/api/v1/supply-chain/suppliers?search=${encodeURIComponent(search)}&size=50`:'/api/v1/supply-chain/suppliers?size=50'); return unwrapList<Supplier>(r) },
    staleTime:30_000,
  })

  const saveMut = useMutation({
    mutationFn:(body:any)=> editSupplier ? apiClient.put(`/api/v1/supply-chain/suppliers/${editSupplier.id}`,body) : apiClient.post('/api/v1/supply-chain/suppliers',body),
    onSuccess:()=>{ qc.invalidateQueries({queryKey:['scm-suppliers']}); qc.invalidateQueries({queryKey:['scm-summary']}); setShowAdd(false); setEditSupplier(null); setForm(initF()); setErr('') },
    onError:(e:any)=>setErr(e.response?.data?.message||'Failed to save supplier'),
  })

  const openEdit = (s:Supplier)=>{ setEditSupplier(s); setForm({name:s.name,contactName:s.contactName||'',contactEmail:s.contactEmail||'',contactPhone:s.contactPhone||'',bbbeeLevel:s.bbbeeLevel?String(s.bbbeeLevel):'',paymentTermsDays:String(s.paymentTermsDays),city:s.city||'',province:'',vatNumber:s.vatNumber||'',registrationNumber:s.registrationNumber||'',bankName:s.bankName||'',bankAccount:s.bankAccount||'',bankBranchCode:'',notes:s.notes||''}); setShowAdd(true); setErr('') }

  const doSave = ()=>{
    if(!form.name.trim()){setErr('Supplier name is required');return}
    saveMut.mutate({name:form.name.trim(),contactName:form.contactName||null,contactEmail:form.contactEmail||null,contactPhone:form.contactPhone||null,bbbeeLevel:form.bbbeeLevel?parseInt(form.bbbeeLevel):null,paymentTermsDays:parseInt(form.paymentTermsDays)||30,city:form.city||null,province:form.province||null,vatNumber:form.vatNumber||null,registrationNumber:form.registrationNumber||null,bankName:form.bankName||null,bankAccount:form.bankAccount||null,bankBranchCode:form.bankBranchCode||null,notes:form.notes||null})
  }

  return (
    <div>
      <div style={{display:'flex',justifyContent:'space-between',alignItems:'center',marginBottom:16,gap:10}}>
        <div style={{position:'relative',flex:1,maxWidth:320}}>
          <Search size={14} style={{position:'absolute',left:10,top:'50%',transform:'translateY(-50%)',color:'#94A3B8'}}/>
          <input value={search} onChange={e=>setSearch(e.target.value)} placeholder="Search suppliers…" style={{...inp,paddingLeft:32}}/>
        </div>
        <Btn icon={Plus} onClick={()=>{setShowAdd(true);setEditSupplier(null);setForm(initF());setErr('')}}>Add Supplier</Btn>
      </div>
      {isLoading ? <Spinner/> : suppliers.length===0 ? <Empty icon={Building2} title="No suppliers yet" sub="Add your first supplier to start raising purchase orders"/> : (
        <div style={{display:'grid',gridTemplateColumns:'repeat(auto-fill,minmax(320px,1fr))',gap:12}}>
          {suppliers.map(s=>(
            <div key={s.id} style={{background:'#fff',border:'1px solid #E2E8F0',borderRadius:12,padding:'18px 20px'}}>
              <div style={{display:'flex',alignItems:'flex-start',justifyContent:'space-between',marginBottom:10}}>
                <div style={{flex:1}}>
                  <p style={{fontSize:15,fontWeight:700,color:'#0F172A',margin:'0 0 6px'}}>{s.name}</p>
                  <div style={{display:'flex',gap:6,flexWrap:'wrap' as const}}>
                    <Badge status={s.status}/>
                    {s.bbbeeLevel && <span style={{background:'#ECFDF5',color:'#059669',fontSize:11,fontWeight:700,padding:'3px 10px',borderRadius:20}}>BBBEE L{s.bbbeeLevel}</span>}
                  </div>
                </div>
                <div style={{display:'flex',gap:4,flexShrink:0}}>
                  {s.onTimeRate!=null && (
                    <div style={{textAlign:'right',marginRight:8}}>
                      <div style={{fontSize:16,fontWeight:800,color:s.onTimeRate>=90?'#059669':s.onTimeRate>=70?'#D97706':'#DC2626'}}>{s.onTimeRate.toFixed(0)}%</div>
                      <div style={{fontSize:10,color:'#94A3B8'}}>On-time</div>
                    </div>
                  )}
                  <button onClick={()=>openEdit(s)} style={{background:'none',border:'none',cursor:'pointer',color:'#64748B',padding:4,borderRadius:6}}><Edit2 size={14}/></button>
                </div>
              </div>
              <div style={{display:'flex',flexDirection:'column',gap:4}}>
                {s.contactName  && <IRow icon={Building2} text={s.contactName}/>}
                {s.contactEmail && <IRow icon={Mail}      text={s.contactEmail}/>}
                {s.contactPhone && <IRow icon={Phone}     text={s.contactPhone}/>}
                {(s.city||s.province) && <IRow icon={Building2} text={[s.city,s.province].filter(Boolean).join(', ')}/>}
              </div>
              <div style={{borderTop:'1px solid #F1F5F9',marginTop:12,paddingTop:10,display:'flex',justifyContent:'space-between',fontSize:12,color:'#64748B'}}>
                <span>{s.paymentTermsDays} day terms</span>
                <span>{s.totalOrders} orders</span>
              </div>
            </div>
          ))}
        </div>
      )}
      {showAdd && (
        <Modal title={editSupplier?'Edit Supplier':'Add Supplier'} onClose={()=>{setShowAdd(false);setEditSupplier(null)}}>
          <div style={{display:'grid',gridTemplateColumns:'1fr 1fr',gap:14}}>
            <F label="Supplier Name *" span={2}><input value={form.name} onChange={e=>sf('name',e.target.value)} placeholder="Bosch Tools SA" style={inp} autoFocus/></F>
            <F label="Registration No."><input value={form.registrationNumber} onChange={e=>sf('registrationNumber',e.target.value)} placeholder="2023/123456/07" style={inp}/></F>
            <F label="VAT Number"><input value={form.vatNumber} onChange={e=>sf('vatNumber',e.target.value)} placeholder="4123456789" style={inp}/></F>
            <F label="Contact Person"><input value={form.contactName} onChange={e=>sf('contactName',e.target.value)} placeholder="Sipho Dlamini" style={inp}/></F>
            <F label="Contact Phone"><input value={form.contactPhone} onChange={e=>sf('contactPhone',e.target.value)} placeholder="+27 11 555 1234" style={inp}/></F>
            <F label="Contact Email" span={2}><input value={form.contactEmail} onChange={e=>sf('contactEmail',e.target.value)} placeholder="sipho@supplier.co.za" style={inp}/></F>
            <F label="City"><input value={form.city} onChange={e=>sf('city',e.target.value)} placeholder="Johannesburg" style={inp}/></F>
            <F label="Province">
              <select value={form.province} onChange={e=>sf('province',e.target.value)} style={inp}>
                <option value="">Select…</option>
                {['Gauteng','Western Cape','KwaZulu-Natal','Eastern Cape','Limpopo','Mpumalanga','North West','Free State','Northern Cape'].map(p=><option key={p} value={p}>{p}</option>)}
              </select>
            </F>
            <F label="BBBEE Level">
              <select value={form.bbbeeLevel} onChange={e=>sf('bbbeeLevel',e.target.value)} style={inp}>
                <option value="">Not rated</option>
                {[1,2,3,4,5,6,7,8].map(l=><option key={l} value={l}>Level {l}</option>)}
              </select>
            </F>
            <F label="Payment Terms (days)">
              <select value={form.paymentTermsDays} onChange={e=>sf('paymentTermsDays',e.target.value)} style={inp}>
                {['7','14','30','45','60','90'].map(d=><option key={d} value={d}>{d} days</option>)}
              </select>
            </F>
            <F label="Bank Name"><input value={form.bankName} onChange={e=>sf('bankName',e.target.value)} placeholder="First National Bank" style={inp}/></F>
            <F label="Account Number"><input value={form.bankAccount} onChange={e=>sf('bankAccount',e.target.value)} placeholder="62341098765" style={inp}/></F>
            <F label="Branch Code" span={2}><input value={form.bankBranchCode} onChange={e=>sf('bankBranchCode',e.target.value)} placeholder="250655" style={inp}/></F>
            <F label="Notes" span={2}><textarea value={form.notes} onChange={e=>sf('notes',e.target.value)} placeholder="Any notes about this supplier…" style={{...inp,minHeight:60,resize:'vertical' as const}}/></F>
          </div>
          {err && <ErrBox msg={err}/>}
          <MF onCancel={()=>{setShowAdd(false);setEditSupplier(null)}} onConfirm={doSave} label={saveMut.isPending?'Saving…':editSupplier?'Save Changes':'Add Supplier'} loading={saveMut.isPending}/>
        </Modal>
      )}
    </div>
  )
}

// ── Purchase Orders ───────────────────────────────────────────────────────────
function PurchaseOrdersTab() {
  const qc = useQueryClient()
  const [statusFilter,setStatus] = useState('')
  const [showCreate,setShowCreate] = useState(false)
  const [expanded,setExpanded] = useState<string|null>(null)
  const [showLines,setShowLines] = useState<string|null>(null)   // PO id for add-line modal
  const [showGR,setShowGR] = useState<string|null>(null)         // PO id for goods receipt
  const [err,setErr] = useState('')

  const initPO = ()=>({supplierId:'',deliverToLocation:'',requiredByDate:'',currency:'ZAR',projectRef:'',notes:''})
  const [form,setForm] = useState(initPO())
  const sf=(k:string,v:string)=>setForm(p=>({...p,[k]:v}))

  const {data:suppliers=[]} = useQuery<Supplier[]>({
    queryKey:['scm-suppliers-list'],
    queryFn:async()=>{ const r=await apiClient.get('/api/v1/supply-chain/suppliers?size=200&status=ACTIVE'); return unwrapList<Supplier>(r) },
    staleTime:60_000,
  })
  const {data:locations=[]} = useQuery<StockLocation[]>({
    queryKey:['scm-locations'],
    queryFn:async()=>{ const r=await apiClient.get('/api/v1/supply-chain/locations'); return unwrapList<StockLocation>(r) },
    staleTime:60_000,
  })
  const {data:orders=[],isLoading} = useQuery<PurchaseOrder[]>({
    queryKey:['scm-pos',statusFilter],
    queryFn:async()=>{ const url=statusFilter?`/api/v1/supply-chain/purchase-orders?status=${statusFilter}&size=50`:'/api/v1/supply-chain/purchase-orders?size=50'; const r=await apiClient.get(url); return unwrapList<PurchaseOrder>(r) },
    staleTime:30_000,
  })

  // Fetch PO lines separately — ScPurchaseOrder is a flat entity, lines in sc_po_lines
  const {data:poLines=[]} = useQuery<PoLine[]>({
    queryKey:['scm-po-lines',expanded],
    queryFn:async()=>{ const r=await apiClient.get(`/api/v1/supply-chain/purchase-orders/${expanded}/lines`); return unwrapList<PoLine>(r) },
    enabled:!!expanded,
    staleTime:15_000,
  })

  const createMut = useMutation({
    mutationFn:(body:any)=>apiClient.post('/api/v1/supply-chain/purchase-orders',body),
    onSuccess:()=>{ qc.invalidateQueries({queryKey:['scm-pos']}); qc.invalidateQueries({queryKey:['scm-summary']}); setShowCreate(false); setForm(initPO()); setErr('') },
    onError:(e:any)=>setErr(e.response?.data?.message||'Failed to create PO'),
  })
  const actionMut = useMutation({
    mutationFn:({id,action}:{id:string;action:string})=>apiClient.post(`/api/v1/supply-chain/purchase-orders/${id}/${action}`),
    onSuccess:()=>{ qc.invalidateQueries({queryKey:['scm-pos']}); qc.invalidateQueries({queryKey:['scm-po-detail',expanded]}); qc.invalidateQueries({queryKey:['scm-summary']}) },
  })

  const STATUSES=['','DRAFT','PENDING_APPROVAL','APPROVED','SENT','PARTIALLY_RECEIVED','FULLY_RECEIVED']

  return (
    <div>
      <div style={{display:'flex',justifyContent:'space-between',alignItems:'center',marginBottom:16,gap:10,flexWrap:'wrap' as const}}>
        <div style={{display:'flex',gap:6,flexWrap:'wrap' as const}}>
          {STATUSES.map(s=><button key={s} onClick={()=>setStatus(s)} style={filterBtn(statusFilter===s)}>{s?s.replace(/_/g,' '):'All'}</button>)}
        </div>
        <Btn icon={Plus} onClick={()=>{setShowCreate(true);setErr('')}}>New PO</Btn>
      </div>

      {isLoading ? <Spinner/> : orders.length===0 ? <Empty icon={ShoppingCart} title="No purchase orders" sub="Create a purchase order to start procurement"/> : (
        <div style={{border:'1px solid #E2E8F0',borderRadius:12,overflow:'hidden'}}>
          <table style={{width:'100%',borderCollapse:'collapse'}}>
            <thead><tr style={{background:'#F8FAFC'}}>
              {['','PO Number','Supplier','Project','Status','Total','Required By','Actions'].map(h=><th key={h} style={th}>{h}</th>)}
            </tr></thead>
            <tbody>
              {orders.map((po,i)=>(
                <React.Fragment key={po.id}>
                  <tr style={{background:i%2===0?'#fff':'#FAFAFA',borderTop:'1px solid #F1F5F9'}}>
                    <td style={{...td,width:32,padding:'12px 8px 12px 16px'}}>
                      <button onClick={()=>setExpanded(expanded===po.id?null:po.id)} style={{background:'none',border:'none',cursor:'pointer',color:'#64748B',display:'flex'}}>
                        {expanded===po.id?<ChevronDown size={16}/>:<ChevronRight size={16}/>}
                      </button>
                    </td>
                    <td style={td}><span style={{fontWeight:700,color:'#1B3A6B'}}>{po.orderNumber}</span></td>
                    <td style={td}>{po.supplierName}</td>
                    <td style={td}><span style={{color:'#94A3B8'}}>{po.projectRef||'—'}</span></td>
                    <td style={td}><Badge status={po.status}/></td>
                    <td style={td}><strong>{fmtR(po.totalAmount)}</strong></td>
                    <td style={td}>{fmtDate(po.requiredByDate)}</td>
                    <td style={td}>
                      <div style={{display:'flex',gap:6}}>
                        {po.status==='DRAFT' && <ActionBtn color="#92400E" bg="#FEF3C7" border="#FCD34D" onClick={()=>actionMut.mutate({id:po.id,action:'submit'})}>Submit</ActionBtn>}
                        {po.status==='PENDING_APPROVAL' && <ActionBtn color="#166534" bg="#DCFCE7" border="#86EFAC" onClick={()=>actionMut.mutate({id:po.id,action:'approve'})}>Approve</ActionBtn>}
                        {po.status==='APPROVED' && <ActionBtn color="#1D4ED8" bg="#DBEAFE" border="#BFDBFE" onClick={()=>actionMut.mutate({id:po.id,action:'send'})}>Mark Sent</ActionBtn>}
                        {po.status==='SENT' && <ActionBtn color="#7C3AED" bg="#EDE9FE" border="#DDD6FE" onClick={()=>setShowGR(po.id)}>Receive GR</ActionBtn>}
                        {po.status==='DRAFT' && <ActionBtn color="#0D9488" bg="#F0FDFA" border="#99F6E4" onClick={()=>setShowLines(po.id)}>+ Lines</ActionBtn>}
                      </div>
                    </td>
                  </tr>
                  {/* Expanded detail */}
                  {expanded===po.id && (
                    <tr style={{background:'#F8FAFC',borderTop:'1px solid #F1F5F9'}}>
                      <td colSpan={8} style={{padding:'0 16px 16px 48px'}}>
                        <div style={{marginTop:12}}>
                          <div style={{fontWeight:700,fontSize:12,color:'#94A3B8',textTransform:'uppercase' as const,letterSpacing:'0.05em',marginBottom:8}}>Line Items</div>
                          {poLines.length>0 ? (
                            <table style={{width:'100%',borderCollapse:'collapse',border:'1px solid #E2E8F0',borderRadius:8,overflow:'hidden'}}>
                              <thead><tr style={{background:'#F1F5F9'}}>
                                {['Item','Supplier SKU','Qty','Unit Cost','VAT','Total (excl)'].map(h=><th key={h} style={{...th,fontSize:10}}>{h}</th>)}
                              </tr></thead>
                              <tbody>
                                {poLines.map(l=>(
                                  <tr key={l.id} style={{borderTop:'1px solid #F1F5F9'}}>
                                    <td style={{...td,fontSize:12,fontWeight:600}}>{l.itemName}</td>
                                    <td style={{...td,fontSize:12,color:'#94A3B8'}}>{l.supplierSku||'—'}</td>
                                    <td style={{...td,fontSize:12}}>{l.qtyOrdered}</td>
                                    <td style={{...td,fontSize:12}}>{fmtR(l.unitCost)}</td>
                                    <td style={{...td,fontSize:12,color:'#64748B'}}>{l.vatRate}%</td>
                                    <td style={{...td,fontSize:12,fontWeight:700}}>{fmtR(l.lineTotal)}</td>
                                  </tr>
                                ))}
                              </tbody>
                            </table>
                          ) : (
                            <div style={{fontSize:13,color:'#94A3B8',padding:'12px 0'}}>
                              No line items yet.{po.status==='DRAFT'&&<> <button onClick={()=>setShowLines(po.id)} style={{background:'none',border:'none',cursor:'pointer',color:'#1B3A6B',fontWeight:600,fontSize:13}}>+ Add line items →</button></>}
                            </div>
                          )}
                        </div>
                      </td>
                    </tr>
                  )}
                </React.Fragment>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* New PO Modal */}
      {showCreate && (
        <Modal title="New Purchase Order" onClose={()=>setShowCreate(false)}>
          <div style={{display:'grid',gridTemplateColumns:'1fr 1fr',gap:14}}>
            <F label="Supplier *" span={2}>
              <select value={form.supplierId} onChange={e=>sf('supplierId',e.target.value)} style={inp}>
                <option value="">Select supplier…</option>
                {suppliers.map(s=><option key={s.id} value={s.id}>{s.name}</option>)}
              </select>
            </F>
            <F label="Deliver To">
              <select value={form.deliverToLocation} onChange={e=>sf('deliverToLocation',e.target.value)} style={inp}>
                <option value="">Select location…</option>
                {locations.map(l=><option key={l.id} value={l.id}>{l.name}</option>)}
              </select>
            </F>
            <F label="Required By"><input type="date" value={form.requiredByDate} onChange={e=>sf('requiredByDate',e.target.value)} style={inp}/></F>
            <F label="Currency">
              <select value={form.currency} onChange={e=>sf('currency',e.target.value)} style={inp}>
                {['ZAR','USD','EUR','GBP'].map(c=><option key={c} value={c}>{c}</option>)}
              </select>
            </F>
            <F label="Project Reference"><input value={form.projectRef} onChange={e=>sf('projectRef',e.target.value)} placeholder="e.g. SITE-A-2026" style={inp}/></F>
            <F label="Notes" span={2}><textarea value={form.notes} onChange={e=>sf('notes',e.target.value)} placeholder="Instructions for supplier…" style={{...inp,minHeight:60,resize:'vertical' as const}}/></F>
          </div>
          {err && <ErrBox msg={err}/>}
          <MF onCancel={()=>setShowCreate(false)} onConfirm={()=>{ if(!form.supplierId){setErr('Select a supplier');return} createMut.mutate({...form,deliverToLocation:form.deliverToLocation||null,requiredByDate:form.requiredByDate||null,projectRef:form.projectRef||null,notes:form.notes||null}) }} label={createMut.isPending?'Creating…':'Create PO'} loading={createMut.isPending}/>
        </Modal>
      )}

      {/* Add Line Items Modal */}
      {showLines && <AddPoLinesModal poId={showLines} onClose={()=>{ setShowLines(null); qc.invalidateQueries({queryKey:['scm-po-lines',showLines]}); qc.invalidateQueries({queryKey:['scm-pos']}) }}/>}

      {/* Goods Receipt Modal */}
      {showGR && (
        <GoodsReceiptModal
          poId={showGR}
          po={orders.find(p=>p.id===showGR)!}
          locations={locations}
          onClose={()=>{ setShowGR(null); qc.invalidateQueries({queryKey:['scm-pos']}); qc.invalidateQueries({queryKey:['scm-summary']}) }}
        />
      )}
    </div>
  )
}

// ── Add PO Lines Modal ────────────────────────────────────────────────────────
function AddPoLinesModal({poId,onClose}:{poId:string;onClose:()=>void}) {
  const [lines,setLines] = useState([{description:'',quantity:'1',unitPrice:'',unitOfMeasure:'EACH',catalogueItemId:''}])
  const [err,setErr] = useState('')
  const [catSearch,setCatSearch] = useState<string[]>(lines.map(()=>''))
  const [catResults,setCatResults] = useState<CatalogueItem[][]>(lines.map(()=>[]))

  const addLine = ()=>{ setLines(l=>[...l,{description:'',quantity:'1',unitPrice:'',unitOfMeasure:'EACH',catalogueItemId:''}]); setCatSearch(s=>[...s,'']); setCatResults(r=>[...r,[]]) }
  const upd = (i:number,k:string,v:string)=>setLines(l=>l.map((x,j)=>j===i?{...x,[k]:v}:x))

  const searchCat = async(i:number,q:string)=>{
    setCatSearch(s=>s.map((x,j)=>j===i?q:x))
    if(q.length<2){setCatResults(r=>r.map((x,j)=>j===i?[]:x));return}
    try {
      const r=await apiClient.get(`/api/v1/catalogue/items?search=${encodeURIComponent(q)}&size=8`)
      setCatResults(res=>res.map((x,j)=>j===i?unwrapList<CatalogueItem>(r):x))
    } catch {}
  }

  const pickCat = (i:number,item:CatalogueItem)=>{
    upd(i,'description',item.name)
    upd(i,'catalogueItemId',item.id)
    upd(i,'unitPrice',String(item.unitPrice??''))
    upd(i,'unitOfMeasure',item.unit||'EACH')
    setCatSearch(s=>s.map((x,j)=>j===i?item.name:x))
    setCatResults(r=>r.map((x,j)=>j===i?[]:x))
  }

  const saveMut = useMutation({
    mutationFn:async()=>{
      for(const l of lines.filter(l=>l.description.trim())) {
        await apiClient.post(`/api/v1/supply-chain/purchase-orders/${poId}/lines`,{
          itemName: l.description,           // maps to ScPoLine.itemName
          qtyOrdered: parseFloat(l.quantity)||1,
          unitCost: parseFloat(l.unitPrice)||0,
          vatRate: null,                     // defaults to 15% in ScPoLine.create()
          catalogueItemId: l.catalogueItemId||null,
          supplierSku: null,
        })
      }
    },
    onSuccess:onClose,
    onError:(e:any)=>setErr(e.response?.data?.message||'Failed to save lines'),
  })

  return (
    <Modal title="Add Line Items to PO" onClose={onClose} wide>
      <p style={{fontSize:13,color:'#64748B',marginBottom:16}}>Search the catalogue or type a free-text description for each line.</p>
      <div style={{display:'flex',flexDirection:'column',gap:10}}>
        {lines.map((l,i)=>(
          <div key={i} style={{padding:'12px 14px',background:'#F8FAFC',borderRadius:10,border:'1px solid #E2E8F0'}}>
            <div style={{display:'grid',gridTemplateColumns:'2fr 1fr 1fr 1fr',gap:10,marginBottom:8}}>
              <div style={{position:'relative'}}>
                <input value={catSearch[i]} onChange={e=>searchCat(i,e.target.value)}
                  placeholder="Search catalogue or type description…" style={{...inp,fontSize:13,padding:'7px 10px'}}/>
                {catResults[i].length>0 && (
                  <div style={{position:'absolute',top:'100%',left:0,right:0,zIndex:50,background:'#fff',border:'1px solid #E2E8F0',borderRadius:8,boxShadow:'0 8px 24px rgba(0,0,0,0.12)',maxHeight:200,overflowY:'auto'}}>
                    {catResults[i].map(c=>(
                      <div key={c.id} onClick={()=>pickCat(i,c)} style={{padding:'8px 12px',cursor:'pointer',fontSize:13,borderBottom:'1px solid #F1F5F9'}}
                        onMouseEnter={e=>(e.currentTarget.style.background='#F8FAFC')} onMouseLeave={e=>(e.currentTarget.style.background='#fff')}>
                        <span style={{fontWeight:600}}>{c.name}</span> {c.code&&<span style={{color:'#94A3B8',fontSize:11}}>· {c.code}</span>}
                        {c.unitPrice&&<span style={{float:'right',color:'#0D9488',fontSize:12,fontWeight:600}}>{fmtR(c.unitPrice)}</span>}
                      </div>
                    ))}
                  </div>
                )}
              </div>
              <input type="number" value={l.quantity} onChange={e=>upd(i,'quantity',e.target.value)} placeholder="Qty" style={{...inp,fontSize:13,padding:'7px 10px'}}/>
              <input type="number" step="0.01" value={l.unitPrice} onChange={e=>upd(i,'unitPrice',e.target.value)} placeholder="Unit price" style={{...inp,fontSize:13,padding:'7px 10px'}}/>
              <select value={l.unitOfMeasure} onChange={e=>upd(i,'unitOfMeasure',e.target.value)} style={{...inp,fontSize:13,padding:'7px 10px'}}>
                {['EACH','LITRE','KG','TON','METRE','PACK','SET','BOX'].map(u=><option key={u} value={u}>{u}</option>)}
              </select>
            </div>
            <div style={{fontSize:12,color:'#94A3B8',display:'flex',justifyContent:'space-between'}}>
              <span>Line total: <strong style={{color:'#0F172A'}}>{fmtR((parseFloat(l.quantity)||0)*(parseFloat(l.unitPrice)||0))}</strong></span>
              {lines.length>1 && <button onClick={()=>{setLines(l=>l.filter((_,j)=>j!==i));setCatSearch(s=>s.filter((_,j)=>j!==i));setCatResults(r=>r.filter((_,j)=>j!==i))}} style={{background:'none',border:'none',cursor:'pointer',color:'#DC2626',fontSize:12}}>Remove</button>}
            </div>
          </div>
        ))}
        <button onClick={addLine} style={{padding:'8px 14px',border:'1px dashed #E2E8F0',borderRadius:9,background:'#F8FAFC',color:'#64748B',fontSize:13,cursor:'pointer'}}>
          + Add another line
        </button>
      </div>
      {err && <ErrBox msg={err}/>}
      <MF onCancel={onClose} onConfirm={()=>saveMut.mutate()} label={saveMut.isPending?'Saving…':'Save Line Items'} loading={saveMut.isPending}/>
    </Modal>
  )
}

// ── Goods Receipt Modal ───────────────────────────────────────────────────────
function GoodsReceiptModal({poId,po,locations,onClose}:{poId:string;po:PurchaseOrder;locations:StockLocation[];onClose:()=>void}) {
  const [locationId,setLocationId] = useState(locations.find(l=>l.isDefault)?.id||'')
  const [deliveryRef,setDeliveryRef] = useState('')
  const [err,setErr] = useState('')
  const qc = useQueryClient()

  const createGR = useMutation({
    mutationFn:()=>apiClient.post('/api/v1/supply-chain/goods-receipts',{purchaseOrderId:poId,receivedToLocation:locationId,deliveryNoteRef:deliveryRef||null}),
    onSuccess:(r)=>{
      const grId = r.data?.data?.id??r.data?.id
      if(grId) postGR.mutate(grId)
      else onClose()
    },
    onError:(e:any)=>setErr(e.response?.data?.message||'Failed to create GR'),
  })

  const postGR = useMutation({
    mutationFn:(grId:string)=>apiClient.post(`/api/v1/supply-chain/goods-receipts/${grId}/post`,[]),
    onSuccess:()=>{ qc.invalidateQueries({queryKey:['scm-pos']}); qc.invalidateQueries({queryKey:['scm-inventory']}); qc.invalidateQueries({queryKey:['scm-summary']}); onClose() },
    onError:(e:any)=>setErr(e.response?.data?.message||'Failed to post GR'),
  })

  const loading = createGR.isPending||postGR.isPending

  return (
    <Modal title="Receive Goods" onClose={onClose}>
      <div style={{padding:'12px 14px',background:'#EFF6FF',borderRadius:10,marginBottom:16,fontSize:13,color:'#1D4ED8'}}>
        Receiving against <strong>{po.orderNumber}</strong> — {po.supplierName}
      </div>
      <div style={{display:'flex',flexDirection:'column',gap:14}}>
        <F label="Deliver To Location *">
          <select value={locationId} onChange={e=>setLocationId(e.target.value)} style={inp}>
            <option value="">Select location…</option>
            {locations.map(l=><option key={l.id} value={l.id}>{l.name}</option>)}
          </select>
        </F>
        <F label="Delivery Note / Waybill Ref">
          <input value={deliveryRef} onChange={e=>setDeliveryRef(e.target.value)} placeholder="e.g. DN-2026-4521" style={inp}/>
        </F>
      </div>
      <p style={{fontSize:12,color:'#94A3B8',marginTop:12}}>This will update inventory quantities at the selected location.</p>
      {err && <ErrBox msg={err}/>}
      <MF onCancel={onClose} onConfirm={()=>{ if(!locationId){setErr('Select a delivery location');return} createGR.mutate() }} label={loading?'Posting…':'Confirm Receipt'} loading={loading}/>
    </Modal>
  )
}

// ── Inventory ─────────────────────────────────────────────────────────────────
function InventoryTab() {
  const qc = useQueryClient()
  const [locationId,setLocationId] = useState('')
  const [showOpening,setShowOpening] = useState(false)
  const [movementsItem,setMovementsItem] = useState<InventoryItem|null>(null)
  const [err,setErr] = useState('')
  const initF=()=>({locationId:'',catalogueItemId:'',catalogueName:'',qty:'',unitCost:'',reorderPoint:'',reorderQty:'',binLocation:''})
  const [form,setForm] = useState(initF())
  const sf=(k:string,v:string)=>setForm(p=>({...p,[k]:v}))
  const [catSearch,setCatSearch] = useState('')
  const [catResults,setCatResults] = useState<CatalogueItem[]>([])
  const [showCat,setShowCat] = useState(false)

  const {data:locations=[]} = useQuery<StockLocation[]>({queryKey:['scm-locations'],queryFn:async()=>{ const r=await apiClient.get('/api/v1/supply-chain/locations'); return unwrapList<StockLocation>(r) },staleTime:60_000})
  const {data:inventory=[],isLoading} = useQuery<InventoryItem[]>({queryKey:['scm-inventory',locationId],queryFn:async()=>{ const r=await apiClient.get(locationId?`/api/v1/supply-chain/inventory?locationId=${locationId}`:'/api/v1/supply-chain/inventory'); return unwrapList<InventoryItem>(r) },staleTime:30_000})
  const {data:lowStock=[]} = useQuery<InventoryItem[]>({queryKey:['scm-low-stock'],queryFn:async()=>{ const r=await apiClient.get('/api/v1/supply-chain/inventory/low-stock'); return unwrapList<InventoryItem>(r) },staleTime:30_000})

  useEffect(()=>{
    if(catSearch.length<2){setCatResults([]);return}
    const t=setTimeout(async()=>{
      try { const r=await apiClient.get(`/api/v1/catalogue/items?search=${encodeURIComponent(catSearch)}&size=8`); setCatResults(unwrapList<CatalogueItem>(r)) } catch { setCatResults([]) }
    },300)
    return ()=>clearTimeout(t)
  },[catSearch])

  const openMut = useMutation({
    mutationFn:(body:any)=>apiClient.post('/api/v1/supply-chain/inventory/opening',body),
    onSuccess:()=>{ qc.invalidateQueries({queryKey:['scm-inventory']}); qc.invalidateQueries({queryKey:['scm-low-stock']}); qc.invalidateQueries({queryKey:['scm-summary']}); setShowOpening(false); setForm(initF()); setCatSearch(''); setErr('') },
    onError:(e:any)=>setErr(e.response?.data?.message||'Failed to set stock'),
  })

  return (
    <div>
      <div style={{display:'flex',justifyContent:'space-between',alignItems:'center',marginBottom:16,gap:10,flexWrap:'wrap' as const}}>
        <select value={locationId} onChange={e=>setLocationId(e.target.value)} style={{...inp,width:'auto',minWidth:200}}>
          <option value="">All locations</option>
          {locations.map(l=><option key={l.id} value={l.id}>{l.name}</option>)}
        </select>
        <Btn icon={Plus} onClick={()=>{setShowOpening(true);setErr('')}}>Set Opening Stock</Btn>
      </div>

      {lowStock.length>0 && <InfoBanner variant="error" icon={AlertTriangle}>{lowStock.length} item{lowStock.length!==1?'s':''} below reorder point</InfoBanner>}

      {isLoading ? <Spinner/> : inventory.length===0 ? <Empty icon={Package} title="No inventory items" sub="Set opening stock or receive a goods receipt to populate inventory"/> : (
        <div style={{border:'1px solid #E2E8F0',borderRadius:12,overflow:'hidden'}}>
          <table style={{width:'100%',borderCollapse:'collapse'}}>
            <thead><tr style={{background:'#F8FAFC'}}>{['Item','Bin','Qty On Hand','Reorder Pt','Reorder Qty','Avg Cost','Status',''].map(h=><th key={h} style={th}>{h}</th>)}</tr></thead>
            <tbody>
              {inventory.map((item,i)=>(
                <tr key={item.id} style={{background:i%2===0?'#fff':'#FAFAFA',borderTop:'1px solid #F1F5F9'}}>
                  <td style={td}><span style={{fontFamily:'monospace',fontSize:12,color:'#64748B'}}>{item.catalogueItemId?.slice(0,8)}…</span></td>
                  <td style={td}>{item.binLocation||'—'}</td>
                  <td style={td}><strong style={{color:item.lowStock?'#DC2626':'#0F172A'}}>{item.qtyOnHand}</strong></td>
                  <td style={td}>{item.reorderPoint}</td>
                  <td style={td}>{item.reorderQty}</td>
                  <td style={td}>{fmtR(item.avgCost)}</td>
                  <td style={td}>
                    {item.lowStock
                      ? <span style={{display:'flex',alignItems:'center',gap:4,color:'#DC2626',fontSize:12,fontWeight:600}}><AlertTriangle size={12}/>Low Stock</span>
                      : <span style={{display:'flex',alignItems:'center',gap:4,color:'#059669',fontSize:12,fontWeight:600}}><CheckCircle size={12}/>OK</span>
                    }
                  </td>
                  <td style={td}>
                    <button onClick={()=>setMovementsItem(item)} style={{background:'none',border:'none',cursor:'pointer',color:'#1B3A6B',fontSize:12,fontWeight:600}}>History</button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* Set Opening Stock */}
      {showOpening && (
        <Modal title="Set Opening Stock" onClose={()=>setShowOpening(false)}>
          <div style={{display:'grid',gridTemplateColumns:'1fr 1fr',gap:14}}>
            <F label="Location *" span={2}>
              <select value={form.locationId} onChange={e=>sf('locationId',e.target.value)} style={inp}>
                <option value="">Select location…</option>
                {locations.map(l=><option key={l.id} value={l.id}>{l.name}</option>)}
              </select>
            </F>
            <F label="Catalogue Item *" span={2}>
              <div style={{position:'relative'}}>
                <Search size={14} style={{position:'absolute',left:10,top:'50%',transform:'translateY(-50%)',color:'#94A3B8'}}/>
                <input value={catSearch} onChange={e=>{setCatSearch(e.target.value);setShowCat(true)}} onFocus={()=>setShowCat(true)}
                  placeholder="Search catalogue by name or code…" style={{...inp,paddingLeft:32}}/>
                {showCat && catResults.length>0 && (
                  <div style={{position:'absolute',top:'100%',left:0,right:0,zIndex:50,background:'#fff',border:'1px solid #E2E8F0',borderRadius:8,boxShadow:'0 8px 24px rgba(0,0,0,0.12)',maxHeight:200,overflowY:'auto'}}>
                    {catResults.map(c=>(
                      <div key={c.id} onClick={()=>{setCatSearch(c.name);sf('catalogueItemId',c.id);sf('catalogueName',c.name);if(c.unitPrice)sf('unitCost',String(c.unitPrice));setShowCat(false)}} style={{padding:'8px 12px',cursor:'pointer',fontSize:13,borderBottom:'1px solid #F1F5F9'}}
                        onMouseEnter={e=>(e.currentTarget.style.background='#F8FAFC')} onMouseLeave={e=>(e.currentTarget.style.background='#fff')}>
                        <strong>{c.name}</strong> {c.code&&<span style={{color:'#94A3B8',fontSize:11}}>({c.code})</span>}
                        {c.unitPrice&&<span style={{float:'right',color:'#0D9488',fontSize:12,fontWeight:600}}>{fmtR(c.unitPrice)}</span>}
                      </div>
                    ))}
                  </div>
                )}
              </div>
              {form.catalogueItemId && <div style={{fontSize:11,color:'#059669',marginTop:4}}>✓ {form.catalogueName}</div>}
            </F>
            <F label="Opening Qty *"><input type="number" value={form.qty} onChange={e=>sf('qty',e.target.value)} placeholder="0" style={inp}/></F>
            <F label="Unit Cost (R)"><input type="number" step="0.01" value={form.unitCost} onChange={e=>sf('unitCost',e.target.value)} placeholder="0.00" style={inp}/></F>
            <F label="Reorder Point"><input type="number" value={form.reorderPoint} onChange={e=>sf('reorderPoint',e.target.value)} placeholder="0" style={inp}/></F>
            <F label="Reorder Qty"><input type="number" value={form.reorderQty} onChange={e=>sf('reorderQty',e.target.value)} placeholder="0" style={inp}/></F>
            <F label="Bin Location" span={2}><input value={form.binLocation} onChange={e=>sf('binLocation',e.target.value)} placeholder="e.g. A3-S2" style={inp}/></F>
          </div>
          {err && <ErrBox msg={err}/>}
          <MF onCancel={()=>setShowOpening(false)} onConfirm={()=>{
            if(!form.locationId||!form.catalogueItemId||!form.qty){setErr('Location, item and qty are required');return}
            openMut.mutate({locationId:form.locationId,catalogueItemId:form.catalogueItemId,qty:parseFloat(form.qty),unitCost:parseFloat(form.unitCost)||0,reorderPoint:parseFloat(form.reorderPoint)||0,reorderQty:parseFloat(form.reorderQty)||0,binLocation:form.binLocation||null})
          }} label={openMut.isPending?'Saving…':'Set Stock'} loading={openMut.isPending}/>
        </Modal>
      )}

      {/* Stock movement history */}
      {movementsItem && <StockMovementsModal item={movementsItem} onClose={()=>setMovementsItem(null)}/>}
    </div>
  )
}

function StockMovementsModal({item,onClose}:{item:InventoryItem;onClose:()=>void}) {
  const {data:movements=[],isLoading} = useQuery<StockMovement[]>({
    queryKey:['scm-movements',item.id],
    queryFn:async()=>{ const r=await apiClient.get(`/api/v1/supply-chain/inventory/${item.id}/movements?size=50`); return unwrapList<StockMovement>(r) },
  })
  return (
    <Modal title="Stock Movement History" onClose={onClose}>
      <div style={{fontSize:12,color:'#64748B',marginBottom:12}}>Item: <strong style={{fontFamily:'monospace'}}>{item.catalogueItemId.slice(0,12)}…</strong> · Current stock: <strong>{item.qtyOnHand}</strong></div>
      {isLoading ? <Spinner/> : movements.length===0 ? <Empty icon={RefreshCw} title="No movements" sub="Stock movements appear when goods are received or adjustments are made"/> : (
        <div style={{maxHeight:400,overflowY:'auto'}}>
          <table style={{width:'100%',borderCollapse:'collapse'}}>
            <thead><tr style={{background:'#F8FAFC'}}>{['Date','Type','Qty','Cost/Unit','Reference','By'].map(h=><th key={h} style={{...th,fontSize:10}}>{h}</th>)}</tr></thead>
            <tbody>
              {movements.map((m,i)=>(
                <tr key={m.id} style={{borderTop:'1px solid #F1F5F9',background:i%2===0?'#fff':'#FAFAFA'}}>
                  <td style={{...td,fontSize:12}}>{fmtDate(m.movedAt)}</td>
                  <td style={{...td,fontSize:12}}><Badge status={m.movementType}/></td>
                  <td style={{...td,fontSize:12,fontWeight:700,color:m.qty>0?'#059669':'#DC2626'}}>{m.qty>0?'+':''}{m.qty}</td>
                  <td style={{...td,fontSize:12}}>{fmtR(m.costPerUnit)}</td>
                  <td style={{...td,fontSize:12,color:'#64748B'}}>{m.reference||'—'}</td>
                  <td style={{...td,fontSize:12,color:'#64748B'}}>{m.movedByName||'—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      <div style={{display:'flex',justifyContent:'flex-end',marginTop:16}}>
        <Btn variant="secondary" onClick={onClose}>Close</Btn>
      </div>
    </Modal>
  )
}

// ── Supplier Invoices ─────────────────────────────────────────────────────────
function InvoicesTab() {
  const qc = useQueryClient()
  const [statusFilter,setStatus] = useState('')
  const [showCreate,setShowCreate] = useState(false)
  const [err,setErr] = useState('')
  const initF=()=>({supplierId:'',supplierInvoiceRef:'',invoiceDate:'',dueDate:'',subtotal:'',vatAmount:'',totalAmount:'',notes:''})
  const [form,setForm] = useState(initF())
  const sf=(k:string,v:string)=>setForm(p=>({...p,[k]:v}))

  const {data:suppliers=[]} = useQuery<Supplier[]>({queryKey:['scm-suppliers-list'],queryFn:async()=>{ const r=await apiClient.get('/api/v1/supply-chain/suppliers?size=200'); return unwrapList<Supplier>(r) },staleTime:60_000})
  const {data:invoices=[],isLoading} = useQuery<SupplierInvoice[]>({
    queryKey:['scm-invoices',statusFilter],
    queryFn:async()=>{ const r=await apiClient.get(statusFilter?`/api/v1/supply-chain/supplier-invoices?status=${statusFilter}&size=50`:'/api/v1/supply-chain/supplier-invoices?size=50'); return unwrapList<SupplierInvoice>(r) },
    staleTime:30_000,
  })

  const createMut = useMutation({
    mutationFn:(body:any)=>apiClient.post('/api/v1/supply-chain/supplier-invoices',body),
    onSuccess:()=>{ qc.invalidateQueries({queryKey:['scm-invoices']}); qc.invalidateQueries({queryKey:['scm-summary']}); setShowCreate(false); setForm(initF()); setErr('') },
    onError:(e:any)=>setErr(e.response?.data?.message||'Failed to create invoice'),
  })
  const approveMut = useMutation({mutationFn:(id:string)=>apiClient.post(`/api/v1/supply-chain/supplier-invoices/${id}/approve`),onSuccess:()=>qc.invalidateQueries({queryKey:['scm-invoices']})})
  const payMut    = useMutation({mutationFn:(id:string)=>apiClient.post(`/api/v1/supply-chain/supplier-invoices/${id}/pay`,{paymentReference:'MANUAL'}),onSuccess:()=>qc.invalidateQueries({queryKey:['scm-invoices']})})

  const subtotal = parseFloat(form.subtotal)||0
  const vat      = parseFloat(form.vatAmount)||0
  const total    = (subtotal+vat).toFixed(2)

  const STATUSES=['','RECEIVED','UNDER_REVIEW','APPROVED','PAID','DISPUTED']

  const matchIcon = (ms:string) => {
    if(ms==='MATCHED') return {icon:CheckCircle,color:'#059669',label:'3-way match ✓'}
    if(ms==='PO_MATCHED') return {icon:CheckCircle,color:'#1D4ED8',label:'PO matched'}
    if(ms==='PARTIAL_MATCH') return {icon:AlertCircle,color:'#D97706',label:'Partial match'}
    return {icon:AlertTriangle,color:'#DC2626',label:'No PO'}
  }

  return (
    <div>
      <div style={{display:'flex',justifyContent:'space-between',alignItems:'center',marginBottom:16,gap:10,flexWrap:'wrap' as const}}>
        <div style={{display:'flex',gap:6,flexWrap:'wrap' as const}}>
          {STATUSES.map(s=><button key={s} onClick={()=>setStatus(s)} style={filterBtn(statusFilter===s)}>{s?s.replace(/_/g,' '):'All'}</button>)}
        </div>
        <Btn icon={Plus} onClick={()=>{setShowCreate(true);setErr('')}}>Record Invoice</Btn>
      </div>

      {isLoading ? <Spinner/> : invoices.length===0 ? <Empty icon={FileText} title="No supplier invoices" sub="Record invoices received from suppliers to process payment"/> : (
        <div style={{border:'1px solid #E2E8F0',borderRadius:12,overflow:'hidden'}}>
          <table style={{width:'100%',borderCollapse:'collapse'}}>
            <thead><tr style={{background:'#F8FAFC'}}>{['Invoice #','Supplier Ref','Total','Due Date','3-Way Match','Status',''].map(h=><th key={h} style={th}>{h}</th>)}</tr></thead>
            <tbody>
              {invoices.map((inv,i)=>{
                const m = matchIcon(inv.matchStatus??'NO_PO')
                return (
                  <tr key={inv.id} style={{background:i%2===0?'#fff':'#FAFAFA',borderTop:'1px solid #F1F5F9'}}>
                    <td style={td}><span style={{fontWeight:700,color:'#1B3A6B'}}>{inv.invoiceNumber??`INV-${inv.id.slice(0,6).toUpperCase()}`}</span></td>
                    <td style={td}><span style={{color:'#64748B'}}>{inv.supplierInvoiceRef||'—'}</span></td>
                    <td style={td}><strong>{fmtR(inv.totalAmount)}</strong></td>
                    <td style={td}><span style={{color:inv.overdue?'#DC2626':'#475569'}}>{fmtDate(inv.dueDate)}{inv.overdue&&' ⚠'}</span></td>
                    <td style={td}>
                      <div style={{display:'flex',alignItems:'center',gap:5}}>
                        <m.icon size={13} color={m.color}/>
                        <span style={{fontSize:12,color:m.color,fontWeight:600}}>{m.label}</span>
                      </div>
                    </td>
                    <td style={td}><Badge status={inv.status}/></td>
                    <td style={td}>
                      <div style={{display:'flex',gap:6}}>
                        {inv.status==='RECEIVED' && <ActionBtn color="#166534" bg="#DCFCE7" border="#86EFAC" onClick={()=>approveMut.mutate(inv.id)}>Approve</ActionBtn>}
                        {inv.status==='APPROVED' && <ActionBtn color="#1D4ED8" bg="#EFF6FF" border="#BFDBFE" onClick={()=>payMut.mutate(inv.id)}>Mark Paid</ActionBtn>}
                      </div>
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}

      {showCreate && (
        <Modal title="Record Supplier Invoice" onClose={()=>setShowCreate(false)}>
          <div style={{display:'grid',gridTemplateColumns:'1fr 1fr',gap:14}}>
            <F label="Supplier *" span={2}>
              <select value={form.supplierId} onChange={e=>sf('supplierId',e.target.value)} style={inp}>
                <option value="">Select supplier…</option>
                {suppliers.map(s=><option key={s.id} value={s.id}>{s.name}</option>)}
              </select>
            </F>
            <F label="Supplier's Invoice Ref"><input value={form.supplierInvoiceRef} onChange={e=>sf('supplierInvoiceRef',e.target.value)} placeholder="INV-2026-001" style={inp}/></F>
            <F label="Invoice Date *"><input type="date" value={form.invoiceDate} onChange={e=>sf('invoiceDate',e.target.value)} style={inp}/></F>
            <F label="Due Date *"><input type="date" value={form.dueDate} onChange={e=>sf('dueDate',e.target.value)} style={inp}/></F>
            <F label="Subtotal (excl. VAT) *"><input type="number" step="0.01" value={form.subtotal} onChange={e=>sf('subtotal',e.target.value)} placeholder="0.00" style={inp}/></F>
            <F label="VAT (R)"><input type="number" step="0.01" value={form.vatAmount} onChange={e=>sf('vatAmount',e.target.value)} placeholder="0.00" style={inp}/></F>
            <F label="Total (incl. VAT) *">
              <input type="number" step="0.01" value={form.totalAmount||total} onChange={e=>sf('totalAmount',e.target.value)} placeholder={total} style={inp}/>
            </F>
            <F label="Notes" span={2}><textarea value={form.notes} onChange={e=>sf('notes',e.target.value)} placeholder="Any notes…" style={{...inp,minHeight:60,resize:'vertical' as const}}/></F>
          </div>
          {err && <ErrBox msg={err}/>}
          <MF onCancel={()=>setShowCreate(false)} onConfirm={()=>{
            if(!form.supplierId||!form.invoiceDate||!form.dueDate||!form.subtotal){setErr('Supplier, dates and subtotal are required');return}
            createMut.mutate({supplierId:form.supplierId,supplierInvoiceRef:form.supplierInvoiceRef||null,invoiceDate:form.invoiceDate,dueDate:form.dueDate,subtotal,vatAmount:vat,totalAmount:parseFloat(form.totalAmount)||subtotal+vat,notes:form.notes||null})
          }} label={createMut.isPending?'Saving…':'Record Invoice'} loading={createMut.isPending}/>
        </Modal>
      )}
    </div>
  )
}

// ── Shared UI components ──────────────────────────────────────────────────────
function Modal({title,children,onClose,wide}:{title:string;children:React.ReactNode;onClose:()=>void;wide?:boolean}) {
  return (
    <div style={{position:'fixed',inset:0,background:'rgba(15,23,42,0.5)',display:'flex',alignItems:'center',justifyContent:'center',zIndex:1000}}>
      <div style={{background:'#fff',borderRadius:14,padding:28,width:wide?720:600,maxHeight:'92vh',overflowY:'auto',boxShadow:'0 20px 60px rgba(0,0,0,0.2)'}}>
        <div style={{display:'flex',justifyContent:'space-between',alignItems:'center',marginBottom:22}}>
          <h3 style={{margin:0,fontSize:17,fontWeight:700,color:'#0F172A'}}>{title}</h3>
          <button onClick={onClose} style={{background:'none',border:'none',cursor:'pointer',color:'#94A3B8'}}><X size={20}/></button>
        </div>
        {children}
      </div>
    </div>
  )
}
function MF({onCancel,onConfirm,label,loading}:{onCancel:()=>void;onConfirm:()=>void;label:string;loading?:boolean}) {
  return (
    <div style={{display:'flex',justifyContent:'flex-end',gap:10,marginTop:24}}>
      <button onClick={onCancel} style={{padding:'9px 16px',border:'1px solid #E2E8F0',borderRadius:9,background:'#fff',fontSize:13,cursor:'pointer',color:'#374151'}}>Cancel</button>
      <button onClick={onConfirm} disabled={loading} style={{padding:'9px 16px',background:'#1B3A6B',color:'#fff',border:'none',borderRadius:9,fontSize:13,fontWeight:600,cursor:loading?'not-allowed':'pointer',opacity:loading?.6:1}}>
        {label}
      </button>
    </div>
  )
}
function F({label,children,span}:{label:string;children:React.ReactNode;span?:number}) {
  return <div style={span?{gridColumn:`span ${span}`}:undefined}><label style={{display:'block',fontSize:12,fontWeight:600,color:'#374151',marginBottom:5}}>{label}</label>{children}</div>
}
function IRow({icon:Icon,text}:{icon:React.ElementType;text:string}) {
  return <div style={{display:'flex',alignItems:'center',gap:6,fontSize:12,color:'#64748B'}}><Icon size={12} color="#94A3B8"/>{text}</div>
}
function Empty({icon:Icon,title,sub}:{icon:React.ElementType;title:string;sub:string}) {
  return <div style={{textAlign:'center',padding:'60px 20px',color:'#94A3B8'}}><Icon size={40} style={{marginBottom:12,opacity:.3}}/><div style={{fontWeight:600,color:'#475569',marginBottom:4}}>{title}</div><div style={{fontSize:13}}>{sub}</div></div>
}
function InfoBanner({variant,icon:Icon,children}:{variant:'error'|'warning'|'info';icon:React.ElementType;children:React.ReactNode}) {
  const s = {error:{bg:'#FEF2F2',border:'#FECACA',color:'#DC2626'},warning:{bg:'#FEF3C7',border:'#FCD34D',color:'#92400E'},info:{bg:'#EFF6FF',border:'#BFDBFE',color:'#1D4ED8'}}[variant]
  return <div style={{background:s.bg,border:`1px solid ${s.border}`,borderRadius:12,padding:'14px 18px',marginBottom:12,display:'flex',alignItems:'center',gap:10}}><Icon size={18} color={s.color}/><span style={{fontSize:13,color:s.color,fontWeight:600}}>{children}</span></div>
}
function ErrBox({msg}:{msg:string}) {
  return <div style={{marginTop:10,padding:'8px 12px',background:'#FEF2F2',border:'1px solid #FECACA',borderRadius:8,color:'#DC2626',fontSize:13}}>{msg}</div>
}
function Spinner() {
  return <div style={{textAlign:'center',padding:60,color:'#94A3B8',fontSize:14}}>Loading…</div>
}
function ActionBtn({color,bg,border,onClick,children}:{color:string;bg:string;border:string;onClick:()=>void;children:React.ReactNode}) {
  return <button onClick={onClick} style={{padding:'5px 10px',background:bg,color,border:`1px solid ${border}`,borderRadius:6,fontSize:12,cursor:'pointer',fontWeight:600}}>{children}</button>
}

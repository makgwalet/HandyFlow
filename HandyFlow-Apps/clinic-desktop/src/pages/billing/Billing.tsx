// src/pages/Billing.tsx
import { useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { useClaims, useOutstanding, usePayments, useRevenue } from '@/hooks/use-billing'
import { Card, CardHeader } from '@/components/ui/Card'
import { Button } from '@/components/ui/Button'
import { Badge } from '@/components/ui/Badge'
import { Table, TableHead, TableBody, TableRow, TableCell, TableHeader } from '@/components/ui/Table'
import { Modal } from '@/components/ui/Modal'
import { Input } from '@/components/ui/Input'
import { cn } from '@/lib/utils'

type Tab = 'outstanding' | 'claims' | 'payments' | 'revenue'

export function Billing() {
  const [searchParams] = useSearchParams()
  const [activeTab, setActiveTab] = useState<Tab>((searchParams.get('tab') as Tab) ?? 'outstanding')
  const [showPaymentModal, setShowPaymentModal] = useState(false)
  const [showClaimModal, setShowClaimModal] = useState(false)
  const [selectedClaims, setSelectedClaims] = useState<string[]>([])

  const { data: outstanding } = useOutstanding()
  const { data: claims } = useClaims()
  const { data: payments } = usePayments()
  const { data: revenue } = useRevenue()

  const tabs = [
    { id: 'outstanding', label: 'Outstanding', count: outstanding?.length },
    { id: 'claims', label: 'Claims', count: claims?.length },
    { id: 'payments', label: 'Payments', count: payments?.length },
    { id: 'revenue', label: 'Revenue', count: undefined },
  ] as const

  const claimStatusColors: Record<string, string> = {
    draft: 'bg-slate-100 text-slate-600',
    queued: 'bg-blue-50 text-blue-700',
    submitted: 'bg-amber-50 text-amber-700',
    acknowledged: 'bg-teal-50 text-teal-700',
    paid: 'bg-emerald-50 text-emerald-700',
    partial: 'bg-amber-50 text-amber-700',
    rejected: 'bg-rose-50 text-rose-700',
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div className="flex gap-1 bg-slate-100 rounded-lg p-0.5">
          {tabs.map((t) => (
            <button
              key={t.id}
              onClick={() => setActiveTab(t.id)}
              className={cn(
                "px-4 py-2 rounded-md text-sm font-medium transition-all flex items-center gap-2",
                activeTab === t.id ? "bg-white text-slate-700 shadow-sm" : "text-slate-500 hover:text-slate-700"
              )}
            >
              {t.label}
              {t.count !== undefined && (
                <span className="h-5 min-w-[1.25rem] flex items-center justify-center rounded-full bg-slate-200 text-[10px] font-bold text-slate-600 px-1.5">
                  {t.count}
                </span>
              )}
            </button>
          ))}
        </div>
        <div className="flex gap-2">
          {activeTab === 'outstanding' && (
            <Button onClick={() => setShowPaymentModal(true)}><BanknoteIcon className="mr-2 h-4 w-4" />Record Payment</Button>
          )}
          {activeTab === 'claims' && (
            <Button onClick={() => setShowClaimModal(true)}><PlusIcon className="mr-2 h-4 w-4" />New Claim</Button>
          )}
        </div>
      </div>

      {/* Outstanding */}
      {activeTab === 'outstanding' && (
        <Card>
          <Table>
            <TableHead>
              <tr>
                <TableHeader>Patient</TableHeader>
                <TableHeader>Total Due</TableHeader>
                <TableHeader>Last Payment</TableHeader>
                <TableHeader>Days Overdue</TableHeader>
                <TableHeader>Status</TableHeader>
                <TableHeader></TableHeader>
              </tr>
            </TableHead>
            <TableBody>
              {outstanding?.map((item: any) => (
                <TableRow key={item.patientId}>
                  <TableCell>
                    <div className="flex items-center gap-3">
                      <div className="h-8 w-8 rounded-full bg-slate-100 flex items-center justify-center text-xs font-bold text-slate-600">
                        {item.initials}
                      </div>
                      <span className="font-medium text-slate-700">{item.patientName}</span>
                    </div>
                  </TableCell>
                  <TableCell className="font-semibold text-slate-700">R {item.totalDue.toLocaleString()}</TableCell>
                  <TableCell>{item.lastPaymentDate ? formatDate(item.lastPaymentDate) : '—'}</TableCell>
                  <TableCell>{item.daysOverdue > 0 ? <span className="text-rose-600 font-medium">{item.daysOverdue}</span> : '—'}</TableCell>
                  <TableCell>
                    <Badge variant={item.daysOverdue > 14 ? 'danger' : item.daysOverdue > 0 ? 'warning' : 'default'}>
                      {item.daysOverdue > 14 ? 'Critical' : item.daysOverdue > 0 ? 'Overdue' : 'Current'}
                    </Badge>
                  </TableCell>
                  <TableCell>
                    <Button size="sm" variant="ghost" onClick={() => setShowPaymentModal(true)}>Record</Button>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </Card>
      )}

      {/* Claims */}
      {activeTab === 'claims' && (
        <Card>
          <div className="mb-4 flex items-center gap-3 px-5 pt-4">
            <Button
              size="sm"
              variant="secondary"
              disabled={selectedClaims.length === 0}
              onClick={() => {/* batch submit */}}
            >
              Batch Submit ({selectedClaims.length})
            </Button>
          </div>
          <Table>
            <TableHead>
              <tr>
                <TableHeader className="w-10">
                  <input
                    type="checkbox"
                    className="rounded border-slate-300"
                    onChange={(e) => setSelectedClaims(e.target.checked ? claims?.map((c: any) => c.id) ?? [] : [])}
                  />
                </TableHeader>
                <TableHeader>Patient</TableHeader>
                <TableHeader>Consultation</TableHeader>
                <TableHeader>Amount</TableHeader>
                <TableHeader>Status</TableHeader>
                <TableHeader>Reference</TableHeader>
                <TableHeader></TableHeader>
              </tr>
            </TableHead>
            <TableBody>
              {claims?.map((claim: any) => (
                <TableRow key={claim.id}>
                  <TableCell>
                    <input
                      type="checkbox"
                      className="rounded border-slate-300"
                      checked={selectedClaims.includes(claim.id)}
                      onChange={(e) => {
                        if (e.target.checked) setSelectedClaims([...selectedClaims, claim.id])
                        else setSelectedClaims(selectedClaims.filter(id => id !== claim.id))
                      }}
                    />
                  </TableCell>
                  <TableCell className="font-medium text-slate-700">{claim.patientName}</TableCell>
                  <TableCell className="text-sm text-slate-500">{formatDate(claim.consultationDate)}</TableCell>
                  <TableCell className="font-semibold">R {claim.totalAmount.toLocaleString()}</TableCell>
                  <TableCell>
                    <span className={cn("px-2 py-0.5 rounded-full text-xs font-medium", claimStatusColors[claim.status] ?? 'bg-slate-100 text-slate-600')}>
                      {claim.status}
                    </span>
                  </TableCell>
                  <TableCell className="text-sm text-slate-500">{claim.referenceNumber ?? '—'}</TableCell>
                  <TableCell>
                    <Button size="sm" variant="ghost">View</Button>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </Card>
      )}

      {/* Payments */}
      {activeTab === 'payments' && (
        <Card>
          <Table>
            <TableHead>
              <tr><TableHeader>Date</TableHeader><TableHeader>Patient</TableHeader><TableHeader>Method</TableHeader><TableHeader>Amount</TableHeader><TableHeader>Notes</TableHeader></tr>
            </TableHead>
            <TableBody>
              {payments?.map((p: any) => (
                <TableRow key={p.id}>
                  <TableCell>{formatDate(p.paymentDate)}</TableCell>
                  <TableCell className="font-medium">{p.patientName}</TableCell>
                  <TableCell><Badge variant="outline">{p.method}</Badge></TableCell>
                  <TableCell className="font-semibold text-emerald-600">R {p.amount.toLocaleString()}</TableCell>
                  <TableCell className="text-sm text-slate-500">{p.notes ?? '—'}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </Card>
      )}

      {/* Revenue */}
      {activeTab === 'revenue' && (
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          {revenue?.map((point: any) => (
            <Card key={point.period} className="p-5">
              <p className="text-sm text-slate-500">{point.period}</p>
              <p className="text-2xl font-bold text-slate-800 mt-1">R {point.total.toLocaleString()}</p>
              <div className="flex items-center gap-2 mt-2">
                <span className={cn("text-xs font-medium", point.change >= 0 ? "text-emerald-600" : "text-rose-600")}>
                  {point.change >= 0 ? '+' : ''}{point.change}%
                </span>
                <span className="text-xs text-slate-400">vs previous</span>
              </div>
            </Card>
          ))}
        </div>
      )}

      {/* Modals */}
      <PaymentModal open={showPaymentModal} onClose={() => setShowPaymentModal(false)} />
      <ClaimModal open={showClaimModal} onClose={() => setShowClaimModal(false)} />
    </div>
  )
}

function PaymentModal({ open, onClose }: { open: boolean; onClose: () => void }) {
  const [amount, setAmount] = useState('')
  const [method, setMethod] = useState('cash')
  const recordPayment = useRecordPayment()

  return (
    <Modal isOpen={open} onClose={onClose} title="Record Payment" footer={
      <>
        <Button variant="secondary" onClick={onClose}>Cancel</Button>
        <Button onClick={() => { recordPayment.mutate({ amount: parseFloat(amount), method }); onClose() }}>Record</Button>
      </>
    }>
      <div className="space-y-4">
        <Input label="Amount (R)" type="number" value={amount} onChange={e => setAmount(e.target.value)} />
        <div>
          <label className="mb-1.5 block text-sm font-medium text-slate-700">Method</label>
          <select className="w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm" value={method} onChange={e => setMethod(e.target.value)}>
            <option value="cash">Cash</option>
            <option value="card">Card</option>
            <option value="eft">EFT</option>
            <option value="medical_aid">Medical Aid</option>
          </select>
        </div>
      </div>
    </Modal>
  )
}

function ClaimModal({ open, onClose }: { open: boolean; onClose: () => void }) {
  return (
    <Modal isOpen={open} onClose={onClose} title="Create Claim" size="lg">
      <p className="text-sm text-slate-500">Select an unbilled consultation and link procedure codes from the NRPL catalogue.</p>
    </Modal>
  )
}
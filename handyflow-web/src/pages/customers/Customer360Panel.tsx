// src/pages/customers/Customer360Panel.tsx
import { useQuery } from '@tanstack/react-query'
import { Calendar, FileText, AlertCircle, TrendingUp, Clock } from 'lucide-react'
import { apiClient } from '../../api/client'

interface Summary360 {
  customerId:          string
  totalBookings:       number
  bookingsLast90Days:  number
  lastBookingAt:       string | null
  totalInvoices:       number
  totalInvoicedAmount: number
  overdueInvoices:     number
  outstandingAmount:   number
}

const fmt = (amount: number) =>
  new Intl.NumberFormat('en-ZA', { style: 'currency', currency: 'ZAR', minimumFractionDigits: 0 }).format(amount)

const fmtDate = (iso: string | null) =>
  iso
    ? new Date(iso).toLocaleDateString('en-ZA', { day: 'numeric', month: 'short', year: 'numeric' })
    : 'Never'

export function Customer360Panel({ customerId }: { customerId: string }) {
  const { data, isLoading, isError } = useQuery<Summary360>({
    queryKey: ['customer-360', customerId],
    queryFn: async () => {
      const res = await apiClient.get(`/api/v1/crm/customers/${customerId}/360`)
      // Backend wraps in ApiResponse<T>: { success, message, data: T }
      return res.data?.data ?? res.data
    },
    staleTime: 30_000,
    retry: false,    // don't retry — 360 is enrichment, silent fail is fine
  })

  if (isLoading) return (
    <div style={wrap}>
      <div style={sectionLabel}>Customer 360</div>
      <div style={{ color: '#94A3B8', fontSize: 13 }}>Loading…</div>
    </div>
  )

  // Silent fail — 360 is enrichment, not critical.
  // Hides if the endpoint isn't available yet or returns an error.
  if (isError || !data) return null

  const s          = data
  const hasOverdue = s.overdueInvoices > 0

  return (
    <div style={wrap}>
      <div style={sectionLabel}>Customer 360</div>
      <div style={grid}>
        <Stat
          icon={<Calendar size={15} />}
          label="Total bookings"
          value={String(s.totalBookings)}
          sub={`${s.bookingsLast90Days} in last 90 days`}
          color="#1D4ED8"
        />
        <Stat
          icon={<Clock size={15} />}
          label="Last booking"
          value={fmtDate(s.lastBookingAt)}
          color={s.lastBookingAt ? '#1D4ED8' : '#94A3B8'}
        />
        <Stat
          icon={<FileText size={15} />}
          label="Total invoiced"
          value={fmt(s.totalInvoicedAmount)}
          sub={`${s.totalInvoices} invoices`}
          color="#16A34A"
        />
        <Stat
          icon={hasOverdue ? <AlertCircle size={15} /> : <TrendingUp size={15} />}
          label={hasOverdue ? 'Overdue invoices' : 'Outstanding'}
          value={hasOverdue ? String(s.overdueInvoices) : fmt(s.outstandingAmount)}
          sub={hasOverdue ? fmt(s.outstandingAmount) + ' outstanding' : undefined}
          color={hasOverdue ? '#DC2626' : '#16A34A'}
          highlight={hasOverdue}
        />
      </div>
    </div>
  )
}

function Stat({ icon, label, value, sub, color, highlight = false }: {
  icon:       React.ReactNode
  label:      string
  value:      string
  sub?:       string
  color:      string
  highlight?: boolean
}) {
  return (
    <div style={{
      padding: '12px 14px',
      background:   highlight ? '#FEF2F2' : '#F8FAFC',
      border:       `1px solid ${highlight ? '#FECACA' : '#F1F5F9'}`,
      borderRadius: 10,
    }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 6, color }}>
        {icon}
        <span style={{ fontSize: 11, fontWeight: 700, textTransform: 'uppercase' as const, letterSpacing: '0.05em', color: '#94A3B8' }}>
          {label}
        </span>
      </div>
      <div style={{ fontSize: 17, fontWeight: 700, color: highlight ? '#DC2626' : '#0F172A' }}>
        {value}
      </div>
      {sub && <div style={{ fontSize: 11, color: '#94A3B8', marginTop: 3 }}>{sub}</div>}
    </div>
  )
}

const wrap: React.CSSProperties = {
  marginTop: 16,
  paddingTop: 16,
  borderTop: '1px solid #F1F5F9',
}

const sectionLabel: React.CSSProperties = {
  fontSize: 11, fontWeight: 700, color: '#94A3B8',
  textTransform: 'uppercase', letterSpacing: '0.06em', marginBottom: 10,
}

const grid: React.CSSProperties = {
  display: 'grid',
  gridTemplateColumns: '1fr 1fr',
  gap: 10,
}

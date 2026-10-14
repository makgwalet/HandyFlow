import { useState } from "react"
import { useMutation, useQuery } from "@tanstack/react-query"
import { AlertCircle, X } from "lucide-react"
import { apiClient } from "../../api/client"
import CustomerPicker from "./CustomerPicker"
import CollectorSelect from "./CollectorSelect"
import {
  debtorFrom, errorOf, money, newCaseProblem, owing, pickedTotal, rowsOf, unpaidInvoices,
  type Collector, type Customer, type InvoiceRow, type NewCaseMode, type OutstandingInvoice,
} from "./collections.logic"
import { ghostBtn, inp, lbl, pill, primaryBtn } from "./ui"

const day = (d: string | null) => (d ? new Date(d).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric" }) : "no due date")

/** Open a case by choosing a customer (all their unpaid invoices) or by ticking specific unpaid invoices. No IDs are typed. */
export default function NewCaseDialog({ onClose, onOpened }: { onClose: () => void; onOpened: () => void }) {
  const [mode, setMode] = useState<NewCaseMode>("customer")
  const [customer, setCustomer] = useState<Customer | null>(null)
  const [search, setSearch] = useState("")
  const [picked, setPicked] = useState<Set<string>>(new Set())
  const [debtor, setDebtor] = useState<{ name?: string; email?: string; phone?: string }>({})
  const [collectorId, setCollectorId] = useState("")
  const [collector, setCollector] = useState<Collector | null>(null)
  const [opened, setOpened] = useState(new Date().toISOString().split("T")[0])
  const [notes, setNotes] = useState("")
  const [error, setError] = useState("")

  const { data: outstanding = [], isFetching: loadingOutstanding } = useQuery<OutstandingInvoice[]>({
    queryKey: ["dc-outstanding", customer?.id],
    enabled: mode === "customer" && !!customer,
    queryFn: async () => rowsOf<OutstandingInvoice>(await apiClient.get(`/api/v1/debtcollection/cases/outstanding-invoices?customerId=${customer!.id}`)),
  })
  const { data: allInvoices = [], isLoading: loadingInvoices } = useQuery<InvoiceRow[]>({
    queryKey: ["dc-unpaid-invoices"],
    enabled: mode === "invoices",
    queryFn: async () => rowsOf<InvoiceRow>(await apiClient.get("/api/v1/invoicing/invoices?size=200")),
  })

  const candidates = unpaidInvoices(allInvoices, search, customer ? { [customer.id]: customer.name } : {})
    .filter(i => !customer || i.customerId === customer.id)
  const pickedRows = allInvoices.filter(i => picked.has(i.id))
  const known = debtorFrom(customer, pickedRows)
  const name = debtor.name ?? known.name, email = debtor.email ?? known.email, phone = debtor.phone ?? known.phone
  const problem = newCaseProblem(mode, customer, outstanding, picked.size, name)

  const done = { onSuccess: () => { onOpened(); onClose() }, onError: (e: any) => setError(errorOf(e, "Failed to open case")) }
  const openForCustomer = useMutation({ mutationFn: (b: any) => apiClient.post("/api/v1/debtcollection/cases/open-for-customer", b), ...done })
  const openManual = useMutation({ mutationFn: (b: any) => apiClient.post("/api/v1/debtcollection/cases", b), ...done })

  const submit = () => {
    if (problem) { setError(problem); return }
    const common = { openedDate: opened || null, assignedToUserId: collectorId || null, assignedToUserName: collector?.userName ?? null, notes: notes || null }
    if (mode === "customer") openForCustomer.mutate({ customerId: customer!.id, ...common })
    else openManual.mutate({ customerId: customer?.id ?? null, debtorName: name.trim(), debtorEmail: email || null, debtorPhone: phone || null, invoiceIds: [...picked], ...common })
  }
  const toggle = (id: string) => setPicked(p => { const n = new Set(p); n.has(id) ? n.delete(id) : n.add(id); return n })
  const switchMode = (m: NewCaseMode) => { setMode(m); setError(""); setPicked(new Set()); setDebtor({}) }
  const busy = openForCustomer.isPending || openManual.isPending
  const customerTotal = outstanding.reduce((t, i) => t + i.outstanding, 0)

  return (
    <div role="dialog" aria-label="Open case" style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000, backdropFilter: "blur(2px)" }}>
      <div style={{ background: "var(--hf-surface)", borderRadius: 16, padding: 26, width: 680, maxWidth: "94vw", maxHeight: "92vh", overflowY: "auto", boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 16 }}>
          <h3 style={{ margin: 0, fontSize: 18, fontWeight: 700, color: "var(--hf-text)" }}>Open a collection case</h3>
          <button aria-label="Close" onClick={onClose} style={{ background: "none", border: "none", cursor: "pointer", color: "var(--hf-text-faint)", display: "flex" }}><X size={20} /></button>
        </div>

        <div style={{ display: "flex", gap: 8, marginBottom: 18 }}>
          <button style={pill(mode === "customer")} onClick={() => switchMode("customer")}>All of a customer's unpaid invoices</button>
          <button style={pill(mode === "invoices")} onClick={() => switchMode("invoices")}>Pick specific invoices</button>
        </div>

        <div style={{ marginBottom: 16 }}>
          <CustomerPicker value={customer} onChange={c => { setCustomer(c); setPicked(new Set()); setDebtor({}) }} optional={mode === "invoices"}
            label={mode === "customer" ? "Customer" : "Customer (narrows the invoice list)"} />
        </div>

        {mode === "customer" && customer && (
          <div style={{ marginBottom: 16 }}>
            <div style={{ fontSize: 13, fontWeight: 700, color: "var(--hf-text)", marginBottom: 6 }}>
              Unpaid invoices that will go into the case{outstanding.length > 0 && ` · ${money(customerTotal)}`}
            </div>
            {loadingOutstanding ? <div style={{ fontSize: 13, color: "var(--hf-text-faint)" }}>Loading…</div>
              : outstanding.length === 0 ? <div style={{ fontSize: 13, color: "var(--hf-text-faint)" }}>This customer has no outstanding invoices.</div>
              : (
                <div style={{ border: "1px solid var(--hf-border)", borderRadius: 8, maxHeight: 190, overflowY: "auto" }}>
                  {outstanding.map(i => (
                    <div key={i.id} style={{ display: "flex", gap: 10, padding: "8px 12px", fontSize: 13, borderBottom: "1px solid var(--hf-border)" }}>
                      <b>{i.invoiceNumber}</b><span style={{ color: "var(--hf-text-faint)" }}>Due {day(i.dueDate)}</span><span style={{ marginLeft: "auto", fontWeight: 700 }}>{money(i.outstanding)}</span>
                    </div>
                  ))}
                </div>
              )}
          </div>
        )}

        {mode === "invoices" && (
          <div style={{ marginBottom: 16 }}>
            <label style={lbl} htmlFor="dc-invoice-search">Unpaid invoices{picked.size > 0 && ` · ${picked.size} ticked · ${money(pickedTotal(allInvoices, picked))}`}</label>
            <input id="dc-invoice-search" value={search} onChange={e => setSearch(e.target.value)} placeholder="Search by invoice number or client name" style={{ ...inp, marginBottom: 8 }} />
            <div style={{ border: "1px solid var(--hf-border)", borderRadius: 8, maxHeight: 210, overflowY: "auto" }}>
              {loadingInvoices && <div style={{ padding: 12, fontSize: 13, color: "var(--hf-text-faint)" }}>Loading…</div>}
              {!loadingInvoices && candidates.length === 0 && <div style={{ padding: 12, fontSize: 13, color: "var(--hf-text-faint)" }}>No unpaid invoices found.</div>}
              {candidates.map(i => (
                <label key={i.id} style={{ display: "flex", alignItems: "center", gap: 10, padding: "8px 12px", fontSize: 13, cursor: "pointer", borderBottom: "1px solid var(--hf-border)" }}>
                  <input type="checkbox" checked={picked.has(i.id)} onChange={() => toggle(i.id)} aria-label={`Invoice ${i.invoiceNumber}`} />
                  <b>{i.invoiceNumber}</b>
                  {i.walkinClientName && <span style={{ color: "var(--hf-text-muted)" }}>{i.walkinClientName}</span>}
                  <span style={{ color: "var(--hf-text-faint)" }}>Due {day(i.dueDate)}</span>
                  <span style={{ marginLeft: "auto", fontWeight: 700 }}>{money(owing(i))}</span>
                </label>
              ))}
            </div>
          </div>
        )}

        {mode === "invoices" && picked.size > 0 && (
          <div style={{ marginBottom: 16 }}>
            <div style={{ marginBottom: 12 }}><label style={lbl} htmlFor="dc-debtor-name">Debtor name</label><input id="dc-debtor-name" value={name} onChange={e => setDebtor(d => ({ ...d, name: e.target.value }))} style={inp} /></div>
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
              <div><label style={lbl} htmlFor="dc-debtor-email">Email</label><input id="dc-debtor-email" type="email" value={email} onChange={e => setDebtor(d => ({ ...d, email: e.target.value }))} style={inp} /></div>
              <div><label style={lbl} htmlFor="dc-debtor-phone">Phone</label><input id="dc-debtor-phone" value={phone} onChange={e => setDebtor(d => ({ ...d, phone: e.target.value }))} style={inp} /></div>
            </div>
          </div>
        )}

        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14, marginBottom: 14 }}>
          <CollectorSelect value={collectorId} onChange={(id, c) => { setCollectorId(id); setCollector(c) }} />
          <div><label style={lbl} htmlFor="dc-opened">Opened on</label><input id="dc-opened" type="date" value={opened} onChange={e => setOpened(e.target.value)} style={inp} /></div>
        </div>
        <div><label style={lbl} htmlFor="dc-notes">Notes</label><textarea id="dc-notes" value={notes} onChange={e => setNotes(e.target.value)} rows={2} style={{ ...inp, resize: "vertical" }} /></div>

        {error && <div role="alert" style={{ marginTop: 14, padding: "10px 12px", background: "var(--hf-danger-soft)", border: "1px solid var(--hf-danger-border)", borderRadius: 8, fontSize: 13, color: "var(--hf-danger-text)", display: "flex", gap: 8, alignItems: "center" }}><AlertCircle size={14} />{error}</div>}

        <div style={{ display: "flex", gap: 10, justifyContent: "flex-end", marginTop: 20, alignItems: "center" }}>
          {problem && <span style={{ fontSize: 12, color: "var(--hf-text-faint)", marginRight: "auto" }}>{problem}</span>}
          <button onClick={onClose} style={ghostBtn}>Cancel</button>
          <button onClick={submit} disabled={!!problem || busy} style={primaryBtn(!!problem || busy)}>{busy ? "Opening…" : "Open case"}</button>
        </div>
      </div>
    </div>
  )
}

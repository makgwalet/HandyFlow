// Company details, VAT, payment terms and the ledger accounts that invoices and payments post to.
import { useEffect, useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { toast } from "../../store/toast.store"
import { Btn, Empty, ErrorLine, Field, SectionTitle, card, errorText, inp, muted } from "./kit"
import { Input } from "../insurance/parts"
import { BASE } from "./lookups"
import { emptyProfile, missingLedgerCodes, profileBody, profileForm, profileProblem, type ProfileData, type ProfileForm } from "./fm.logic"

interface Account { accountCode: string; accountName: string }
const unwrap = (r: any) => r.data?.data ?? r.data
const KEY = ["fm-profile"]

/** A ledger account picker; falls back to a plain code box when the chart of accounts cannot be read. */
function AccountPick({ label, hint, value, onChange, accounts, disabled }: { label: string; hint?: string; value: string; onChange: (v: string) => void; accounts: Account[] | null; disabled: boolean }) {
  if (!accounts) return <Input label={label} hint={hint} value={value} onChange={onChange} disabled={disabled} maxLength={20} />
  const known = accounts.some(a => a.accountCode === value)
  return (
    <Field label={label} hint={hint}>
      <select value={value} onChange={e => onChange(e.target.value)} disabled={disabled} style={inp}>
        <option value="">Not set</option>
        {!known && value && <option value={value}>{value}</option>}
        {accounts.map(a => <option key={a.accountCode} value={a.accountCode}>{a.accountCode} — {a.accountName}</option>)}
      </select>
    </Field>
  )
}

export default function ProfileTab() {
  const qc = useQueryClient()
  const canEdit = usePermission("FACILITIESMANAGEMENT_ADMIN") || usePermission("FACILITIESMANAGEMENT_MANAGE")
  const prof = useQuery({
    queryKey: KEY, retry: false,
    queryFn: async () => {
      try { return unwrap(await apiClient.get<ProfileData>(`${BASE}/profile`)) as ProfileData }
      catch (e: any) { if (e?.response?.status === 404) return null; throw e }
    },
  })
  const accts = useQuery({ queryKey: ["fm-profile-accounts"], retry: false, queryFn: async () => (unwrap(await apiClient.get("/api/v1/accounting/accounts")) ?? []) as Account[] })
  const [f, setF] = useState<ProfileForm>(emptyProfile()), [err, setErr] = useState("")
  useEffect(() => { if (prof.data) setF(profileForm(prof.data)) }, [prof.data])
  const set = <K extends keyof ProfileForm>(k: K, v: ProfileForm[K]) => setF(s => ({ ...s, [k]: v }))
  const save = useMutation({
    mutationFn: () => apiClient.put(`${BASE}/profile`, profileBody(f)),
    onSuccess: () => { toast.success("Company and billing settings saved"); qc.invalidateQueries({ queryKey: KEY }) },
    onError: async e => setErr(await errorText(e, "Could not save the settings")),
  })
  if (prof.isError) return <Empty>Could not load the settings. Please try again.</Empty>
  if (prof.isLoading) return <Empty>Loading…</Empty>
  const problem = profileProblem(f), missing = missingLedgerCodes(f), accounts = accts.isSuccess ? accts.data : null
  const grid = { display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(220px, 1fr))", gap: "0 12px" } as const
  return (
    <div>
      <SectionTitle>Company and billing</SectionTitle>
      {prof.data === null && <div style={{ ...card, marginBottom: 12 }}>Your company is not set up yet. {canEdit ? "Fill this in once and invoices can be prepared and posted." : "Ask an administrator to set it up."}</div>}
      {prof.data !== null && missing.length > 0 && (
        <div style={{ ...card, marginBottom: 12, borderColor: "var(--hf-warning-text, #b54708)" }}>
          <b>Ledger accounts missing: {missing.join(", ")}.</b> <span style={muted}>{missing.includes("bank") ? "Payments cannot be recorded in the ledger until a bank account is chosen. " : ""}Invoices and payments are posted to these accounts.</span>
        </div>)}
      <div style={{ ...card, marginBottom: 16 }}>
        <div style={{ fontWeight: 700, marginBottom: 8 }}>Company</div>
        <div style={grid}>
          <Input label="Company name *" value={f.companyName} onChange={v => set("companyName", v)} disabled={!canEdit} maxLength={200} />
          <Input label="Registration number" value={f.registrationNumber} onChange={v => set("registrationNumber", v)} disabled={!canEdit} maxLength={50} />
          <Input label="Email" type="email" value={f.contactEmail} onChange={v => set("contactEmail", v)} disabled={!canEdit} />
          <Input label="Phone" value={f.contactPhone} onChange={v => set("contactPhone", v)} disabled={!canEdit} maxLength={30} />
        </div>
        <Field label="Address"><textarea value={f.address} onChange={e => set("address", e.target.value)} disabled={!canEdit} rows={2} style={inp} /></Field>
      </div>
      <div style={{ ...card, marginBottom: 16 }}>
        <div style={{ fontWeight: 700, marginBottom: 8 }}>VAT and payment terms</div>
        <label style={{ display: "flex", gap: 8, alignItems: "center", fontSize: 13, marginBottom: 10 }}>
          <input type="checkbox" checked={f.vatRegistered} disabled={!canEdit} onChange={e => set("vatRegistered", e.target.checked)} />We are registered for VAT
        </label>
        <div style={grid}>
          {f.vatRegistered && <Input label="VAT number *" value={f.vatNumber} onChange={v => set("vatNumber", v)} disabled={!canEdit} hint="10 digits, starting with 4" maxLength={14} />}
          <Input label="VAT rate (%)" type="number" value={f.vatRate} onChange={v => set("vatRate", v)} disabled={!canEdit} min={0} max={100} step="0.01" />
          <Input label="Payment terms (days)" type="number" value={f.paymentTermsDays} onChange={v => set("paymentTermsDays", v)} disabled={!canEdit} min={0} max={365} step={1} hint="Used to set the due date on new invoices" />
        </div>
      </div>
      <div style={{ ...card, marginBottom: 16 }}>
        <div style={{ fontWeight: 700, marginBottom: 2 }}>Ledger accounts</div>
        <div style={{ ...muted, marginBottom: 8 }}>{accounts ? "Pick from your chart of accounts." : "Your chart of accounts could not be read, so type the account codes."}</div>
        <div style={grid}>
          <AccountPick label="Debtors (receivables)" value={f.arAccountCode} onChange={v => set("arAccountCode", v)} accounts={accounts} disabled={!canEdit} hint="Money clients owe you" />
          <AccountPick label="Income (revenue)" value={f.revenueAccountCode} onChange={v => set("revenueAccountCode", v)} accounts={accounts} disabled={!canEdit} hint="Where invoiced work is recorded" />
          <AccountPick label={f.vatRegistered ? "VAT output *" : "VAT output"} value={f.vatAccountCode} onChange={v => set("vatAccountCode", v)} accounts={accounts} disabled={!canEdit} hint="VAT charged on invoices" />
          <AccountPick label="Bank" value={f.bankAccountCode} onChange={v => set("bankAccountCode", v)} accounts={accounts} disabled={!canEdit} hint="Where client payments are received" />
        </div>
      </div>
      <Field label="Notes"><textarea value={f.notes} onChange={e => set("notes", e.target.value)} disabled={!canEdit} rows={2} style={inp} /></Field>
      {err && <ErrorLine text={err} />}
      {canEdit
        ? <div><Btn kind="primary" disabled={!!problem || save.isPending} onClick={() => { setErr(""); save.mutate() }}>{save.isPending ? "Saving…" : "Save settings"}</Btn>{problem && <span style={{ ...muted, marginLeft: 10 }}>{problem}</span>}</div>
        : <div style={muted}>Only administrators and managers can change these.</div>}
    </div>
  )
}

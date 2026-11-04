// Outside contractors: who they are, whether they are approved, and whether their paperwork lets them be given work.
import { useRef, useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { toast } from "../../store/toast.store"
import { Btn, Dialog, Empty, ErrorLine, Input, Pager, Pick, Pill, ReasonDialog, Row, SectionTitle, card, day, errorText, fail, money, muted, pageOf, td, th } from "./kit"
import FmEvidencePanel, { DOC_KINDS, fileProblem, fileSize } from "./FmEvidencePanel"
import { BASE, type FVendor } from "./lookups"
import { DOC_TYPES, ONBOARDING, docBody, docNote, docProblem, emptyDoc, emptyVendor, labelOf, vendorActions, vendorBody, vendorProblem, type DocForm, type VendorForm } from "./fm.logic"

interface Vendor extends FVendor { contactName: string | null; contactPhone: string | null; contactEmail: string | null; notes: string | null }
interface Doc { id: string; docType: string; reference: string | null; issuedBy: string | null; issueDate: string | null; expiryDate: string | null; coverAmount: number | null; documentRef: string | null; status: string; inForce: boolean; revokedReason: string | null }
interface Compliance { complianceStatus: string; eligible: boolean; problems: string[]; missing: string[]; lapsed: string[]; expiring: { docType: string; expiryDate: string; daysLeft: number }[]; documents: Doc[] }

export default function VendorsTab() {
  const qc = useQueryClient()
  const canWrite = usePermission("FACILITIESMANAGEMENT_MANAGE") || usePermission("FACILITIESMANAGEMENT_ADMIN")
  const [page, setPage] = useState(0), [editing, setEditing] = useState<Vendor | "new" | null>(null), [open, setOpen] = useState<Vendor | null>(null)
  const { data, isLoading, isError } = useQuery({ queryKey: ["fm-vendors", page], queryFn: async () => pageOf<Vendor>(await apiClient.get(`${BASE}/vendors`, { params: { page, size: 25 } })) })
  const refresh = () => { qc.invalidateQueries({ queryKey: ["fm-vendors"] }); qc.invalidateQueries({ queryKey: ["fm-vendors-all"] }) }
  const toggle = useMutation({
    mutationFn: (v: Vendor) => apiClient.post(`${BASE}/vendors/${v.id}/${v.active ? "deactivate" : "reactivate"}`),
    onSuccess: refresh, onError: e => { void fail(e, "That did not work") },
  })
  const rows = data?.content ?? []
  return (
    <div>
      <SectionTitle right={canWrite && <Btn kind="primary" onClick={() => setEditing("new")}>Add vendor</Btn>}>Vendors</SectionTitle>
      <div style={{ ...card, padding: 0, overflowX: "auto" }}>
        {isError ? <Empty>Could not load the vendors. Please try again.</Empty> : isLoading ? <Empty>Loading…</Empty> : rows.length === 0 ? <Empty>No vendors yet.</Empty> : (
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead><tr><th style={th}>Vendor</th><th style={th}>Contact</th><th style={th}>Approval</th><th style={th}>Status</th><th /></tr></thead>
            <tbody>{rows.map(v => {
              const ob = ONBOARDING[v.onboardingStatus ?? ""] ?? { label: v.onboardingStatus ?? "", tone: "neutral" as const }
              return (
                <tr key={v.id} onClick={() => setOpen(v)} style={{ cursor: "pointer", opacity: v.active ? 1 : 0.6 }}>
                  <td style={td}><b>{v.companyName}</b><div style={muted}>{v.serviceType}</div></td>
                  <td style={td}>{v.contactName ?? "—"}<div style={muted}>{[v.contactPhone, v.contactEmail].filter(Boolean).join(" · ")}</div></td>
                  <td style={td}><Pill tone={ob.tone}>{ob.label}</Pill></td>
                  <td style={td}><Pill tone={v.active ? "success" : "neutral"}>{v.active ? "Active" : "Inactive"}</Pill></td>
                  <td style={{ ...td, textAlign: "right", whiteSpace: "nowrap" }} onClick={e => e.stopPropagation()}>{canWrite && <><Btn small onClick={() => setEditing(v)}>Edit</Btn>{" "}<Btn small disabled={toggle.isPending} onClick={() => toggle.mutate(v)}>{v.active ? "Deactivate" : "Reactivate"}</Btn></>}</td>
                </tr>)})}</tbody>
          </table>)}
      </div>
      <Pager page={page} pages={data?.totalPages ?? 0} onPage={setPage} />
      {open && <VendorDetail vendor={open} canWrite={canWrite} onClose={() => setOpen(null)} onChanged={v => { setOpen(v); refresh() }} />}
      {editing && <VendorDialog vendor={editing === "new" ? null : editing} onClose={() => setEditing(null)} onSaved={refresh} />}
    </div>
  )
}

function VendorDetail({ vendor, canWrite, onClose, onChanged }: { vendor: Vendor; canWrite: boolean; onClose: () => void; onChanged: (v: Vendor) => void }) {
  const qc = useQueryClient(), approver = usePermission("FACILITIESMANAGEMENT_VENDOR_APPROVE"), canApprove = canWrite || approver
  const [suspending, setSuspending] = useState(false), [adding, setAdding] = useState(false), [revoking, setRevoking] = useState<Doc | null>(null), [filesOf, setFilesOf] = useState<Doc | null>(null)
  const key = ["fm-vendor-compliance", vendor.id]
  const comp = useQuery({ queryKey: key, queryFn: async () => (await apiClient.get<Compliance>(`${BASE}/vendors/${vendor.id}/compliance`)).data })
  const ob = ONBOARDING[vendor.onboardingStatus ?? ""] ?? { label: vendor.onboardingStatus ?? "", tone: "neutral" as const }
  const approve = useMutation({
    mutationFn: async () => (await apiClient.post<Vendor>(`${BASE}/vendors/${vendor.id}/approve`)).data,
    onSuccess: v => { toast.success("Vendor approved"); onChanged({ ...vendor, ...(v ?? {}), onboardingStatus: "APPROVED" }); qc.invalidateQueries({ queryKey: key }) },
    onError: e => { void fail(e, "Could not approve that vendor") },
  })
  const c = comp.data, acts = vendorActions(vendor.onboardingStatus ?? "PENDING", canApprove), now = new Date()
  return (
    <Dialog title={<>{vendor.companyName} <Pill tone={ob.tone}>{ob.label}</Pill></>} onClose={onClose} width={720}
      footer={acts.length ? <>
        {acts.includes("suspend") && <Btn onClick={() => setSuspending(true)}>Suspend</Btn>}
        {acts.includes("approve") && <Btn kind="primary" disabled={approve.isPending} onClick={() => approve.mutate()}>{approve.isPending ? "Approving…" : "Approve"}</Btn>}
      </> : undefined}>
      <Row k="Service">{vendor.serviceType ?? ""}</Row>
      <Row k="Contact">{[vendor.contactName, vendor.contactPhone, vendor.contactEmail].filter(Boolean).join(" · ")}</Row>
      {vendor.notes && <Row k="Notes">{vendor.notes}</Row>}
      <div style={{ margin: "14px 0 6px", fontSize: 13, fontWeight: 700 }}>Can they be given work?</div>
      {comp.isError ? <div style={muted}>Could not load the compliance check.</div> : !c ? <div style={muted}>Loading…</div> : (
        <div style={{ ...card, fontSize: 13, lineHeight: 1.55 }}>
          <div>{c.eligible ? <Pill tone="success">Yes</Pill> : <Pill tone="danger">No</Pill>}</div>
          {c.problems.length > 0 && <ul style={{ margin: "8px 0 0", paddingLeft: 18 }}>{c.problems.map(p => <li key={p}>{p}</li>)}</ul>}
          {c.expiring.length > 0 && <div style={{ marginTop: 8 }}>Running out soon: {c.expiring.map(e => `${labelOf(DOC_TYPES, e.docType)} in ${e.daysLeft} d`).join(", ")}</div>}
        </div>)}
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", margin: "14px 0 6px" }}>
        <div style={{ fontSize: 13, fontWeight: 700 }}>Documents</div>
        {canWrite && <Btn small onClick={() => setAdding(true)}>Record a document</Btn>}
      </div>
      <div style={{ ...card, padding: 0, overflowX: "auto" }}>
        {(c?.documents ?? []).length === 0 ? <div style={{ ...muted, padding: 14 }}>{comp.isLoading ? "Loading…" : "No documents recorded."}</div> : (
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead><tr><th style={th}>Document</th><th style={th}>Expires</th><th style={th}>Standing</th><th /></tr></thead>
            <tbody>{c!.documents.map(d => {
              const n = docNote(d.status, d.inForce, d.expiryDate, now)
              return (
                <tr key={d.id}>
                  <td style={td}><b>{labelOf(DOC_TYPES, d.docType)}</b><div style={muted}>{[d.reference, d.issuedBy, d.coverAmount != null ? `Cover ${money(d.coverAmount)}` : null].filter(Boolean).join(" · ")}</div>{d.revokedReason && <div style={muted}>Revoked: {d.revokedReason}</div>}</td>
                  <td style={td}>{day(d.expiryDate)}</td>
                  <td style={td}><Pill tone={n.tone}>{n.text}</Pill></td>
                  <td style={{ ...td, textAlign: "right", whiteSpace: "nowrap" }}><Btn small onClick={() => setFilesOf(d)}>Files</Btn>{canWrite && d.status !== "REVOKED" && <>{" "}<Btn small onClick={() => setRevoking(d)}>Revoke</Btn></>}</td>
                </tr>)})}</tbody>
          </table>)}
      </div>
      {suspending && <ReasonDialog title="Suspend this vendor?" label="Why?" confirm="Suspend" danger intro="A suspended vendor cannot be given new work."
        onClose={() => setSuspending(false)} onSubmit={async t => { await apiClient.post(`${BASE}/vendors/${vendor.id}/suspend`, { reason: t }); toast.success("Vendor suspended"); onChanged({ ...vendor, onboardingStatus: "SUSPENDED" }); qc.invalidateQueries({ queryKey: key }) }} />}
      {revoking && <ReasonDialog title="Revoke this document?" label="Why?" confirm="Revoke" danger onClose={() => setRevoking(null)}
        onSubmit={async t => { await apiClient.post(`${BASE}/vendors/${vendor.id}/documents/${revoking.id}/revoke`, { reason: t }); toast.success("Document revoked"); qc.invalidateQueries({ queryKey: key }) }} />}
      {filesOf && (
        <Dialog title={`${labelOf(DOC_TYPES, filesOf.docType)} · files`} onClose={() => setFilesOf(null)} width={640}>
          <FmEvidencePanel owner="vendor-documents" id={filesOf.id} canWrite={canWrite} title="Files for this document" kinds={DOC_KINDS} defaultKind="DOCUMENT"
            path={`${BASE}/vendors/${vendor.id}/documents/${filesOf.id}/evidence`} />
        </Dialog>)}
      {adding && <DocDialog vendorId={vendor.id} onClose={() => setAdding(false)} onSaved={() => qc.invalidateQueries({ queryKey: key })} />}
    </Dialog>
  )
}

function DocDialog({ vendorId, onClose, onSaved }: { vendorId: string; onClose: () => void; onSaved: () => void }) {
  const [f, setF] = useState<DocForm>(emptyDoc()), [err, setErr] = useState(""), [file, setFile] = useState<File | null>(null), input = useRef<HTMLInputElement>(null)
  const set = (k: keyof DocForm) => (v: string) => setF(s => ({ ...s, [k]: v }))
  const problem = docProblem(f), fileBad = file ? fileProblem(file) : null
  const save = useMutation({
    mutationFn: async () => {
      const created = (await apiClient.post<{ id: string }>(`${BASE}/vendors/${vendorId}/documents`, docBody(f))).data
      if (!file) return true
      // The record exists now. If the file fails, say so plainly: the record stays, and Files on the document can try again.
      try {
        const fd = new FormData(); fd.append("file", file, file.name); fd.append("evidenceType", f.docType === "OTHER" ? "DOCUMENT" : "CERTIFICATE")
        await apiClient.post(`${BASE}/vendors/${vendorId}/documents/${created.id}/evidence`, fd)
        return true
      } catch { return false }
    },
    onSuccess: attached => { attached ? toast.success(file ? "Document recorded with its file" : "Document recorded") : toast.error("The document was recorded, but its file was not attached. Open Files on the document to try again."); onSaved(); onClose() },
    onError: async e => setErr(await errorText(e, "Could not record the document")),
  })
  return (
    <Dialog title="Record a document" onClose={onClose} width={600}
      footer={<><Btn onClick={onClose}>Cancel</Btn><Btn kind="primary" disabled={!!problem || !!fileBad || save.isPending} onClick={() => { setErr(""); save.mutate() }}>{save.isPending ? "Saving…" : "Record"}</Btn></>}>
      <Pick label="Kind" value={f.docType} onChange={set("docType")} options={DOC_TYPES} />
      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "0 12px" }}>
        <Input label="Reference number" value={f.reference} onChange={set("reference")} maxLength={120} />
        <Input label="Issued by" value={f.issuedBy} onChange={set("issuedBy")} maxLength={200} />
        <Input label="Issued" type="date" value={f.issueDate} onChange={set("issueDate")} />
        <Input label="Expires" type="date" value={f.expiryDate} onChange={set("expiryDate")} hint={f.docType === "OTHER" ? "Optional for this kind" : "Needed"} />
        {f.docType === "PUBLIC_LIABILITY_INSURANCE" && <Input label="Cover amount (R)" type="number" min="0" step="0.01" value={f.coverAmount} onChange={set("coverAmount")} />}
      </div>
      <div style={{ marginBottom: 12 }}>
        <div style={{ fontSize: 12, fontWeight: 600, marginBottom: 4 }}>The file (optional)</div>
        <input ref={input} type="file" aria-label="Document file" onChange={e => setFile(e.target.files?.[0] ?? null)} style={{ fontSize: 13 }} />
        {file && <div style={muted}>{file.name} · {fileSize(file.size)}{fileBad ? ` · ${fileBad}` : ""}</div>}
      </div>
      <Input label="Where else it is kept" hint="A note, folder or link, if you also keep it elsewhere" value={f.documentRef} onChange={set("documentRef")} maxLength={500} />
      {problem && (f.expiryDate || f.docType === "OTHER") && <div style={muted}>{problem}</div>}
      {err && <ErrorLine text={err} />}
    </Dialog>
  )
}

function VendorDialog({ vendor, onClose, onSaved }: { vendor: Vendor | null; onClose: () => void; onSaved: () => void }) {
  const [f, setF] = useState<VendorForm>(() => vendor ? { companyName: vendor.companyName, serviceType: vendor.serviceType ?? "", contactName: vendor.contactName ?? "", contactPhone: vendor.contactPhone ?? "", contactEmail: vendor.contactEmail ?? "", notes: vendor.notes ?? "" } : emptyVendor())
  const [err, setErr] = useState("")
  const set = (k: keyof VendorForm) => (v: string) => setF(s => ({ ...s, [k]: v }))
  const problem = vendorProblem(f)
  const save = useMutation({
    mutationFn: () => vendor ? apiClient.put(`${BASE}/vendors/${vendor.id}`, vendorBody(f)) : apiClient.post(`${BASE}/vendors`, vendorBody(f)),
    onSuccess: () => { toast.success(vendor ? "Vendor saved" : "Vendor added"); onSaved(); onClose() },
    onError: async e => setErr(await errorText(e, "Could not save the vendor")),
  })
  return (
    <Dialog title={vendor ? `Edit ${vendor.companyName}` : "Add a vendor"} onClose={onClose}
      footer={<><Btn onClick={onClose}>Cancel</Btn><Btn kind="primary" disabled={!!problem || save.isPending} onClick={() => { setErr(""); save.mutate() }}>{save.isPending ? "Saving…" : "Save"}</Btn></>}>
      {!vendor && <div style={{ ...muted, marginBottom: 10 }}>A new vendor has to be approved before it can be given work.</div>}
      <Input label="Company name *" value={f.companyName} onChange={set("companyName")} autoFocus maxLength={255} />
      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "0 12px" }}>
        <Input label="Kind of service" value={f.serviceType} onChange={set("serviceType")} />
        <Input label="Contact person" value={f.contactName} onChange={set("contactName")} />
        <Input label="Phone" value={f.contactPhone} onChange={set("contactPhone")} />
        <Input label="Email" type="email" value={f.contactEmail} onChange={set("contactEmail")} />
      </div>
      <Input label="Notes" value={f.notes} onChange={set("notes")} />
      {problem && f.companyName.trim() && <div style={muted}>{problem}</div>}
      {err && <ErrorLine text={err} />}
    </Dialog>
  )
}

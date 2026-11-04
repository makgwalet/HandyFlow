// The firm's own details, printed on invoices, and the two bank accounts. Trust money and business money live in different accounts.
import { useEffect, useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { toast } from "../../store/toast.store"
import { Btn, ErrorLine, Input, SectionTitle, errorText, muted } from "./kit"
import { BASE } from "./lookups"
import { emptyProfile, profileProblem, profileRequest, type ProfileForm } from "./legal.logic"

export default function ProfileTab() {
  const qc = useQueryClient()
  const canEdit = usePermission("LEGALPRACTICE_MANAGE") || usePermission("LEGALPRACTICE_ADMIN")
  const [f, setF] = useState<ProfileForm>(emptyProfile), [err, setErr] = useState("")
  // A firm that has not saved a profile yet gets a 404; that means "empty form", not an error.
  const { data, isLoading } = useQuery<ProfileForm | null>({
    queryKey: ["lp-profile"], retry: false,
    queryFn: async () => { try { const r: any = await apiClient.get(`${BASE}/profile`); return (r?.data?.data ?? r?.data) as ProfileForm } catch (e: any) { if (e?.response?.status === 404) return null; throw e } },
  })
  useEffect(() => { if (data) setF(Object.fromEntries(Object.keys(emptyProfile()).map(k => [k, (data as any)[k] ?? ""])) as unknown as ProfileForm) }, [data])
  const set = (k: keyof ProfileForm) => (v: string) => setF(s => ({ ...s, [k]: v }))
  const problem = profileProblem(f)
  const save = useMutation({
    mutationFn: () => apiClient.put(`${BASE}/profile`, profileRequest(f)),
    onSuccess: () => { toast.success("Firm details saved"); setErr(""); qc.invalidateQueries({ queryKey: ["lp-profile"] }) },
    onError: async e => setErr(await errorText(e, "Could not save the details")),
  })
  const d = !canEdit
  return (
    <div style={{ maxWidth: 640 }}>
      <SectionTitle>Firm details</SectionTitle>
      {isLoading ? <div style={muted}>Loading…</div> : (<>
        {!data && <div style={{ ...muted, marginBottom: 12 }}>No details saved yet.{canEdit ? " Add them so invoices carry your firm's details." : ""}</div>}
        <fieldset disabled={d} style={{ border: 0, padding: 0, margin: 0 }}>
          <Input label="Firm name" value={f.firmName} onChange={set("firmName")} maxLength={255} />
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "0 12px" }}>
            <Input label="Practice number" value={f.practiceNumber} onChange={set("practiceNumber")} />
            <Input label="VAT number" value={f.vatNumber} onChange={set("vatNumber")} />
            <Input label="Contact email" type="email" value={f.contactEmail} onChange={set("contactEmail")} />
            <Input label="Contact phone" value={f.contactPhone} onChange={set("contactPhone")} />
          </div>
          <div style={{ fontSize: 13, fontWeight: 700, margin: "14px 0 6px" }}>Trust account</div>
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "0 12px" }}>
            <Input label="Bank" value={f.trustBankName} onChange={set("trustBankName")} />
            <Input label="Account number" value={f.trustAccountNumber} onChange={set("trustAccountNumber")} />
          </div>
          <div style={{ fontSize: 13, fontWeight: 700, margin: "6px 0 6px" }}>Business account</div>
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "0 12px" }}>
            <Input label="Bank" value={f.businessBankName} onChange={set("businessBankName")} />
            <Input label="Account number" value={f.businessAccountNumber} onChange={set("businessAccountNumber")} />
          </div>
        </fieldset>
        {problem && f.firmName.trim() && <div style={muted}>{problem}</div>}
        {err && <ErrorLine text={err} />}
        {canEdit && <div style={{ marginTop: 10 }}><Btn kind="primary" disabled={!!problem || save.isPending} onClick={() => { setErr(""); save.mutate() }}>{save.isPending ? "Saving…" : "Save details"}</Btn></div>}
      </>)}
    </div>
  )
}

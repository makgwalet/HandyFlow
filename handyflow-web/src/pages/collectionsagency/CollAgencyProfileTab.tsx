// src/pages/collectionsagency/CollAgencyProfileTab.tsx
import { useState, useEffect } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { CA_ACCENT } from "./constants"

interface ProfileResponse {
  id: string | null; agencyName: string | null; firmRegistrationNumber: string | null
  firmRegistrationExpiryDate: string | null; defaultCommissionPct: number | null
  contactEmail: string | null; contactPhone: string | null; physicalAddress: string | null
}

const inputStyle: React.CSSProperties = { width: "100%", padding: "9px 12px", border: "1px solid #E2E8F0", borderRadius: 8, fontSize: 13, boxSizing: "border-box", fontFamily: "inherit" }
const labelStyle: React.CSSProperties = { fontSize: 12, fontWeight: 600, color: "#374151", marginBottom: 5, display: "block" }

export default function CollAgencyProfileTab() {
  const qc = useQueryClient()
  const { data, isLoading } = useQuery<ProfileResponse | null>({
    queryKey: ["ca-profile"],
    queryFn: async () => (await apiClient.get("/api/v1/collections-agency/profile")).data,
  })

  const [form, setForm] = useState({
    agencyName: "", firmRegistrationNumber: "", firmRegistrationExpiryDate: "",
    defaultCommissionPct: 15, contactEmail: "", contactPhone: "", physicalAddress: "",
  })

  useEffect(() => {
    if (data) {
      setForm({
        agencyName: data.agencyName ?? "", firmRegistrationNumber: data.firmRegistrationNumber ?? "",
        firmRegistrationExpiryDate: data.firmRegistrationExpiryDate ?? "", defaultCommissionPct: data.defaultCommissionPct ?? 15,
        contactEmail: data.contactEmail ?? "", contactPhone: data.contactPhone ?? "", physicalAddress: data.physicalAddress ?? "",
      })
    }
  }, [data])

  const save = useMutation({
    mutationFn: async () => apiClient.put("/api/v1/collections-agency/profile", form),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["ca-profile"] }),
  })

  if (isLoading) return <p style={{ color: "#94A3B8", fontSize: 13 }}>Loading…</p>

  return (
    <div style={{ maxWidth: 560 }}>
      <p style={{ fontSize: 12, color: "#94A3B8", marginBottom: 20 }}>
        The agency's own practice profile, including its firm Debt Collectors Act registration — tracked separately from each individual collector's own registration (see the Collectors tab).
      </p>
      <div style={{ display: "grid", gap: 14 }}>
        <div><label style={labelStyle}>Agency name *</label><input style={inputStyle} value={form.agencyName} onChange={e => setForm({ ...form, agencyName: e.target.value })} /></div>
        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
          <div><label style={labelStyle}>Firm registration number</label><input style={inputStyle} value={form.firmRegistrationNumber} onChange={e => setForm({ ...form, firmRegistrationNumber: e.target.value })} /></div>
          <div><label style={labelStyle}>Firm registration expiry</label><input type="date" style={inputStyle} value={form.firmRegistrationExpiryDate} onChange={e => setForm({ ...form, firmRegistrationExpiryDate: e.target.value })} /></div>
        </div>
        <div>
          <label style={labelStyle}>Default commission rate (%) *</label>
          <input type="number" step="0.5" min="0" max="100" style={{ ...inputStyle, maxWidth: 160 }} value={form.defaultCommissionPct}
            onChange={e => setForm({ ...form, defaultCommissionPct: parseFloat(e.target.value) || 0 })} />
          <p style={{ fontSize: 11, color: "#94A3B8", margin: "4px 0 0" }}>Used when a client doesn't have its own commission rate override.</p>
        </div>
        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
          <div><label style={labelStyle}>Contact email</label><input type="email" style={inputStyle} value={form.contactEmail} onChange={e => setForm({ ...form, contactEmail: e.target.value })} /></div>
          <div><label style={labelStyle}>Contact phone</label><input style={inputStyle} value={form.contactPhone} onChange={e => setForm({ ...form, contactPhone: e.target.value })} /></div>
        </div>
        <div><label style={labelStyle}>Physical address</label><textarea style={{ ...inputStyle, minHeight: 70, resize: "vertical" }} value={form.physicalAddress} onChange={e => setForm({ ...form, physicalAddress: e.target.value })} /></div>
      </div>

      {save.isError && <p style={{ color: "#DC2626", fontSize: 12, marginTop: 12 }}>{(save.error as any)?.response?.data?.message ?? "Could not save the profile"}</p>}
      {save.isSuccess && <p style={{ color: "#059669", fontSize: 12, marginTop: 12 }}>Profile saved.</p>}

      <button onClick={() => save.mutate()} disabled={!form.agencyName || save.isPending}
        style={{ marginTop: 20, padding: "10px 20px", borderRadius: 8, border: "none", background: CA_ACCENT, color: "#fff", fontSize: 13, fontWeight: 600, cursor: "pointer" }}>
        {save.isPending ? "Saving…" : "Save profile"}
      </button>
    </div>
  )
}

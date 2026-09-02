// src/pages/trainingprovider/TrainProvProfileTab.tsx
//
// The provider's own accreditation profile — confirmed via
// TrainProvController: GET/PUT /api/v1/training-provider/profile.
// ProfileResponse(id, tradingName, registrationNumber, accreditationBody,
// accreditationNumber, accreditationExpiry, address, phone, email, logoUrl).
// accreditationExpiry feeds TrainProvNotificationScheduler's daily sweep
// (accreditation expiring soon) — keeping it current here matters.
import { useState, useEffect } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { TRAINPROV_ACCENT } from "./constants"

interface ProfileResponse {
  id: string | null; tradingName: string | null; registrationNumber: string | null
  accreditationBody: string | null; accreditationNumber: string | null; accreditationExpiry: string | null
  address: string | null; phone: string | null; email: string | null; logoUrl: string | null
}

const inputStyle: React.CSSProperties = { width: "100%", padding: "9px 12px", border: "1px solid #E2E8F0", borderRadius: 8, fontSize: 13, boxSizing: "border-box", fontFamily: "inherit" }
const labelStyle: React.CSSProperties = { fontSize: 12, fontWeight: 600, color: "#374151", marginBottom: 5, display: "block" }

export default function TrainProvProfileTab() {
  const qc = useQueryClient()
  const { data, isLoading } = useQuery<ProfileResponse | null>({
    queryKey: ["trainprov-profile"],
    queryFn: async () => (await apiClient.get("/api/v1/training-provider/profile")).data,
  })

  const [form, setForm] = useState({
    tradingName: "", registrationNumber: "", accreditationBody: "", accreditationNumber: "",
    accreditationExpiry: "", address: "", phone: "", email: "",
  })

  useEffect(() => {
    if (data) {
      setForm({
        tradingName: data.tradingName ?? "", registrationNumber: data.registrationNumber ?? "",
        accreditationBody: data.accreditationBody ?? "", accreditationNumber: data.accreditationNumber ?? "",
        accreditationExpiry: data.accreditationExpiry ?? "", address: data.address ?? "",
        phone: data.phone ?? "", email: data.email ?? "",
      })
    }
  }, [data])

  const save = useMutation({
    mutationFn: async () => apiClient.put("/api/v1/training-provider/profile", form),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["trainprov-profile"] }),
  })

  if (isLoading) return <p style={{ color: "#94A3B8", fontSize: 13 }}>Loading…</p>

  return (
    <div style={{ maxWidth: 560 }}>
      <p style={{ fontSize: 12, color: "#94A3B8", marginBottom: 20 }}>
        Your training academy's own accreditation profile. The accreditation expiry date is watched by a daily notification sweep — keep it current.
      </p>
      <div style={{ display: "grid", gap: 14 }}>
        <div><label style={labelStyle}>Trading name *</label><input style={inputStyle} value={form.tradingName} onChange={e => setForm({ ...form, tradingName: e.target.value })} /></div>
        <div><label style={labelStyle}>Registration number</label><input style={inputStyle} value={form.registrationNumber} onChange={e => setForm({ ...form, registrationNumber: e.target.value })} /></div>
        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
          <div><label style={labelStyle}>Accreditation body</label><input style={inputStyle} value={form.accreditationBody} onChange={e => setForm({ ...form, accreditationBody: e.target.value })} placeholder="e.g. QCTO, SETA name" /></div>
          <div><label style={labelStyle}>Accreditation number</label><input style={inputStyle} value={form.accreditationNumber} onChange={e => setForm({ ...form, accreditationNumber: e.target.value })} /></div>
        </div>
        <div><label style={labelStyle}>Accreditation expiry</label><input type="date" style={{ ...inputStyle, maxWidth: 220 }} value={form.accreditationExpiry} onChange={e => setForm({ ...form, accreditationExpiry: e.target.value })} /></div>
        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
          <div><label style={labelStyle}>Contact email</label><input type="email" style={inputStyle} value={form.email} onChange={e => setForm({ ...form, email: e.target.value })} /></div>
          <div><label style={labelStyle}>Contact phone</label><input style={inputStyle} value={form.phone} onChange={e => setForm({ ...form, phone: e.target.value })} /></div>
        </div>
        <div><label style={labelStyle}>Address</label><textarea style={{ ...inputStyle, minHeight: 70, resize: "vertical" }} value={form.address} onChange={e => setForm({ ...form, address: e.target.value })} /></div>
      </div>

      {save.isError && <p style={{ color: "#DC2626", fontSize: 12, marginTop: 12 }}>{(save.error as any)?.response?.data?.message ?? "Could not save the profile"}</p>}
      {save.isSuccess && <p style={{ color: "#059669", fontSize: 12, marginTop: 12 }}>Profile saved.</p>}

      <button onClick={() => save.mutate()} disabled={!form.tradingName || save.isPending}
        style={{ marginTop: 20, padding: "10px 20px", borderRadius: 8, border: "none", background: TRAINPROV_ACCENT, color: "#fff", fontSize: 13, fontWeight: 600, cursor: "pointer" }}>
        {save.isPending ? "Saving…" : "Save profile"}
      </button>
    </div>
  )
}

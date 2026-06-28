// src/pages/bookings/AvailabilityTab.tsx
//
// CHANGES vs original:
// - TIME_SLOTS now covers 05:00–22:00 (35 slots × 30min)
//   WHY: The original only covered 07:00–21:00. SA service businesses include
//   early-morning gym trainers (05:30 sessions), late-evening restaurants, and
//   event photographers who need 21:00+ slots. Covering 05:00–22:00 handles all
//   realistic cases without being an infinite list.
// - CSS Modules replaced with inline styles consistent with the rest of the module
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { Plus, X, AlertCircle, CheckCircle } from "lucide-react"

const DAYS = [
  { value: 1, label: "Monday" },
  { value: 2, label: "Tuesday" },
  { value: 3, label: "Wednesday" },
  { value: 4, label: "Thursday" },
  { value: 5, label: "Friday" },
  { value: 6, label: "Saturday" },
  { value: 0, label: "Sunday" },
]

// 05:00 to 22:00 in 30-minute steps = 34 slots
// WHY start at 05:00? Gym trainers, early market traders, breakfast restaurants.
// WHY end at 22:00? Late-evening restaurants, event staff, security patrols.
// Beyond 22:00 is genuinely edge-case and can be added if a specific tenant needs it.
const TIME_SLOTS = Array.from({ length: 35 }, (_, i) => {
  const totalMinutes = 5 * 60 + i * 30  // starts at 05:00
  const h = Math.floor(totalMinutes / 60)
  const m = totalMinutes % 60
  return `${String(h).padStart(2, "0")}:${String(m).padStart(2, "0")}`
})

export default function AvailabilityTab() {
  const qc = useQueryClient()

  const [showBlockModal, setShowBlockModal]   = useState(false)
  const [availForm, setAvailForm]             = useState({ staffId: "", dayOfWeek: 1, startTime: "08:00", endTime: "17:00" })
  const [blockForm, setBlockForm]             = useState({ staffId: "", blockDate: "", startTime: "", endTime: "", reason: "" })
  const [availSuccess, setAvailSuccess]       = useState("")
  const [availError, setAvailError]           = useState("")
  const [blockError, setBlockError]           = useState("")

  const { data: staff = [] } = useQuery<any[]>({
    queryKey: ["booking-staff"],
    queryFn: async () => (await apiClient.get("/api/v1/bookings/staff")).data?.data ?? [],
  })

  const setAvailability = useMutation({
    mutationFn: (body: any) => apiClient.post("/api/v1/bookings/availability", body),
    onSuccess: () => {
      setAvailSuccess("Working hours saved")
      setAvailError("")
      setTimeout(() => setAvailSuccess(""), 3000)
    },
    onError: (e: any) => {
      setAvailError(e.response?.data?.message ?? "Failed to save availability")
      setAvailSuccess("")
    },
  })

  const addBlock = useMutation({
    mutationFn: (body: any) => apiClient.post("/api/v1/bookings/blocks", body),
    onSuccess: () => {
      setShowBlockModal(false)
      setBlockForm({ staffId: "", blockDate: "", startTime: "", endTime: "", reason: "" })
      setBlockError("")
    },
    onError: (e: any) => setBlockError(e.response?.data?.message ?? "Failed to add block"),
  })

  const handleSaveAvailability = () => {
    if (!availForm.startTime || !availForm.endTime) { setAvailError("Start and end time are required"); return }
    if (availForm.startTime >= availForm.endTime)   { setAvailError("End time must be after start time"); return }
    setAvailability.mutate({
      staffId:    availForm.staffId || null,
      dayOfWeek:  availForm.dayOfWeek,
      startTime:  availForm.startTime,
      endTime:    availForm.endTime,
    })
  }

  return (
    <div>
      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 24 }}>

        {/* ── Working hours ──────────────────────────────────────────────── */}
        <div>
          <h3 style={{ fontSize: 15, fontWeight: 700, color: "#0F172A", margin: "0 0 4px" }}>Working Hours</h3>
          <p style={{ fontSize: 13, color: "#94A3B8", margin: "0 0 20px" }}>Set when bookings can be made for each day of the week</p>

          <div style={{ display: "flex", flexDirection: "column", gap: 14 }}>
            <div>
              <label style={lbl}>Staff member <span style={{ fontWeight: 400, color: "#94A3B8" }}>(leave blank for all staff)</span></label>
              <select value={availForm.staffId} onChange={e => setAvailForm(f => ({ ...f, staffId: e.target.value }))} style={inp}>
                <option value="">All staff / Business hours</option>
                {staff.map((s: any) => <option key={s.id} value={s.id}>{s.name}</option>)}
              </select>
            </div>

            <div>
              <label style={lbl}>Day of week</label>
              <div style={{ display: "grid", gridTemplateColumns: "repeat(7, 1fr)", gap: 4 }}>
                {DAYS.map(d => (
                  <button key={d.value} onClick={() => setAvailForm(f => ({ ...f, dayOfWeek: d.value }))}
                    style={{
                      padding: "8px 4px", borderRadius: 7, fontSize: 11, fontWeight: 600, cursor: "pointer", border: "none",
                      background: availForm.dayOfWeek === d.value ? "#1B3A6B" : "#F1F5F9",
                      color: availForm.dayOfWeek === d.value ? "#fff" : "#64748B",
                    }}>
                    {d.label.slice(0, 3)}
                  </button>
                ))}
              </div>
            </div>

            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
              <div>
                <label style={lbl}>Start time</label>
                <select value={availForm.startTime} onChange={e => setAvailForm(f => ({ ...f, startTime: e.target.value }))} style={inp}>
                  {TIME_SLOTS.map(t => <option key={t} value={t}>{t}</option>)}
                </select>
              </div>
              <div>
                <label style={lbl}>End time</label>
                <select value={availForm.endTime} onChange={e => setAvailForm(f => ({ ...f, endTime: e.target.value }))} style={inp}>
                  {TIME_SLOTS.map(t => <option key={t} value={t}>{t}</option>)}
                </select>
              </div>
            </div>

            {availError && (
              <div style={{ padding: "10px 12px", background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 8, fontSize: 13, color: "#DC2626", display: "flex", alignItems: "center", gap: 8 }}>
                <AlertCircle size={14} />{availError}
              </div>
            )}
            {availSuccess && (
              <div style={{ padding: "10px 12px", background: "#F0FDF4", border: "1px solid #86EFAC", borderRadius: 8, fontSize: 13, color: "#166534", display: "flex", alignItems: "center", gap: 8 }}>
                <CheckCircle size={14} />{availSuccess}
              </div>
            )}

            <button
              onClick={handleSaveAvailability}
              disabled={setAvailability.isPending}
              style={{ padding: "10px", background: "#1B3A6B", color: "#fff", border: "none", borderRadius: 9, fontSize: 14, fontWeight: 600, cursor: "pointer" }}>
              {setAvailability.isPending ? "Saving…" : "Save working hours"}
            </button>
          </div>
        </div>

        {/* ── Time Blocks ────────────────────────────────────────────────── */}
        <div>
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", marginBottom: 20 }}>
            <div>
              <h3 style={{ fontSize: 15, fontWeight: 700, color: "#0F172A", margin: "0 0 4px" }}>Time Blocks</h3>
              <p style={{ fontSize: 13, color: "#94A3B8", margin: 0 }}>Block time for lunch, leave, holidays or maintenance</p>
            </div>
            <button
              onClick={() => setShowBlockModal(true)}
              style={{ display: "flex", alignItems: "center", gap: 6, padding: "8px 14px", background: "#FEF2F2", color: "#DC2626", border: "1px solid #FECACA", borderRadius: 8, fontSize: 13, fontWeight: 600, cursor: "pointer" }}>
              <Plus size={13} /> Add block
            </button>
          </div>

          {/* Quick presets */}
          <div style={{ display: "flex", flexDirection: "column", gap: 8, marginBottom: 16 }}>
            <div style={{ fontSize: 12, fontWeight: 600, color: "#94A3B8", textTransform: "uppercase", letterSpacing: "0.05em" }}>Quick presets</div>
            {[
              { label: "Lunch break",    start: "12:00", end: "13:00", reason: "Lunch" },
              { label: "Public holiday", start: "",      end: "",      reason: "Public holiday — full day" },
              { label: "Staff training", start: "09:00", end: "17:00", reason: "Staff training" },
            ].map(preset => (
              <button
                key={preset.label}
                onClick={() => { setBlockForm(f => ({ ...f, startTime: preset.start, endTime: preset.end, reason: preset.reason })); setShowBlockModal(true) }}
                style={{ padding: "10px 14px", background: "#F8FAFC", border: "1px solid #E2E8F0", borderRadius: 8, fontSize: 13, cursor: "pointer", textAlign: "left", color: "#374151", fontWeight: 500 }}>
                {preset.label}
              </button>
            ))}
          </div>

          <div style={{ padding: "14px 16px", background: "#FEF3C7", border: "1px solid #FCD34D", borderRadius: 8, fontSize: 12, color: "#92400E", lineHeight: 1.6 }}>
            <strong>Tip:</strong> Leave start and end time empty to block the entire day.
            Blocks prevent new bookings but do not affect existing confirmed bookings.
          </div>
        </div>
      </div>

      {/* ── Block modal ─────────────────────────────────────────────────── */}
      {showBlockModal && (
        <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000, backdropFilter: "blur(2px)" }}>
          <div style={{ background: "#fff", borderRadius: 14, padding: 28, width: 460, boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }}>
            <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 22 }}>
              <h3 style={{ margin: 0, fontSize: 17, fontWeight: 700, color: "#0F172A" }}>Block Time</h3>
              <button onClick={() => setShowBlockModal(false)} style={{ background: "none", border: "none", cursor: "pointer", color: "#94A3B8", display: "flex" }}><X size={20} /></button>
            </div>

            <div style={{ display: "flex", flexDirection: "column", gap: 14 }}>
              <div>
                <label style={lbl}>Staff member <span style={{ fontWeight: 400, color: "#94A3B8" }}>(blank = all staff)</span></label>
                <select value={blockForm.staffId} onChange={e => setBlockForm(f => ({ ...f, staffId: e.target.value }))} style={inp}>
                  <option value="">All staff</option>
                  {staff.map((s: any) => <option key={s.id} value={s.id}>{s.name}</option>)}
                </select>
              </div>
              <div>
                <label style={lbl}>Date *</label>
                <input type="date" value={blockForm.blockDate} onChange={e => setBlockForm(f => ({ ...f, blockDate: e.target.value }))} style={inp} />
              </div>
              <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
                <div>
                  <label style={lbl}>Start time <span style={{ fontWeight: 400, color: "#94A3B8" }}>(blank = full day)</span></label>
                  <select value={blockForm.startTime} onChange={e => setBlockForm(f => ({ ...f, startTime: e.target.value }))} style={inp}>
                    <option value="">Full day</option>
                    {TIME_SLOTS.map(t => <option key={t} value={t}>{t}</option>)}
                  </select>
                </div>
                <div>
                  <label style={lbl}>End time</label>
                  <select value={blockForm.endTime} onChange={e => setBlockForm(f => ({ ...f, endTime: e.target.value }))} style={inp}>
                    <option value="">Full day</option>
                    {TIME_SLOTS.map(t => <option key={t} value={t}>{t}</option>)}
                  </select>
                </div>
              </div>
              <div>
                <label style={lbl}>Reason</label>
                <input value={blockForm.reason} onChange={e => setBlockForm(f => ({ ...f, reason: e.target.value }))}
                  placeholder="e.g. Public holiday, Lunch, Staff training" style={inp} />
              </div>
            </div>

            {blockError && (
              <div style={{ marginTop: 14, padding: "10px 12px", background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 8, fontSize: 13, color: "#DC2626", display: "flex", alignItems: "center", gap: 8 }}>
                <AlertCircle size={14} />{blockError}
              </div>
            )}

            <div style={{ display: "flex", gap: 10, justifyContent: "flex-end", marginTop: 20 }}>
              <button onClick={() => setShowBlockModal(false)}
                style={{ padding: "9px 18px", border: "1px solid #E2E8F0", borderRadius: 9, background: "#fff", fontSize: 14, cursor: "pointer", color: "#374151" }}>
                Cancel
              </button>
              <button
                onClick={() => addBlock.mutate({
                  staffId:   blockForm.staffId || null,
                  blockDate: blockForm.blockDate,
                  startTime: blockForm.startTime || null,
                  endTime:   blockForm.endTime   || null,
                  reason:    blockForm.reason    || null,
                })}
                disabled={!blockForm.blockDate || addBlock.isPending}
                style={{ padding: "9px 20px", background: "#DC2626", color: "#fff", border: "none", borderRadius: 9, fontSize: 14, fontWeight: 600, cursor: "pointer" }}>
                {addBlock.isPending ? "Blocking…" : "Block time"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

const lbl: React.CSSProperties = { display: "block", fontSize: 13, fontWeight: 600, color: "#374151", marginBottom: 5 }
const inp: React.CSSProperties = { width: "100%", padding: "9px 12px", border: "1.5px solid #E2E8F0", borderRadius: 8, fontSize: 14, boxSizing: "border-box", background: "#fff", outline: "none" }

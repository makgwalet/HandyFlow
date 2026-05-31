// src/pages/security/LiveMapTab.tsx
import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { Radio, MapPin, QrCode, Bluetooth, Navigation, Clock, Shield } from "lucide-react"

const SCAN_TYPE_CONFIG: Record<string, { label: string; color: string; icon: React.ElementType }> = {
  QR:       { label: "QR Code",   color: "#1D4ED8", icon: QrCode },
  NFC:      { label: "NFC Tag",   color: "#7C3AED", icon: Radio },
  BLE:      { label: "BLE Beacon",color: "#0D9488", icon: Bluetooth },
  GPS_PING: { label: "GPS Ping",  color: "#166534", icon: Navigation },
  MANUAL:   { label: "Manual",    color: "#D97706", icon: Clock },
}

export default function LiveMapTab() {
  const [selectedGuard, setSelectedGuard] = useState<string | null>(null)

  const { data: guards = [] } = useQuery({
    queryKey: ["guards"],
    queryFn: async () => { const r = await apiClient.get("/api/v1/security/guards?size=100"); return (r.data?.data ?? r.data).content ?? [] },
    refetchInterval: 30000,
  })

  const { data: shifts = [] } = useQuery({
    queryKey: ["active-shifts"],
    queryFn: async () => {
      const r = await apiClient.get("/api/v1/security/shifts?size=100")
      const all = (r.data?.data ?? r.data).content ?? []
      return (all as any[]).filter((s: any) => s.status === "ACTIVE")
    },
    refetchInterval: 30000,
  })

  const activeCount = (shifts as any[]).length

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 20 }}>
        <div>
          <h3 style={{ margin: "0 0 4px", fontSize: 16, fontWeight: 700, color: "#0F172A" }}>Live Operations Map</h3>
          <p style={{ margin: 0, fontSize: 13, color: "#94A3B8" }}>Real-time guard positions and checkpoint scans</p>
        </div>
        <div style={{ display: "flex", alignItems: "center", gap: 8, padding: "6px 14px", background: "#DCFCE7", border: "1px solid #86EFAC", borderRadius: 20 }}>
          <div style={{ width: 8, height: 8, borderRadius: "50%", background: "#22C55E", animation: "pulse 2s infinite" }} />
          <span style={{ fontSize: 13, fontWeight: 600, color: "#166534" }}>{activeCount} guards on duty</span>
        </div>
      </div>

      {/* Hardware support notice */}
      <div style={{ marginBottom: 20, padding: "14px 18px", background: "#EFF6FF", border: "1px solid #BFDBFE", borderRadius: 10 }}>
        <div style={{ fontSize: 13, fontWeight: 700, color: "#1D4ED8", marginBottom: 6 }}>Supported scan types</div>
        <div style={{ display: "flex", gap: 16, flexWrap: "wrap" }}>
          {Object.entries(SCAN_TYPE_CONFIG).map(([key, cfg]) => (
            <div key={key} style={{ display: "flex", alignItems: "center", gap: 6, fontSize: 12, color: "#475569" }}>
              <div style={{ width: 22, height: 22, borderRadius: 6, background: `${cfg.color}18`, display: "flex", alignItems: "center", justifyContent: "center" }}>
                <cfg.icon size={12} color={cfg.color} />
              </div>
              {cfg.label}
            </div>
          ))}
        </div>
        <div style={{ marginTop: 8, fontSize: 12, color: "#64748B" }}>
          Future: CAT phone app will send GPS pings every 5 minutes during active shifts. NFC tags and BLE beacons supported for indoor checkpoints without GPS.
        </div>
      </div>

      <div style={{ display: "grid", gridTemplateColumns: "1fr 300px", gap: 16 }}>
        {/* Map placeholder */}
        <div style={{ border: "1px solid #E2E8F0", borderRadius: 12, overflow: "hidden", minHeight: 500, position: "relative", background: "#F8FAFC" }}>
          {/* Simulated map background */}
          <div style={{ position: "absolute", inset: 0, background: "linear-gradient(135deg, #EFF6FF 0%, #F0FDF4 50%, #FFFBEB 100%)", display: "flex", alignItems: "center", justifyContent: "center", flexDirection: "column", gap: 16 }}>

            {/* Mock guard pins */}
            {(shifts as any[]).slice(0, 4).map((shift: any, i: number) => {
              const positions = [
                { top: "20%", left: "25%" },
                { top: "45%", left: "60%" },
                { top: "65%", left: "30%" },
                { top: "30%", left: "70%" },
              ]
              const pos = positions[i] ?? positions[0]
              return (
                <div key={shift.id} style={{ position: "absolute", ...pos, transform: "translate(-50%, -50%)", cursor: "pointer", zIndex: 2 }}
                  onClick={() => setSelectedGuard(shift.guardId)}>
                  <div style={{ width: 36, height: 36, borderRadius: "50%", background: "#1B3A6B", border: "3px solid #fff", display: "flex", alignItems: "center", justifyContent: "center", boxShadow: "0 4px 12px rgba(27,58,107,0.4)" }}>
                    <Shield size={16} color="#fff" />
                  </div>
                  <div style={{ position: "absolute", top: "100%", left: "50%", transform: "translateX(-50%)", marginTop: 4, background: "#1B3A6B", color: "#fff", borderRadius: 6, padding: "2px 8px", fontSize: 10, fontWeight: 600, whiteSpace: "nowrap" }}>
                    Guard {i + 1}
                  </div>
                  {/* Pulse ring */}
                  <div style={{ position: "absolute", inset: -6, borderRadius: "50%", border: "2px solid #1B3A6B", opacity: 0.3, animation: "ping 2s infinite" }} />
                </div>
              )
            })}

            {/* Map grid lines */}
            <svg style={{ position: "absolute", inset: 0, width: "100%", height: "100%", opacity: 0.15 }}>
              {Array.from({ length: 10 }, (_, i) => (
                <g key={i}>
                  <line x1={`${i * 10}%`} y1="0%" x2={`${i * 10}%`} y2="100%" stroke="#1B3A6B" strokeWidth="1" />
                  <line x1="0%" y1={`${i * 10}%`} x2="100%" y2={`${i * 10}%`} stroke="#1B3A6B" strokeWidth="1" />
                </g>
              ))}
            </svg>

            {activeCount === 0 && (
              <div style={{ textAlign: "center", color: "#94A3B8" }}>
                <MapPin size={36} color="#CBD5E1" style={{ marginBottom: 10 }} />
                <div style={{ fontWeight: 600, color: "#475569" }}>No active shifts</div>
                <div style={{ fontSize: 13, marginTop: 4 }}>Guard positions will appear here during active shifts</div>
              </div>
            )}
          </div>

          {/* Map controls */}
          <div style={{ position: "absolute", top: 16, right: 16, display: "flex", flexDirection: "column", gap: 6, zIndex: 3 }}>
            {["+", "−", "⊕"].map(c => (
              <button key={c} style={{ width: 32, height: 32, background: "#fff", border: "1px solid #E2E8F0", borderRadius: 6, fontSize: 16, cursor: "pointer", display: "flex", alignItems: "center", justifyContent: "center", boxShadow: "0 2px 8px rgba(0,0,0,0.1)" }}>{c}</button>
            ))}
          </div>
        </div>

        {/* Active guards list */}
        <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
          <div style={{ fontSize: 13, fontWeight: 700, color: "#0F172A", marginBottom: 4 }}>Active Guards</div>

          {(shifts as any[]).length === 0 ? (
            <div style={{ textAlign: "center", padding: "30px 16px", border: "1px dashed #E2E8F0", borderRadius: 10, color: "#94A3B8", fontSize: 13 }}>
              No guards currently on shift
            </div>
          ) : (
            (shifts as any[]).map((shift: any, i: number) => {
              const guard = (guards as any[]).find(g => g.id === shift.guardId)
              return (
                <div key={shift.id}
                  onClick={() => setSelectedGuard(selectedGuard === shift.guardId ? null : shift.guardId)}
                  style={{ padding: "14px 16px", border: `2px solid ${selectedGuard === shift.guardId ? "#0D9488" : "#E2E8F0"}`, borderRadius: 10, background: selectedGuard === shift.guardId ? "#F0FDF4" : "#fff", cursor: "pointer" }}>
                  <div style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 8 }}>
                    <div style={{ position: "relative", flexShrink: 0 }}>
                      <div style={{ width: 36, height: 36, borderRadius: "50%", background: "#EFF6FF", display: "flex", alignItems: "center", justifyContent: "center", fontWeight: 700, color: "#1D4ED8", fontSize: 13 }}>
                        {guard ? `${guard.firstName?.[0]}${guard.lastName?.[0]}` : "?"}
                      </div>
                      <div style={{ position: "absolute", bottom: 0, right: 0, width: 10, height: 10, borderRadius: "50%", background: "#22C55E", border: "2px solid #fff" }} />
                    </div>
                    <div>
                      <div style={{ fontWeight: 600, fontSize: 13, color: "#0F172A" }}>{guard?.fullName ?? `Guard ${i + 1}`}</div>
                      <div style={{ fontSize: 11, color: "#94A3B8" }}>Grade {guard?.grade ?? "—"}</div>
                    </div>
                  </div>
                  <div style={{ fontSize: 11, color: "#64748B" }}>
                    Shift: {new Date(shift.startAt).toLocaleTimeString("en-ZA", { hour: "2-digit", minute: "2-digit" })} – {new Date(shift.endAt).toLocaleTimeString("en-ZA", { hour: "2-digit", minute: "2-digit" })}
                  </div>
                  {/* Last scan info */}
                  <div style={{ marginTop: 8, padding: "6px 10px", background: "#F8FAFC", borderRadius: 6, fontSize: 11, color: "#64748B" }}>
                    Last checkpoint: —
                  </div>
                </div>
              )
            })
          )}

          {/* Device legend */}
          <div style={{ marginTop: 8, padding: "14px 16px", background: "#FEF3C7", border: "1px solid #FCD34D", borderRadius: 10 }}>
            <div style={{ fontSize: 12, fontWeight: 700, color: "#92400E", marginBottom: 8 }}>Coming soon</div>
            <div style={{ fontSize: 11, color: "#78350F", lineHeight: 1.6 }}>
              CAT phone GPS tracking — guards on active shifts will show real-time positions updated every 5 minutes. NFC wristbands and BLE ankle tags also supported.
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}
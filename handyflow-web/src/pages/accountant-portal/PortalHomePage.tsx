// src/pages/accountant-portal/PortalHomePage.tsx
import { useEffect, useState } from "react"
import { useNavigate } from "react-router-dom"
import { portalApi } from "../../api/portal.api"
import { usePortalAuthStore } from "../../store/portalAuth.store"
import type { PortalClientSummary } from "../../types/portal.types"
import { PortalShell } from "./PortalShell"
import { color, radius, shadow, space, type } from "./portal-theme"

const fmtR = (n: any) => `R ${Number(n ?? 0).toLocaleString("en-ZA", { minimumFractionDigits: 2 })}`

export function PortalHomePage() {
  const navigate = useNavigate()
  const user = usePortalAuthStore(s => s.user)
  const [clients, setClients] = useState<PortalClientSummary[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    portalApi.getMyClients().then(setClients).finally(() => setLoading(false))
  }, [])

  const firstName = user?.fullName?.split(" ")[0]

  return (
    <PortalShell>
      <div style={{ maxWidth: 680, margin: "0 auto", padding: `${space(10)} ${space(5)} ${space(12)}` }}>
        <h1
          style={{
            fontSize: 24,
            fontWeight: 800,
            color: color.ink,
            marginBottom: space(1),
            letterSpacing: "-0.02em",
          }}
        >
          Welcome back{firstName ? `, ${firstName}` : ""}
        </h1>
        <p style={{ fontSize: 14, color: color.muted, marginBottom: space(8) }}>
          {loading
            ? "Loading your client access…"
            : clients.length === 0
            ? "Your client access:"
            : `You have access to ${clients.length} client${clients.length !== 1 ? "s" : ""}.`}
        </p>

        {loading ? (
          <LoadingRows />
        ) : clients.length === 0 ? (
          <EmptyState
            icon="📂"
            title="No client access yet"
            body="Once your accountant grants you access to a client, it will appear here."
          />
        ) : (
          <div style={{ display: "flex", flexDirection: "column", gap: space(3) }}>
            {clients.map(c => (
              <ClientCard key={c.clientId} client={c} onClick={() => navigate(`/accountant/portal/clients/${c.clientId}`)} />
            ))}
          </div>
        )}
      </div>
    </PortalShell>
  )
}

function ClientCard({ client, onClick }: { client: PortalClientSummary; onClick: () => void }) {
  const owing = client.outstandingBalance > 0
  return (
    <button
      onClick={onClick}
      style={{
        textAlign: "left" as const,
        width: "100%",
        padding: `${space(5)} ${space(6)}`,
        background: color.surface,
        border: `1px solid ${color.border}`,
        borderLeft: owing ? `3px solid ${color.red}` : `1px solid ${color.border}`,
        borderRadius: radius.md,
        cursor: "pointer",
        boxShadow: shadow.card,
        transition: "box-shadow 0.15s ease, transform 0.1s ease, border-color 0.15s ease",
      }}
      onMouseEnter={e => {
        e.currentTarget.style.boxShadow = shadow.cardHover
        e.currentTarget.style.transform = "translateY(-1px)"
      }}
      onMouseLeave={e => {
        e.currentTarget.style.boxShadow = shadow.card
        e.currentTarget.style.transform = "translateY(0)"
      }}
    >
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "baseline", gap: space(4) }}>
        <span style={{ fontSize: 15.5, fontWeight: 700, color: color.ink, letterSpacing: "-0.01em" }}>
          {client.tradingName}
        </span>
        {owing && (
          <span style={{ fontSize: 15, fontWeight: 700, color: color.red, whiteSpace: "nowrap" as const }}>
            {fmtR(client.outstandingBalance)} owing
          </span>
        )}
      </div>

      {(client.openRequestCount > 0 || client.upcomingDeadlineCount > 0) && (
        <div style={{ display: "flex", gap: space(2), marginTop: space(3), flexWrap: "wrap" as const }}>
          {client.openRequestCount > 0 && (
            <Badge tone="amber">
              {client.openRequestCount} request{client.openRequestCount !== 1 ? "s" : ""} awaiting you
            </Badge>
          )}
          {client.upcomingDeadlineCount > 0 && (
            <Badge tone="blue">
              {client.upcomingDeadlineCount} deadline{client.upcomingDeadlineCount !== 1 ? "s" : ""} in 30 days
            </Badge>
          )}
        </div>
      )}

      <div
        style={{
          fontSize: 12.5,
          color: color.faint,
          fontWeight: 500,
          marginTop: space(3),
          display: "flex",
          alignItems: "center",
          gap: 4,
        }}
      >
        View fee notes, documents, requests, and deadlines
        <span style={{ transition: "transform 0.15s ease" }}>→</span>
      </div>
    </button>
  )
}

function Badge({ tone, children }: { tone: "amber" | "blue"; children: React.ReactNode }) {
  const tones = {
    amber: { color: color.amber, bg: color.amberBg },
    blue: { color: color.blue, bg: color.blueBg },
  }[tone]
  return (
    <span
      style={{
        fontSize: 11.5,
        fontWeight: 700,
        color: tones.color,
        background: tones.bg,
        padding: "3px 10px",
        borderRadius: radius.pill,
        letterSpacing: "0.01em",
      }}
    >
      {children}
    </span>
  )
}

function EmptyState({ icon, title, body }: { icon: string; title: string; body: string }) {
  return (
    <div
      style={{
        textAlign: "center" as const,
        padding: `${space(14)} ${space(6)}`,
        background: color.surface,
        border: `1px dashed ${color.border}`,
        borderRadius: radius.lg,
      }}
    >
      <div style={{ fontSize: 32, marginBottom: space(3) }}>{icon}</div>
      <div style={{ fontSize: 15, fontWeight: 700, color: color.ink, marginBottom: space(1) }}>{title}</div>
      <div style={{ fontSize: 13.5, color: color.muted, maxWidth: 320, margin: "0 auto", lineHeight: 1.5 }}>{body}</div>
    </div>
  )
}

function LoadingRows() {
  return (
    <div style={{ display: "flex", flexDirection: "column", gap: space(3) }}>
      {[0, 1].map(i => (
        <div
          key={i}
          style={{
            height: 92,
            background: color.surface,
            border: `1px solid ${color.border}`,
            borderRadius: radius.md,
            opacity: 0.5,
          }}
        />
      ))}
    </div>
  )
}

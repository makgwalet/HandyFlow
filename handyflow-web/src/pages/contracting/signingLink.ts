// Signing-link status for a party, shown to staff. Email is sent asynchronously, so QUEUED means handed to the mail queue,
// not delivered or read.
export interface LinkStatus { text: string; tone: 'ok' | 'warn' | 'bad' | 'none' }

export function linkStatus(p: { linkIssuedAt?: string | null; linkDeliveryStatus?: string | null; email?: string | null }, fmt: (iso: string) => string): LinkStatus {
  if (!p.linkIssuedAt) return { text: 'No signing link issued yet', tone: 'none' }
  const when = fmt(p.linkIssuedAt)
  switch (p.linkDeliveryStatus) {
    case 'QUEUED': return { text: `Link issued ${when}. Email queued (delivery is not confirmed)`, tone: 'ok' }
    case 'FAILED': return { text: `Link issued ${when}. Email failed, use Link to resend`, tone: 'bad' }
    case 'NO_CHANNEL': return { text: `Link issued ${when}. No email address on file, so nothing was sent`, tone: 'warn' }
    default: return { text: `Link issued ${when}`, tone: 'none' }
  }
}

export const LINK_TONE_COLOR: Record<LinkStatus['tone'], string> = {
  ok: 'var(--hf-text-muted)', warn: 'var(--hf-warning-text)', bad: 'var(--hf-danger-text)', none: 'var(--hf-text-faint)',
}

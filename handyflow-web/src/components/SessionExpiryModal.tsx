// src/components/SessionExpiryModal.tsx
//
// Watches the current access token's own expiry and warns before it
// happens, giving the user a chance to extend their session — via a real
// refresh, not just dismissing a warning — or being logged out
// automatically if they don't respond in time.
//
// Deliberately complementary to, not a replacement for, client.ts's
// existing silent-refresh-on-401/403 interceptor. That one is reactive —
// it only fires once a request has already failed. This one is
// proactive: it warns before that ever happens, and matters specifically
// because someone sitting idle with no pending request would otherwise
// get no warning at all before their session just stops working.
import { useEffect, useRef, useState } from 'react'
import { useAuthStore } from '../store/auth.store'
import { apiClient } from '../api/client'
import { decodeJwtExpiry } from '../utils/jwt'

// How long before actual expiry the warning appears. 2 minutes on a
// 15-minute token gives a real chance to notice and respond, without
// warning so early into a short-lived token that it feels premature.
const WARNING_WINDOW_MS = 2 * 60 * 1000
// How often the background check runs before the warning is showing —
// coarse on purpose, this only needs to catch "have we crossed into the
// warning window yet", not drive a live countdown display.
const CHECK_INTERVAL_MS = 5000

export function SessionExpiryModal() {
  const token = useAuthStore((s) => s.token)
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated)
  const logout = useAuthStore((s) => s.logout)

  const [showWarning, setShowWarning] = useState(false)
  const [secondsRemaining, setSecondsRemaining] = useState(0)
  const [extending, setExtending] = useState(false)

  useEffect(() => {
    if (!isAuthenticated || !token) {
      setShowWarning(false)
      return
    }

    const expiryMs = decodeJwtExpiry(token)
    // Couldn't decode — fail open. This is a UX warning, not a security
    // boundary; the actual enforcement is server-side and client.ts's
    // interceptor already handles a token that's genuinely expired on
    // the next real request regardless of whether this modal ever shows.
    if (!expiryMs) return

    const checkInterval = setInterval(() => {
      const msRemaining = expiryMs - Date.now()

      if (msRemaining <= 0) {
        // Already expired with the user just sitting idle — no pending
        // request exists for client.ts's own interceptor to catch, so
        // nothing else would ever notice this. Log out directly rather
        // than wait for a request that might never come.
        clearInterval(checkInterval)
        logout()
        window.location.href = '/login'
        return
      }

      if (msRemaining <= WARNING_WINDOW_MS) {
        setShowWarning(true)
        setSecondsRemaining(Math.ceil(msRemaining / 1000))
      }
    }, CHECK_INTERVAL_MS)

    return () => clearInterval(checkInterval)
  }, [token, isAuthenticated, logout])

  // Separate, second-by-second countdown, only running once the warning
  // is actually visible — the background check above only ticks every
  // 5s, too coarse for a live "0:47 remaining" display on its own.
  useEffect(() => {
    if (!showWarning) return

    const tick = setInterval(() => {
      setSecondsRemaining((s) => {
        if (s <= 1) {
          clearInterval(tick)
          logout()
          window.location.href = '/login'
          return 0
        }
        return s - 1
      })
    }, 1000)

    return () => clearInterval(tick)
  }, [showWarning, logout])

  const handleContinue = async () => {
    setExtending(true)
    try {
      // Reuses the exact same refresh endpoint client.ts's own
      // interceptor already calls — no separate extension mechanism,
      // just triggering the real rotation deliberately and early instead
      // of waiting for a failed request to trigger it reactively.
      const response = await apiClient.post('/api/v1/auth/refresh')
      const data = response.data as {
        accessToken: string; userId: string; tenantId: string; email: string
        firstName: string; lastName: string; permissions: string[]
      }
      const currentUser = useAuthStore.getState().user
      useAuthStore.getState().setAuth(data.accessToken, {
        ...(currentUser ?? {}),
        userId: data.userId,
        tenantId: data.tenantId,
        email: data.email,
        firstName: data.firstName,
        lastName: data.lastName,
        permissions: data.permissions,
      } as any)
      setShowWarning(false)
    } catch {
      // Refresh itself failed — the session genuinely can't be extended
      // (e.g. the refresh token has also expired or been revoked
      // separately). Nothing left to do but log out cleanly rather than
      // leave the modal open with a "Continue" button that can't work.
      logout()
      window.location.href = '/login'
    } finally {
      setExtending(false)
    }
  }

  if (!showWarning) return null

  const minutes = Math.floor(secondsRemaining / 60)
  const seconds = secondsRemaining % 60

  return (
    <div style={{
      position: 'fixed', inset: 0, background: 'rgba(15, 23, 42, 0.6)',
      display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 9999,
    }}>
      <div style={{ background: 'white', borderRadius: 16, padding: 32, maxWidth: 400, width: '90%', boxShadow: '0 24px 80px rgba(0,0,0,0.3)', textAlign: 'center' }}>
        <div style={{ width: 52, height: 52, borderRadius: '50%', background: '#FFFBEB', display: 'flex', alignItems: 'center', justifyContent: 'center', margin: '0 auto 16px', fontSize: 24 }}>
          ⏱️
        </div>
        <h2 style={{ fontSize: 18, fontWeight: 800, color: '#0F172A', margin: '0 0 8px' }}>
          Your session is about to expire
        </h2>
        <p style={{ fontSize: 14, color: '#64748B', margin: '0 0 4px' }}>
          You'll be logged out in
        </p>
        <p style={{ fontSize: 28, fontWeight: 800, color: '#D97706', fontVariantNumeric: 'tabular-nums', margin: '0 0 20px' }}>
          {minutes}:{seconds.toString().padStart(2, '0')}
        </p>
        <button onClick={handleContinue} disabled={extending}
          style={{ width: '100%', padding: '12px', background: '#1B3A6B', color: 'white', border: 'none', borderRadius: 10, fontSize: 15, fontWeight: 700, cursor: 'pointer', opacity: extending ? 0.6 : 1 }}>
          {extending ? 'Extending session...' : 'Continue working'}
        </button>
      </div>
    </div>
  )
}

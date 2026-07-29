// src/components/layout/NotificationDrawer.tsx
// FIX: "implement the notification APIs" — the previous version guessed at
// endpoints and a flat-array response shape; the real
// NotificationController/NotificationQueryService are now confirmed. Two
// real mismatches this fixes, not just style cleanup:
//   1. GET /api/v1/notifications returns a Spring Data Page<NotificationResponse>
//      (an object with a .content array + pagination metadata), not a flat
//      array — this is exactly what caused "notifications.filter is not a
//      function": the code was calling .filter() on the Page object itself.
//   2. Marking a notification read is PATCH /api/v1/notifications/{id}/read,
//      not POST — confirmed from NotificationController.markRead().
import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { X, Bell, CheckCheck, Loader2 } from 'lucide-react'
import { apiClient } from '../../api/client'

// Matches NotificationQueryService.toResponse() field-for-field —
// id, type, severity, title, message, actionUrl, sourceModule,
// sourceEntityId, read, readAt, createdAt, in that exact order.
interface Notification {
  id: string
  type: string
  severity: string
  title: string
  message: string
  actionUrl?: string | null
  sourceModule?: string
  sourceEntityId?: string | null
  read: boolean
  readAt?: string | null
  createdAt: string
}

interface NotificationPage {
  content: Notification[]
  totalElements: number
  totalPages: number
  number: number
}

const PAGE_SIZE = 25

// FIX: "clicking a notification redirects to dashboard" — confirmed root
// cause via App.tsx: <Route path="*" element={<Navigate to="/dashboard" />} />
// is a catch-all, and several modules (Clinic, Projects' list view, etc.)
// are single-page shells with internal tab state, not real sub-routes —
// there's no /clinic/claims or /clinic/appointments route at all, only
// /clinic itself. Any notification whose actionUrl pointed one level
// deeper than a module's real route was silently hitting that catch-all
// and bouncing to dashboard, on every single click, regardless of which
// notification — matching exactly what was reported.
//
// This list is the exact set of top-level module routes from App.tsx's
// <ModuleLayout> route group (plus /dashboard) — not guessed, copied
// directly from that file. Deliberately checking only the FIRST path
// segment rather than trying to replicate React Router's full matching
// (including dynamic segments like /quotes/:id, /projects/:id): if the
// actionUrl's base segment is a real route, land on that module's page —
// even if the deeper path wasn't real, landing on the right module is a
// far better outcome than bouncing to dashboard. If the base segment
// itself isn't recognized, that's a genuine dead link, and dashboard is
// the correct fallback for that case.
const KNOWN_ROUTES = new Set([
  'dashboard', 'customers', 'quotes', 'invoices', 'catalogue', 'billing',
  'security', 'fuel', 'earthmoving', 'property', 'fleet', 'bookings',
  'accounting', 'settings', 'hr', 'clinic', 'events', 'contracts',
  'expenses', 'invite', 'creative', 'desk', 'tasks', 'marketing',
  'recruiter', 'pos', 'accountant', 'ap', 'profile', 'recurring',
  'supply-chain', 'projects',
])

/** Returns a safe URL to navigate to, or null if actionUrl doesn't target any known route at all. */
function resolveSafeActionUrl(actionUrl: string): string | null {
  const segments = actionUrl.split('/').filter(Boolean)
  if (segments.length === 0) return null
  const base = segments[0]
  if (!KNOWN_ROUTES.has(base)) return null
  // Known dynamic-segment routes where the deeper path is real and should
  // be preserved, not truncated — everything else lands on the module's
  // base page since its sub-paths are just internal tab state, not routes.
  const preservesDeepPath = new Set(['quotes', 'projects'])
  return preservesDeepPath.has(base) ? actionUrl : '/' + base
}

const timeAgo = (iso: string) => {
  const diffMs = Date.now() - new Date(iso).getTime()
  const mins = Math.floor(diffMs / 60000)
  if (mins < 1) return 'just now'
  if (mins < 60) return `${mins}m ago`
  const hrs = Math.floor(mins / 60)
  if (hrs < 24) return `${hrs}h ago`
  const days = Math.floor(hrs / 24)
  if (days < 7) return `${days}d ago`
  return new Date(iso).toLocaleDateString('en-ZA', { day: 'numeric', month: 'short' })
}

export function NotificationDrawer({ open, onClose }: { open: boolean; onClose: () => void }) {
  const navigate = useNavigate()
  const qc = useQueryClient()

  // FIX: retention question resolved as "paginate, don't cut off by
  // time" — the backend keeps everything, so hiding anything past an
  // arbitrary age would only make real history awkward to reach for no
  // real benefit. Load Recent by default; "Load more" fetches further
  // back on demand rather than ever hard-cutting access. unreadOnly
  // reuses the filter the backend already supports.
  const [unreadOnly, setUnreadOnly] = useState(false)
  const [loadedPages, setLoadedPages] = useState(0)
  const [allLoaded, setAllLoaded] = useState<Notification[]>([])

  const { data, isLoading, isFetching, isError } = useQuery<NotificationPage>({
    queryKey: ['notifications', unreadOnly, loadedPages],
    queryFn: async () => {
      const res = await apiClient.get('/api/v1/notifications', {
        params: { unreadOnly, page: loadedPages, size: PAGE_SIZE },
      })
      return res.data
    },
    enabled: open,
    refetchInterval: open && loadedPages === 0 ? 30_000 : false,
  })

  // Accumulate pages as "Load more" is clicked; reset when the filter changes or the drawer reopens.
  const notifications = loadedPages === 0 ? (data?.content ?? []) : [...allLoaded, ...(data?.content ?? [])]
  const hasMore = data ? data.number < data.totalPages - 1 : false

  const resetPaging = (nextUnreadOnly: boolean) => {
    setUnreadOnly(nextUnreadOnly)
    setAllLoaded([])
    setLoadedPages(0)
  }

  const loadMore = () => {
    if (data?.content) setAllLoaded(prev => [...prev, ...data.content])
    setLoadedPages(p => p + 1)
  }

  const markRead = useMutation({
    mutationFn: (id: string) => apiClient.patch(`/api/v1/notifications/${id}/read`),
    // Optimistic: the whole point of "click to read" is that it should
    // feel instant, not wait on a round-trip before the dot disappears.
    onMutate: async (id: string) => {
      setAllLoaded(prev => prev.map(n => n.id === id ? { ...n, read: true } : n))
      qc.setQueryData<NotificationPage | undefined>(['notifications', unreadOnly, loadedPages], old =>
        old ? { ...old, content: old.content.map(n => n.id === id ? { ...n, read: true } : n) } : old)
    },
    onSettled: () => {
      qc.invalidateQueries({ queryKey: ['notifications'] })
      qc.invalidateQueries({ queryKey: ['notifications-unread-count'] })
    },
  })

  const markAllRead = useMutation({
    mutationFn: () => apiClient.post('/api/v1/notifications/read-all'),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['notifications'] })
      qc.invalidateQueries({ queryKey: ['notifications-unread-count'] })
    },
  })

  // Shares the exact same query key as the topbar bell badge — deriving
  // "unread count" from only the currently-loaded page would drift from
  // the bell's number the moment more than one page exists.
  const { data: unreadData } = useQuery<{ unreadCount: number }>({
    queryKey: ['notifications-unread-count'],
    queryFn: async () => (await apiClient.get('/api/v1/notifications/unread-count')).data,
    enabled: open,
  })
  const unreadCount = unreadData?.unreadCount ?? 0

  // Click behavior: always mark read immediately (optimistic). If there's
  // an actionUrl, navigate there and close the drawer — you're now doing
  // the thing the notification was about. If there isn't one, just mark
  // read and leave the drawer open: the full message is already shown in
  // the list (never truncated), so a separate detail view would just be
  // an extra click to see text that's already visible.
  const handleClick = (n: Notification) => {
    if (!n.read) markRead.mutate(n.id)
    if (n.actionUrl) {
      const safeUrl = resolveSafeActionUrl(n.actionUrl)
      if (safeUrl) { navigate(safeUrl); onClose() }
      // else: actionUrl doesn't target any known route — do nothing rather
      // than bounce to dashboard on a genuinely broken link. The
      // notification stays marked read either way.
    }
  }

  if (!open) return null

  return (
    <>
      <div onClick={onClose} style={{ position: 'fixed', inset: 0, background: 'rgba(15,23,42,0.35)', zIndex: 400, animation: 'fadeIn 0.15s ease' }} />
      <div style={{
        position: 'fixed', top: 0, right: 0, bottom: 0, width: 400, maxWidth: '90vw',
        background: 'white', zIndex: 401, boxShadow: '-8px 0 32px rgba(0,0,0,0.15)',
        display: 'flex', flexDirection: 'column', animation: 'slideIn 0.2s ease',
      }}>
        <style>{`
          @keyframes slideIn { from { transform: translateX(100%); } to { transform: translateX(0); } }
          @keyframes fadeIn { from { opacity: 0; } to { opacity: 1; } }
          @keyframes notifSpin { from { transform: rotate(0deg); } to { transform: rotate(360deg); } }
        `}</style>

        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '18px 20px', borderBottom: '1px solid #F1F5F9', flexShrink: 0 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <h3 style={{ margin: 0, fontSize: 16, fontWeight: 700, color: '#0F172A' }}>Notifications</h3>
            {unreadCount > 0 && (
              <span style={{ background: '#1B3A6B', color: 'white', fontSize: 11, fontWeight: 700, padding: '1px 7px', borderRadius: 10 }}>{unreadCount}</span>
            )}
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
            {unreadCount > 0 && (
              <button onClick={() => markAllRead.mutate()} title="Mark all as read"
                style={{ display: 'flex', alignItems: 'center', gap: 4, background: 'none', border: 'none', cursor: 'pointer', color: '#64748B', fontSize: 12, padding: '4px 6px' }}>
                <CheckCheck size={13} /> Mark all read
              </button>
            )}
            <button onClick={onClose} style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#94A3B8', display: 'flex', padding: 4 }}><X size={18} /></button>
          </div>
        </div>

        <div style={{ display: 'flex', gap: 6, padding: '10px 20px', borderBottom: '1px solid #F1F5F9', flexShrink: 0 }}>
          {[{ label: 'All', val: false }, { label: 'Unread', val: true }].map(f => (
            <button key={f.label} onClick={() => resetPaging(f.val)}
              style={{
                padding: '5px 12px', borderRadius: 20, fontSize: 12, fontWeight: 600, cursor: 'pointer',
                border: unreadOnly === f.val ? '1px solid #1B3A6B' : '1px solid #E2E8F0',
                background: unreadOnly === f.val ? '#EFF6FF' : 'white',
                color: unreadOnly === f.val ? '#1B3A6B' : '#64748B',
              }}>
              {f.label}
            </button>
          ))}
        </div>

        <div style={{ flex: 1, overflowY: 'auto' }}>
          {isLoading ? (
            <div style={{ padding: 40, textAlign: 'center', color: '#94A3B8', fontSize: 13 }}>Loading...</div>
          ) : isError ? (
            <div style={{ padding: 40, textAlign: 'center', color: '#94A3B8', fontSize: 13 }}>Couldn't load notifications. Please try again.</div>
          ) : notifications.length === 0 ? (
            <div style={{ padding: '60px 20px', textAlign: 'center' }}>
              <Bell size={32} color="#CBD5E1" style={{ marginBottom: 10 }} />
              <div style={{ fontSize: 13, color: '#94A3B8' }}>{unreadOnly ? "No unread notifications" : "You're all caught up"}</div>
            </div>
          ) : (
            <>
              {notifications.map(n => (
                <div key={n.id} onClick={() => handleClick(n)}
                  style={{
                    display: 'flex', gap: 10, padding: '14px 20px', cursor: 'pointer',
                    borderBottom: '1px solid #F8FAFC', background: n.read ? 'white' : '#F0F9FF',
                  }}
                  onMouseEnter={e => { e.currentTarget.style.background = n.read ? '#FAFBFC' : '#E0F2FE' }}
                  onMouseLeave={e => { e.currentTarget.style.background = n.read ? 'white' : '#F0F9FF' }}>
                  <div style={{ width: 7, height: 7, borderRadius: '50%', background: n.read ? 'transparent' : '#0D9488', flexShrink: 0, marginTop: 6 }} />
                  <div style={{ flex: 1, minWidth: 0 }}>
                    <div style={{ fontSize: 13, fontWeight: n.read ? 500 : 700, color: '#0F172A', marginBottom: 2 }}>{n.title}</div>
                    <div style={{ fontSize: 12.5, color: '#64748B', lineHeight: 1.4 }}>{n.message}</div>
                    <div style={{ fontSize: 11, color: '#94A3B8', marginTop: 4 }}>{timeAgo(n.createdAt)}</div>
                  </div>
                </div>
              ))}
              {hasMore && (
                <div style={{ padding: '14px 20px', textAlign: 'center' }}>
                  <button onClick={loadMore} disabled={isFetching}
                    style={{ display: 'inline-flex', alignItems: 'center', gap: 6, padding: '8px 16px', border: '1px solid #E2E8F0', borderRadius: 8, background: 'white', color: '#1B3A6B', fontSize: 12.5, fontWeight: 600, cursor: 'pointer' }}>
                    {isFetching ? <><Loader2 size={13} style={{ animation: 'notifSpin 0.8s linear infinite' }} /> Loading...</> : 'Load more'}
                  </button>
                </div>
              )}
            </>
          )}
        </div>
      </div>
    </>
  )
}

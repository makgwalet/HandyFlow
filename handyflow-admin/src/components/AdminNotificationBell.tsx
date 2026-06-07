// src/components/AdminNotificationBell.tsx
// Drop-in component for AdminLayout header — shows unread badge + popover list
// Uses EventSource (SSE) for real-time push, falls back to polling.

import { useState, useEffect, useRef, useCallback } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { adminApi } from '../api/client'
import { Bell, X, Building2, AlertTriangle, CheckCircle, TrendingUp, Package, FileText, Clock } from 'lucide-react'

interface AdminNotification {
  id: string
  type: string
  title: string
  body: string
  tenant_name: string | null
  tenant_slug: string | null
  metadata: Record<string, string> | null
  created_at: string
  unread: boolean
}

const TYPE_CONFIG: Record<string, { color: string; bg: string; icon: any; dot: string }> = {
  TENANT_SIGNED_UP:   { color: '#68D391', bg: '#16653420', icon: Building2,     dot: '#68D391' },
  PILOT_EXPIRING:     { color: '#F6AD55', bg: '#D9770620', icon: Clock,          dot: '#F6AD55' },
  PILOT_CONVERTED:    { color: '#0D9488', bg: '#0D948820', icon: TrendingUp,     dot: '#0D9488' },
  TENANT_SUSPENDED:   { color: '#FC8181', bg: '#DC262620', icon: AlertTriangle,  dot: '#FC8181' },
  TENANT_REACTIVATED: { color: '#68D391', bg: '#16653420', icon: CheckCircle,    dot: '#68D391' },
  INVOICE_PAID:       { color: '#68D391', bg: '#16653420', icon: FileText,       dot: '#68D391' },
  INVOICE_OVERDUE:    { color: '#FC8181', bg: '#DC262620', icon: FileText,       dot: '#FC8181' },
  INCIDENT_RAISED:    { color: '#FC8181', bg: '#DC262620', icon: AlertTriangle,  dot: '#FC8181' },
  MODULE_ACTIVATED:   { color: '#60A5FA', bg: '#1D4ED820', icon: Package,        dot: '#60A5FA' },
  MODULE_CANCELLED:   { color: '#718096', bg: '#2D374820', icon: Package,        dot: '#718096' },
}

const fmtAgo = (d: string) => {
  const diff = Date.now() - new Date(d).getTime()
  const mins = Math.floor(diff / 60000)
  if (mins < 1)  return 'Just now'
  if (mins < 60) return `${mins}m ago`
  const hrs = Math.floor(mins / 60)
  if (hrs < 24)  return `${hrs}h ago`
  return `${Math.floor(hrs / 24)}d ago`
}

export function AdminNotificationBell() {
  const qc = useQueryClient()
  const [open,       setOpen]       = useState(false)
  const [sseReady,   setSseReady]   = useState(false)
  const popoverRef                  = useRef<HTMLDivElement>(null)
  const esRef                       = useRef<EventSource | null>(null)

  // Load notifications list
  const { data: notifications = [], refetch } = useQuery<AdminNotification[]>({
    queryKey: ['admin-notifications'],
    queryFn: async () => {
      const r = await adminApi.get('/notifications?limit=50')
      return r.data?.data ?? r.data ?? []
    },
    refetchInterval: 60_000, // fallback polling every 60s
  })

  // Unread count
  const { data: countData } = useQuery({
    queryKey: ['admin-unread-count'],
    queryFn: async () => {
      const r = await adminApi.get('/notifications/unread-count')
      return r.data?.data ?? r.data ?? { count: 0 }
    },
    refetchInterval: 30_000,
  })

  const unreadCount = countData?.count ?? 0

  // SSE connection — reconnects automatically
  useEffect(() => {
    const token = localStorage.getItem('hf_admin_token')
    if (!token) return

    const connect = () => {
      // EventSource doesn't support custom headers — token via cookie or
      // include in URL as query param (acceptable for SSE in internal tools)
      const es = new EventSource(
        `/api/v1/admin/notifications/stream`,
        { withCredentials: false }
      )
      esRef.current = es

      es.addEventListener('notification', (e) => {
        // New notification pushed — add to list and refetch count
        qc.invalidateQueries({ queryKey: ['admin-notifications'] })
        qc.invalidateQueries({ queryKey: ['admin-unread-count'] })
      })

      es.addEventListener('ping', () => {
        setSseReady(true)
      })

      es.onerror = () => {
        setSseReady(false)
        es.close()
        // Reconnect after 5s
        setTimeout(connect, 5000)
      }
    }

    connect()
    return () => { esRef.current?.close() }
  }, [])

  // Close popover on outside click
  useEffect(() => {
    const handler = (e: MouseEvent) => {
      if (popoverRef.current && !popoverRef.current.contains(e.target as Node)) {
        setOpen(false)
      }
    }
    document.addEventListener('mousedown', handler)
    return () => document.removeEventListener('mousedown', handler)
  }, [])

  const markRead = useMutation({
    mutationFn: (id: string) => adminApi.post(`/notifications/${id}/read`),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['admin-notifications'] }); qc.invalidateQueries({ queryKey: ['admin-unread-count'] }) },
  })

  const markAllRead = useMutation({
    mutationFn: () => adminApi.post('/notifications/read-all'),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['admin-notifications'] }); qc.invalidateQueries({ queryKey: ['admin-unread-count'] }) },
  })

  const unreadNotifs = notifications.filter(n => n.unread)

  return (
    <div ref={popoverRef} style={{ position: 'relative' as const }}>
      {/* Bell button */}
      <button
        onClick={() => setOpen(p => !p)}
        style={{
          position: 'relative' as const, background: open ? '#1A202C' : 'none',
          border: '1.5px solid', borderColor: open ? '#2D3748' : 'transparent',
          borderRadius: 8, width: 36, height: 36, display: 'flex',
          alignItems: 'center', justifyContent: 'center',
          cursor: 'pointer', color: '#718096', transition: 'all 0.12s',
        }}
        onMouseEnter={e => { if (!open) (e.currentTarget as HTMLElement).style.background = '#1A202C' }}
        onMouseLeave={e => { if (!open) (e.currentTarget as HTMLElement).style.background = 'none' }}>
        <Bell size={16} />
        {unreadCount > 0 && (
          <div style={{
            position: 'absolute' as const, top: -4, right: -4,
            width: 16, height: 16, borderRadius: '50%',
            background: '#FC8181', border: '2px solid #13161E',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            fontSize: 9, fontWeight: 800, color: '#fff',
          }}>
            {unreadCount > 9 ? '9+' : unreadCount}
          </div>
        )}
        {/* SSE live indicator */}
        <div style={{
          position: 'absolute' as const, bottom: 4, right: 4,
          width: 5, height: 5, borderRadius: '50%',
          background: sseReady ? '#0D9488' : '#4A5568',
        }} />
      </button>

      {/* Popover */}
      {open && (
        <div style={{
          position: 'absolute' as const, right: 0, top: '110%', zIndex: 2000,
          width: 360, background: '#13161E', border: '1px solid #2D3748',
          borderRadius: 12, boxShadow: '0 20px 60px rgba(0,0,0,0.5)',
          overflow: 'hidden',
        }}>
          {/* Header */}
          <div style={{ padding: '12px 16px', borderBottom: '1px solid #1E2532', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <div>
              <div style={{ fontSize: 13, fontWeight: 700, color: '#F7FAFC' }}>
                Notifications
                {unreadCount > 0 && (
                  <span style={{ marginLeft: 8, background: '#FC818130', color: '#FC8181', padding: '1px 7px', borderRadius: 20, fontSize: 11, fontWeight: 700 }}>
                    {unreadCount} unread
                  </span>
                )}
              </div>
              <div style={{ fontSize: 10, color: '#4A5568', marginTop: 2 }}>
                {sseReady ? '● Live' : '○ Polling'} · Last {fmtAgo(notifications[0]?.created_at ?? new Date().toISOString())}
              </div>
            </div>
            <div style={{ display: 'flex', gap: 6 }}>
              {unreadCount > 0 && (
                <button onClick={() => markAllRead.mutate()}
                  style={{ fontSize: 11, color: '#0D9488', background: 'none', border: 'none', cursor: 'pointer', fontWeight: 600 }}>
                  Mark all read
                </button>
              )}
              <button onClick={() => setOpen(false)}
                style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#4A5568', display: 'flex' }}>
                <X size={14} />
              </button>
            </div>
          </div>

          {/* Notification list */}
          <div style={{ maxHeight: 420, overflowY: 'auto' }}>
            {notifications.length === 0 ? (
              <div style={{ padding: '40px 20px', textAlign: 'center', color: '#4A5568' }}>
                <Bell size={28} style={{ marginBottom: 10, opacity: 0.2 }} />
                <div style={{ fontSize: 13, fontWeight: 600, color: '#718096' }}>No notifications yet</div>
                <div style={{ fontSize: 12, marginTop: 4 }}>Lifecycle events will appear here in real time.</div>
              </div>
            ) : notifications.map(n => {
              const cfg   = TYPE_CONFIG[n.type] ?? TYPE_CONFIG.TENANT_SIGNED_UP
              const Icon  = cfg.icon
              return (
                <div key={n.id}
                  onClick={() => { if (n.unread) markRead.mutate(n.id) }}
                  style={{
                    display: 'flex', gap: 12, padding: '12px 16px',
                    borderBottom: '1px solid #1E2532',
                    background: n.unread ? '#1A202C' : 'transparent',
                    cursor: n.unread ? 'pointer' : 'default',
                    transition: 'background 0.12s',
                  }}
                  onMouseEnter={e => (e.currentTarget as HTMLElement).style.background = '#1E2532'}
                  onMouseLeave={e => (e.currentTarget as HTMLElement).style.background = n.unread ? '#1A202C' : 'transparent'}>

                  {/* Icon */}
                  <div style={{
                    width: 34, height: 34, borderRadius: 9, background: cfg.bg,
                    display: 'flex', alignItems: 'center', justifyContent: 'center',
                    flexShrink: 0, color: cfg.color,
                  }}>
                    <Icon size={15} />
                  </div>

                  {/* Content */}
                  <div style={{ flex: 1, minWidth: 0 }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 6 }}>
                      <div style={{ fontSize: 13, fontWeight: n.unread ? 700 : 500, color: '#F7FAFC', lineHeight: 1.3 }}>
                        {n.title}
                      </div>
                      <div style={{ fontSize: 10, color: '#4A5568', flexShrink: 0, marginTop: 1 }}>
                        {fmtAgo(n.created_at)}
                      </div>
                    </div>
                    {n.body && (
                      <div style={{ fontSize: 12, color: '#718096', marginTop: 2, lineHeight: 1.4, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' as const }}>
                        {n.body}
                      </div>
                    )}
                    {n.tenant_slug && (
                      <div style={{ fontSize: 10, color: '#4A5568', marginTop: 3, fontFamily: 'monospace' }}>
                        {n.tenant_slug}
                      </div>
                    )}
                  </div>

                  {/* Unread dot */}
                  {n.unread && (
                    <div style={{ width: 7, height: 7, borderRadius: '50%', background: cfg.dot, flexShrink: 0, marginTop: 4 }} />
                  )}
                </div>
              )
            })}
          </div>

          {/* Footer */}
          <div style={{ padding: '8px 16px', borderTop: '1px solid #1E2532', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <div style={{ fontSize: 11, color: '#4A5568' }}>Last 50 notifications</div>
            <button onClick={() => refetch()} style={{ fontSize: 11, color: '#718096', background: 'none', border: 'none', cursor: 'pointer' }}>
              Refresh
            </button>
          </div>
        </div>
      )}
    </div>
  )
}

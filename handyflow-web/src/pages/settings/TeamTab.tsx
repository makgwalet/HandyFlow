import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../../api/client'
import {
  Plus, X, Mail, Shield, UserCheck, UserX,
  ChevronDown, ChevronUp, Check, Pencil, Clock,
} from 'lucide-react'

// ── Types ──────────────────────────────────────────────────────────────────────
interface User {
  id: string; email: string; firstName: string; lastName: string
  phone: string | null; jobTitle: string | null; department: string | null
  status: 'ACTIVE' | 'INACTIVE' | 'LOCKED'
  roles: string[]; permissions: string[]; createdAt: string
}
interface Role {
  id: string; name: string; description: string
  permissions: string[]; userCount: number
}
interface Permission { id: string; name: string; description: string }
interface Invitation {
  id: string; email: string; firstName: string; lastName: string
  jobTitle: string | null; roleName: string; status: string
  expiresAt: string; createdAt: string
}

const STATUS_STYLE: Record<string, { bg: string; color: string; label: string }> = {
  ACTIVE:   { bg: '#DCFCE7', color: '#166534', label: 'Active'      },
  INACTIVE: { bg: '#F1F5F9', color: '#64748B', label: 'Inactive'    },
  LOCKED:   { bg: '#FEF2F2', color: '#DC2626', label: 'Locked'      },
  PENDING:  { bg: '#FEF3C7', color: '#92400E', label: 'Pending'     },
  ACCEPTED: { bg: '#DCFCE7', color: '#166534', label: 'Accepted'    },
  EXPIRED:  { bg: '#F1F5F9', color: '#64748B', label: 'Expired'     },
  CANCELLED:{ bg: '#FEF2F2', color: '#DC2626', label: 'Cancelled'   },
}

const PERMISSION_GROUPS: Record<string, string[]> = {
  'User Management': ['USER_READ','USER_CREATE','USER_UPDATE','USER_DELETE','USER_INVITE','USER_DEACTIVATE'],
  'Role Management': ['ROLE_READ','ROLE_MANAGE'],
  'CRM':            ['CUSTOMER_READ','CUSTOMER_CREATE','CUSTOMER_UPDATE','CUSTOMER_DELETE'],
  'Invoicing':      ['INVOICE_READ','INVOICE_CREATE','INVOICE_SEND','INVOICE_DELETE'],
  'Billing':        ['BILLING_READ','BILLING_MANAGE'],
  'System':         ['REPORT_VIEW','SETTINGS_MANAGE'],
}

export default function TeamTab() {
  const qc = useQueryClient()
  const [activeView, setActiveView] = useState<'users' | 'roles' | 'invites'>('users')
  const [showInvite, setShowInvite]   = useState(false)
  const [showRole, setShowRole]       = useState(false)
  const [editUser, setEditUser]       = useState<User | null>(null)
  const [editRole, setEditRole]       = useState<Role | null>(null)
  const [error, setError]             = useState('')
  const [expandedRole, setExpandedRole] = useState<string | null>(null)

  const [inviteForm, setInviteForm] = useState({
    email: '', firstName: '', lastName: '', jobTitle: '', department: '', roleId: '',
  })
  const [roleForm, setRoleForm] = useState({ name: '', description: '' })
  const [selectedPerms, setSelectedPerms] = useState<Set<string>>(new Set())

  // ── Queries ───────────────────────────────────────────────────────────────
  const { data: users = [], isLoading: loadingUsers } = useQuery<User[]>({
    queryKey: ['team-users'],
    queryFn: async () => (await apiClient.get('/api/v1/identity/users')).data,
  })
  const { data: roles = [] } = useQuery<Role[]>({
    queryKey: ['team-roles'],
    queryFn: async () => (await apiClient.get('/api/v1/identity/roles')).data,
  })
  const { data: permissions = [] } = useQuery<Permission[]>({
    queryKey: ['team-permissions'],
    queryFn: async () => (await apiClient.get('/api/v1/identity/permissions')).data,
  })
  const { data: invitations = [] } = useQuery<Invitation[]>({
    queryKey: ['team-invitations'],
    queryFn: async () => (await apiClient.get('/api/v1/identity/invitations')).data,
  })

  const invalidate = () => {
    qc.invalidateQueries({ queryKey: ['team-users'] })
    qc.invalidateQueries({ queryKey: ['team-invitations'] })
    qc.invalidateQueries({ queryKey: ['team-roles'] })
  }

  // ── Mutations ─────────────────────────────────────────────────────────────
  const inviteMutation = useMutation({
    mutationFn: (body: any) => apiClient.post('/api/v1/identity/users/invite', body),
    onSuccess: () => { invalidate(); setShowInvite(false); setInviteForm({ email: '', firstName: '', lastName: '', jobTitle: '', department: '', roleId: '' }); setError('') },
    onError: (e: any) => setError(e.response?.data?.message || 'Failed to send invitation'),
  })
  const updateUserMutation = useMutation({
    mutationFn: ({ id, body }: { id: string; body: any }) => apiClient.put(`/api/v1/identity/users/${id}`, body),
    onSuccess: () => { invalidate(); setEditUser(null); setError('') },
    onError: (e: any) => setError(e.response?.data?.message || 'Failed to update user'),
  })
  const deactivateMutation = useMutation({
    mutationFn: (id: string) => apiClient.post(`/api/v1/identity/users/${id}/deactivate`),
    onSuccess: () => invalidate(),
  })
  const reactivateMutation = useMutation({
    mutationFn: (id: string) => apiClient.post(`/api/v1/identity/users/${id}/reactivate`),
    onSuccess: () => invalidate(),
  })
  const cancelInviteMutation = useMutation({
    mutationFn: (id: string) => apiClient.delete(`/api/v1/identity/invitations/${id}`),
    onSuccess: () => invalidate(),
  })
  const createRoleMutation = useMutation({
    mutationFn: (body: any) => apiClient.post('/api/v1/identity/roles', body),
    onSuccess: () => { invalidate(); setShowRole(false); setRoleForm({ name: '', description: '' }); setSelectedPerms(new Set()); setError('') },
    onError: (e: any) => setError(e.response?.data?.message || 'Failed to create role'),
  })
  const updateRolePermsMutation = useMutation({
    mutationFn: ({ id, permIds }: { id: string; permIds: string[] }) =>
      apiClient.put(`/api/v1/identity/roles/${id}/permissions`, permIds),
    onSuccess: () => { invalidate(); setEditRole(null); setSelectedPerms(new Set()) },
    onError: (e: any) => setError(e.response?.data?.message || 'Failed to update role'),
  })

  // ── Helpers ───────────────────────────────────────────────────────────────
  const initials = (u: User) => `${u.firstName[0]}${u.lastName[0]}`.toUpperCase()
  const fmtDate  = (d: string) => new Date(d).toLocaleDateString('en-ZA', { day: 'numeric', month: 'short', year: 'numeric' })
  const permMap  = Object.fromEntries(permissions.map(p => [p.name, p.id]))

  const openEditRole = (role: Role) => {
    setEditRole(role)
    setSelectedPerms(new Set(role.permissions.map(pname => permMap[pname]).filter(Boolean)))
    setError('')
  }

  const pendingInvites = invitations.filter(i => i.status === 'PENDING').length

  return (
    <div style={{ fontFamily: "'Inter', system-ui, sans-serif" }}>

      {/* Sub-nav */}
      <div style={{ display: 'flex', gap: 4, borderBottom: '1px solid #E2E8F0', marginBottom: 24 }}>
        {[
          { id: 'users' as const,   label: `Team (${users.length})` },
          { id: 'roles' as const,   label: `Roles (${roles.length})` },
          { id: 'invites' as const, label: `Invitations${pendingInvites > 0 ? ` (${pendingInvites} pending)` : ''}` },
        ].map(t => (
          <button key={t.id} onClick={() => setActiveView(t.id)}
            style={{ padding: '8px 16px', background: 'none', border: 'none', cursor: 'pointer', fontSize: 14, fontWeight: activeView === t.id ? 600 : 400, color: activeView === t.id ? '#0D9488' : '#64748B', borderBottom: activeView === t.id ? '2px solid #0D9488' : '2px solid transparent', marginBottom: -1 }}>
            {t.label}
          </button>
        ))}
      </div>

      {/* ── USERS ──────────────────────────────────────────────────────── */}
      {activeView === 'users' && (
        <div>
          <div style={{ display: 'flex', justifyContent: 'flex-end', marginBottom: 16 }}>
            <button onClick={() => { setShowInvite(true); setError('') }}
              style={{ display: 'flex', alignItems: 'center', gap: 7, background: '#1B3A6B', color: 'white', border: 'none', borderRadius: 9, padding: '10px 18px', fontSize: 14, fontWeight: 600, cursor: 'pointer' }}>
              <Plus size={15} /> Invite user
            </button>
          </div>

          {loadingUsers ? (
            <div style={{ textAlign: 'center', padding: 48, color: '#94A3B8' }}>Loading team...</div>
          ) : (
            <div style={{ background: 'white', border: '1px solid #E2E8F0', borderRadius: 14, overflow: 'hidden' }}>
              <table style={{ width: '100%', borderCollapse: 'collapse' }}>
                <thead>
                  <tr style={{ background: '#F8FAFC', borderBottom: '1px solid #F1F5F9' }}>
                    {['Team member', 'Role', 'Department', 'Status', ''].map(h => (
                      <th key={h} style={{ textAlign: 'left', padding: '10px 16px', fontSize: 11, fontWeight: 700, color: '#94A3B8', textTransform: 'uppercase', letterSpacing: '0.05em' }}>{h}</th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {users.map(u => {
                    const ss = STATUS_STYLE[u.status] || STATUS_STYLE.ACTIVE
                    return (
                      <tr key={u.id} style={{ borderBottom: '1px solid #F8FAFC' }}
                        onMouseEnter={e => (e.currentTarget.style.background = '#F8FAFC')}
                        onMouseLeave={e => (e.currentTarget.style.background = 'white')}>
                        <td style={{ padding: '14px 16px' }}>
                          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                            <div style={{ width: 36, height: 36, borderRadius: '50%', background: '#EFF6FF', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 12, fontWeight: 700, color: '#1D4ED8', flexShrink: 0 }}>
                              {initials(u)}
                            </div>
                            <div>
                              <div style={{ fontWeight: 700, fontSize: 14, color: '#0F172A' }}>{u.firstName} {u.lastName}</div>
                              <div style={{ fontSize: 12, color: '#64748B' }}>{u.email}</div>
                            </div>
                          </div>
                        </td>
                        <td style={{ padding: '14px 16px', fontSize: 13, color: '#374151' }}>
                          {u.roles.map(r => (
                            <span key={r} style={{ background: '#EFF6FF', color: '#1D4ED8', padding: '2px 8px', borderRadius: 20, fontSize: 11, fontWeight: 600, marginRight: 4 }}>{r}</span>
                          ))}
                        </td>
                        <td style={{ padding: '14px 16px', fontSize: 13, color: '#64748B' }}>
                          {u.department || u.jobTitle || '—'}
                        </td>
                        <td style={{ padding: '14px 16px' }}>
                          <span style={{ background: ss.bg, color: ss.color, padding: '3px 9px', borderRadius: 20, fontSize: 12, fontWeight: 600 }}>{ss.label}</span>
                        </td>
                        <td style={{ padding: '14px 16px' }}>
                          <div style={{ display: 'flex', gap: 6, justifyContent: 'flex-end' }}>
                            <button onClick={() => { setEditUser(u); setError('') }}
                              style={{ display: 'flex', alignItems: 'center', gap: 5, padding: '5px 10px', background: '#EFF6FF', border: '1px solid #BFDBFE', borderRadius: 7, fontSize: 12, color: '#1D4ED8', cursor: 'pointer' }}>
                              <Pencil size={12} /> Edit
                            </button>
                            {u.status === 'ACTIVE' ? (
                              <button onClick={() => { if (confirm(`Deactivate ${u.firstName}?`)) deactivateMutation.mutate(u.id) }}
                                style={{ display: 'flex', alignItems: 'center', gap: 5, padding: '5px 10px', background: '#FEF2F2', border: '1px solid #FECACA', borderRadius: 7, fontSize: 12, color: '#DC2626', cursor: 'pointer' }}>
                                <UserX size={12} /> Deactivate
                              </button>
                            ) : (
                              <button onClick={() => reactivateMutation.mutate(u.id)}
                                style={{ display: 'flex', alignItems: 'center', gap: 5, padding: '5px 10px', background: '#F0FDF4', border: '1px solid #BBF7D0', borderRadius: 7, fontSize: 12, color: '#166534', cursor: 'pointer' }}>
                                <UserCheck size={12} /> Reactivate
                              </button>
                            )}
                          </div>
                        </td>
                      </tr>
                    )
                  })}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}

      {/* ── ROLES ──────────────────────────────────────────────────────── */}
      {activeView === 'roles' && (
        <div>
          <div style={{ display: 'flex', justifyContent: 'flex-end', marginBottom: 16 }}>
            <button onClick={() => { setShowRole(true); setSelectedPerms(new Set()); setError('') }}
              style={{ display: 'flex', alignItems: 'center', gap: 7, background: '#1B3A6B', color: 'white', border: 'none', borderRadius: 9, padding: '10px 18px', fontSize: 14, fontWeight: 600, cursor: 'pointer' }}>
              <Plus size={15} /> Create role
            </button>
          </div>

          <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
            {roles.map(role => {
              const isExpanded = expandedRole === role.id
              const isAdmin = role.name === 'ADMIN'
              return (
                <div key={role.id} style={{ background: 'white', border: '1px solid #E2E8F0', borderRadius: 12, overflow: 'hidden' }}>
                  <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '16px 20px', cursor: 'pointer' }}
                    onClick={() => setExpandedRole(isExpanded ? null : role.id)}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                      <div style={{ width: 36, height: 36, borderRadius: 9, background: isAdmin ? '#EFF6FF' : '#F0FDF4', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                        <Shield size={16} color={isAdmin ? '#1D4ED8' : '#0D9488'} />
                      </div>
                      <div>
                        <div style={{ fontWeight: 700, fontSize: 14, color: '#0F172A', display: 'flex', alignItems: 'center', gap: 8 }}>
                          {role.name}
                          {isAdmin && <span style={{ background: '#EFF6FF', color: '#1D4ED8', padding: '1px 8px', borderRadius: 20, fontSize: 11, fontWeight: 600 }}>System</span>}
                        </div>
                        <div style={{ fontSize: 12, color: '#64748B' }}>
                          {role.description || '—'} · {role.userCount} user{role.userCount !== 1 ? 's' : ''} · {role.permissions.size || role.permissions.length} permissions
                        </div>
                      </div>
                    </div>
                    <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
                      {!isAdmin && (
                        <button onClick={e => { e.stopPropagation(); openEditRole(role) }}
                          style={{ display: 'flex', alignItems: 'center', gap: 5, padding: '5px 10px', background: '#EFF6FF', border: '1px solid #BFDBFE', borderRadius: 7, fontSize: 12, color: '#1D4ED8', cursor: 'pointer' }}>
                          <Pencil size={12} /> Edit permissions
                        </button>
                      )}
                      {isExpanded ? <ChevronUp size={15} color="#94A3B8" /> : <ChevronDown size={15} color="#94A3B8" />}
                    </div>
                  </div>
                  {isExpanded && (
                    <div style={{ borderTop: '1px solid #F1F5F9', padding: '14px 20px', background: '#F8FAFC' }}>
                      <div style={{ fontSize: 11, fontWeight: 700, color: '#94A3B8', letterSpacing: '0.06em', marginBottom: 10 }}>PERMISSIONS</div>
                      <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
                        {[...(role.permissions as unknown as string[])].sort().map(pname => (
                          <span key={pname} style={{ background: 'white', border: '1px solid #E2E8F0', padding: '3px 10px', borderRadius: 20, fontSize: 11, color: '#374151' }}>{pname}</span>
                        ))}
                      </div>
                    </div>
                  )}
                </div>
              )
            })}
          </div>
        </div>
      )}

      {/* ── INVITATIONS ────────────────────────────────────────────────── */}
      {activeView === 'invites' && (
        <div>
          {invitations.length === 0 ? (
            <div style={{ textAlign: 'center', padding: '48px 20px', color: '#94A3B8' }}>
              <Mail size={32} style={{ marginBottom: 12, opacity: 0.3 }} />
              <div style={{ fontWeight: 600, color: '#475569' }}>No invitations sent yet</div>
              <div style={{ fontSize: 13, marginTop: 4 }}>Invite your team members from the Team tab.</div>
            </div>
          ) : (
            <div style={{ background: 'white', border: '1px solid #E2E8F0', borderRadius: 14, overflow: 'hidden' }}>
              <table style={{ width: '100%', borderCollapse: 'collapse' }}>
                <thead>
                  <tr style={{ background: '#F8FAFC', borderBottom: '1px solid #F1F5F9' }}>
                    {['Invited person', 'Role', 'Sent', 'Expires', 'Status', ''].map(h => (
                      <th key={h} style={{ textAlign: 'left', padding: '10px 16px', fontSize: 11, fontWeight: 700, color: '#94A3B8', textTransform: 'uppercase', letterSpacing: '0.05em' }}>{h}</th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {invitations.map(inv => {
                    const ss = STATUS_STYLE[inv.status] || STATUS_STYLE.PENDING
                    return (
                      <tr key={inv.id} style={{ borderBottom: '1px solid #F8FAFC' }}>
                        <td style={{ padding: '14px 16px' }}>
                          <div style={{ fontWeight: 600, fontSize: 14, color: '#0F172A' }}>{inv.firstName} {inv.lastName}</div>
                          <div style={{ fontSize: 12, color: '#64748B' }}>{inv.email}</div>
                        </td>
                        <td style={{ padding: '14px 16px', fontSize: 13 }}>
                          <span style={{ background: '#EFF6FF', color: '#1D4ED8', padding: '2px 8px', borderRadius: 20, fontSize: 11, fontWeight: 600 }}>{inv.roleName}</span>
                        </td>
                        <td style={{ padding: '14px 16px', fontSize: 13, color: '#64748B' }}>{fmtDate(inv.createdAt)}</td>
                        <td style={{ padding: '14px 16px', fontSize: 13, color: '#64748B' }}>
                          <div style={{ display: 'flex', alignItems: 'center', gap: 5 }}>
                            <Clock size={12} /> {fmtDate(inv.expiresAt)}
                          </div>
                        </td>
                        <td style={{ padding: '14px 16px' }}>
                          <span style={{ background: ss.bg, color: ss.color, padding: '3px 9px', borderRadius: 20, fontSize: 12, fontWeight: 600 }}>{ss.label}</span>
                        </td>
                        <td style={{ padding: '14px 16px' }}>
                          {inv.status === 'PENDING' && (
                            <button onClick={() => { if (confirm('Cancel this invitation?')) cancelInviteMutation.mutate(inv.id) }}
                              style={{ padding: '5px 10px', background: '#FEF2F2', border: '1px solid #FECACA', borderRadius: 7, fontSize: 12, color: '#DC2626', cursor: 'pointer' }}>
                              Cancel
                            </button>
                          )}
                        </td>
                      </tr>
                    )
                  })}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}

      {/* ── INVITE MODAL ───────────────────────────────────────────────── */}
      {showInvite && (
        <Modal title="Invite team member" onClose={() => { setShowInvite(false); setError('') }}>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
              <Field label="First name *"><input value={inviteForm.firstName} onChange={e => setInviteForm(f => ({ ...f, firstName: e.target.value }))} placeholder="Jane" style={inp} autoFocus /></Field>
              <Field label="Last name *"><input value={inviteForm.lastName} onChange={e => setInviteForm(f => ({ ...f, lastName: e.target.value }))} placeholder="Dlamini" style={inp} /></Field>
            </div>
            <Field label="Work email *"><input type="email" value={inviteForm.email} onChange={e => setInviteForm(f => ({ ...f, email: e.target.value }))} placeholder="jane@company.co.za" style={inp} /></Field>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
              <Field label="Job title"><input value={inviteForm.jobTitle} onChange={e => setInviteForm(f => ({ ...f, jobTitle: e.target.value }))} placeholder="Sales Manager" style={inp} /></Field>
              <Field label="Department"><input value={inviteForm.department} onChange={e => setInviteForm(f => ({ ...f, department: e.target.value }))} placeholder="Sales" style={inp} /></Field>
            </div>
            <Field label="Role">
              <select value={inviteForm.roleId} onChange={e => setInviteForm(f => ({ ...f, roleId: e.target.value }))} style={{ ...inp, background: 'white' }}>
                <option value="">Select a role...</option>
                {roles.map(r => <option key={r.id} value={r.id}>{r.name}{r.description ? ` — ${r.description}` : ''}</option>)}
              </select>
            </Field>
            <div style={{ padding: '10px 14px', background: '#F0FDF4', border: '1px solid #BBF7D0', borderRadius: 8, fontSize: 13, color: '#166534' }}>
              An invitation email will be sent. The invite expires in 72 hours. The user sets their own password on acceptance.
            </div>
          </div>
          {error && <ErrMsg msg={error} />}
          <Footer
            onCancel={() => { setShowInvite(false); setError('') }}
            onSubmit={() => inviteMutation.mutate({
              email: inviteForm.email, firstName: inviteForm.firstName,
              lastName: inviteForm.lastName, jobTitle: inviteForm.jobTitle || null,
              department: inviteForm.department || null,
              roleId: inviteForm.roleId || null,
            })}
            loading={inviteMutation.isPending}
            disabled={!inviteForm.email || !inviteForm.firstName || !inviteForm.lastName}
            label="Send invitation"
          />
        </Modal>
      )}

      {/* ── EDIT USER MODAL ────────────────────────────────────────────── */}
      {editUser && (
        <Modal title={`Edit — ${editUser.firstName} ${editUser.lastName}`} onClose={() => { setEditUser(null); setError('') }}>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
              <Field label="First name">
                <input defaultValue={editUser.firstName} id="eu-fn" style={inp} />
              </Field>
              <Field label="Last name">
                <input defaultValue={editUser.lastName} id="eu-ln" style={inp} />
              </Field>
            </div>
            <Field label="Job title">
              <input defaultValue={editUser.jobTitle ?? ''} id="eu-jt" placeholder="Sales Manager" style={inp} />
            </Field>
            <Field label="Department">
              <input defaultValue={editUser.department ?? ''} id="eu-dept" placeholder="Sales" style={inp} />
            </Field>
            <Field label="Change role">
              <select id="eu-role" defaultValue={''} style={{ ...inp, background: 'white' }}>
                <option value="">Keep current role ({editUser.roles.join(', ')})</option>
                {roles.map(r => <option key={r.id} value={r.id}>{r.name}</option>)}
              </select>
            </Field>
          </div>
          {error && <ErrMsg msg={error} />}
          <Footer
            onCancel={() => { setEditUser(null); setError('') }}
            onSubmit={() => {
              const fn   = (document.getElementById('eu-fn')   as HTMLInputElement).value
              const ln   = (document.getElementById('eu-ln')   as HTMLInputElement).value
              const jt   = (document.getElementById('eu-jt')   as HTMLInputElement).value
              const dept = (document.getElementById('eu-dept') as HTMLInputElement).value
              const role = (document.getElementById('eu-role') as HTMLSelectElement).value
              updateUserMutation.mutate({ id: editUser.id, body: { firstName: fn, lastName: ln, jobTitle: jt || null, department: dept || null, roleId: role || null } })
            }}
            loading={updateUserMutation.isPending}
            disabled={false}
            label="Save changes"
          />
        </Modal>
      )}

      {/* ── CREATE ROLE MODAL ──────────────────────────────────────────── */}
      {showRole && (
        <Modal title="Create role" onClose={() => { setShowRole(false); setError('') }} wide>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 14, marginBottom: 20 }}>
            <Field label="Role name *"><input value={roleForm.name} onChange={e => setRoleForm(f => ({ ...f, name: e.target.value }))} placeholder="MANAGER" style={inp} autoFocus /></Field>
            <Field label="Description"><input value={roleForm.description} onChange={e => setRoleForm(f => ({ ...f, description: e.target.value }))} placeholder="Department managers with reporting access" style={inp} /></Field>
          </div>
          <PermissionPicker
            permissions={permissions}
            selected={selectedPerms}
            onToggle={pid => setSelectedPerms(prev => {
              const next = new Set(prev)
              next.has(pid) ? next.delete(pid) : next.add(pid)
              return next
            })}
          />
          {error && <ErrMsg msg={error} />}
          <Footer
            onCancel={() => { setShowRole(false); setError('') }}
            onSubmit={() => createRoleMutation.mutate({ name: roleForm.name, description: roleForm.description, permissionIds: Array.from(selectedPerms) })}
            loading={createRoleMutation.isPending}
            disabled={!roleForm.name}
            label="Create role"
          />
        </Modal>
      )}

      {/* ── EDIT ROLE PERMISSIONS MODAL ────────────────────────────────── */}
      {editRole && (
        <Modal title={`Permissions — ${editRole.name}`} onClose={() => { setEditRole(null); setError('') }} wide>
          <PermissionPicker
            permissions={permissions}
            selected={selectedPerms}
            onToggle={pid => setSelectedPerms(prev => {
              const next = new Set(prev)
              next.has(pid) ? next.delete(pid) : next.add(pid)
              return next
            })}
          />
          {error && <ErrMsg msg={error} />}
          <Footer
            onCancel={() => { setEditRole(null); setError('') }}
            onSubmit={() => updateRolePermsMutation.mutate({ id: editRole.id, permIds: Array.from(selectedPerms) })}
            loading={updateRolePermsMutation.isPending}
            disabled={false}
            label="Save permissions"
          />
        </Modal>
      )}
    </div>
  )
}

// ── Permission picker component ────────────────────────────────────────────────
function PermissionPicker({ permissions, selected, onToggle }: {
  permissions: Permission[]
  selected: Set<string>
  onToggle: (id: string) => void
}) {
  const permById = Object.fromEntries(permissions.map(p => [p.id, p]))
  return (
    <div>
      <div style={{ fontSize: 13, fontWeight: 600, color: '#374151', marginBottom: 12 }}>
        Permissions — {selected.size} selected
      </div>
      {Object.entries(PERMISSION_GROUPS).map(([group, permNames]) => {
        const groupPerms = permissions.filter(p => permNames.includes(p.name))
        if (groupPerms.length === 0) return null
        return (
          <div key={group} style={{ marginBottom: 14 }}>
            <div style={{ fontSize: 11, fontWeight: 700, color: '#94A3B8', letterSpacing: '0.06em', marginBottom: 8 }}>{group.toUpperCase()}</div>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 6 }}>
              {groupPerms.map(p => {
                const checked = selected.has(p.id)
                return (
                  <div key={p.id} onClick={() => onToggle(p.id)}
                    style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '8px 12px', border: `1px solid ${checked ? '#0D9488' : '#E2E8F0'}`, borderRadius: 8, cursor: 'pointer', background: checked ? '#F0FDF4' : 'white', transition: 'all 0.12s' }}>
                    <div style={{ width: 16, height: 16, borderRadius: 4, border: `1.5px solid ${checked ? '#0D9488' : '#D1D5DB'}`, background: checked ? '#0D9488' : 'white', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
                      {checked && <Check size={10} color="white" strokeWidth={3} />}
                    </div>
                    <div>
                      <div style={{ fontSize: 12, fontWeight: 600, color: '#0F172A' }}>{p.name}</div>
                      <div style={{ fontSize: 11, color: '#94A3B8' }}>{p.description}</div>
                    </div>
                  </div>
                )
              })}
            </div>
          </div>
        )
      })}
    </div>
  )
}

// ── Shared helpers ─────────────────────────────────────────────────────────────
function Modal({ title, onClose, children, wide }: { title: string; onClose: () => void; children: React.ReactNode; wide?: boolean }) {
  return (
    <div style={{ position: 'fixed', inset: 0, background: 'rgba(15,23,42,0.5)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000 }}>
      <div style={{ background: 'white', borderRadius: 16, padding: 28, width: wide ? 680 : 520, maxHeight: '88vh', overflowY: 'auto', boxShadow: '0 20px 60px rgba(0,0,0,0.2)' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 22 }}>
          <h3 style={{ margin: 0, fontSize: 17, fontWeight: 700, color: '#0F172A' }}>{title}</h3>
          <button onClick={onClose} style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#94A3B8', display: 'flex' }}><X size={20} /></button>
        </div>
        {children}
      </div>
    </div>
  )
}
function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return <div><label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: '#374151', marginBottom: 5 }}>{label}</label>{children}</div>
}
function ErrMsg({ msg }: { msg: string }) {
  return <div style={{ marginTop: 12, padding: '8px 12px', background: '#FEF2F2', border: '1px solid #FECACA', borderRadius: 8, color: '#DC2626', fontSize: 13 }}>{msg}</div>
}
function Footer({ onCancel, onSubmit, loading, disabled, label }: { onCancel: () => void; onSubmit: () => void; loading: boolean; disabled: boolean; label: string }) {
  return (
    <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 10, marginTop: 22 }}>
      <button onClick={onCancel} style={{ padding: '10px 18px', border: '1px solid #E2E8F0', borderRadius: 9, background: 'white', fontSize: 14, cursor: 'pointer', color: '#374151' }}>Cancel</button>
      <button onClick={onSubmit} disabled={disabled || loading}
        style={{ padding: '10px 22px', background: disabled || loading ? '#94A3B8' : '#1B3A6B', color: 'white', border: 'none', borderRadius: 9, fontSize: 14, fontWeight: 600, cursor: disabled || loading ? 'not-allowed' : 'pointer' }}>
        {loading ? 'Saving...' : label}
      </button>
    </div>
  )
}
const inp: React.CSSProperties = { width: '100%', padding: '10px 12px', border: '1.5px solid #E2E8F0', borderRadius: 9, fontSize: 14, boxSizing: 'border-box' as const }

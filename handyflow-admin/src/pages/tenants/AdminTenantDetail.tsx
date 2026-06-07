// src/pages/tenants/AdminTenantDetail.tsx
// Full implementation — see phase output
import { adminApi } from '../../api/client'
import { useQuery } from '@tanstack/react-query'
import { Building2 } from 'lucide-react'

export function AdminTenantDetail() {
  return (
    <div style={{ color: '#F7FAFC' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 24 }}>
        <div style={{ width: 36, height: 36, borderRadius: 10, background: '#1A202C', border: '1px solid #2D3748', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
          <Building2 size={16} color="#0D9488" />
        </div>
        <h1 style={{ fontSize: 22, fontWeight: 800, margin: 0 }}>Tenant Detail</h1>
      </div>
      <div style={{ background: '#13161E', border: '1px solid #1E2532', borderRadius: 12, padding: 32, textAlign: 'center', color: '#4A5568' }}>
        <Building2 size={40} style={{ marginBottom: 12, opacity: 0.2 }} />
        <div style={{ fontSize: 15, fontWeight: 600, color: '#718096', marginBottom: 6 }}>Coming in next build</div>
        <div style={{ fontSize: 13 }}>This page is scaffolded. Full implementation ships in the next phase.</div>
      </div>
    </div>
  )
}

// src/pages/auth/AccountLockedPage.tsx
//
// Terminal screen for accounts that are SUSPENDED or CANCELLED (trial ended
// past the grace period, or payment failed past the final retry). Distinct
// from the read-only grace period, which keeps the normal app shell visible
// with editing disabled — see RequireActiveAccount below for that branch.
//
// Two reasons get two different messages (never conflate a card issue with
// a trial ending — different anxieties, different fixes) but one visual
// treatment and one CTA target: Billing.

import { useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
// FIX: was '../api/client', '../store/auth.store', '../types/billing.types'
// — correct for src/pages/, wrong for src/pages/auth/ where this file
// actually needs to live (confirmed it never existed anywhere in the real
// project at all until now). One extra ../ on each to reach src/ from the
// actual, one-level-deeper location.
import { apiClient } from '../../api/client'
import { Building2, ShieldAlert, CreditCard, Mail, LogOut } from 'lucide-react'
import { useAuthStore } from '../../store/auth.store'
import type { Subscription } from '../../types/billing.types'

export function AccountLockedPage() {
  const navigate = useNavigate()
  const { logout } = useAuthStore()

  const { data: subscription } = useQuery<Subscription>({
    queryKey: ['subscription'],
    queryFn: async () => (await apiClient.get('/api/v1/billing/subscription')).data,
  })

  const isPastDue = subscription?.status === 'PAST_DUE' || subscription?.status === 'SUSPENDED'

  // FIX: previous copy assumed automated card billing — "update your
  // card", a "payment method" CTA, a CreditCard icon linking to /billing
  // as if there's a card-management UI there. Confirmed nothing in this
  // platform stores or processes card payments at all — the real flow is
  // EFT, with proof of payment sent in afterward for someone to manually
  // reconcile. Rewritten to match that, not a payment mechanism that
  // doesn't exist.
  const copy = isPastDue
    ? {
        title: 'Your account is on hold',
        body: 'We haven\u2019t received your last payment. Please make an EFT payment for your outstanding invoice and email your proof of payment to restore access.',
        cta: 'Email proof of payment',
      }
    : {
        title: 'Your trial has ended',
        body: 'Your 60-day trial (plus grace period) has ended without an active subscription. Your data is retained for 90 days — subscribe any time to pick up right where you left off.',
        cta: 'Choose a plan',
      }

  // Split by scenario rather than one shared handler — "trial ended,
  // pick a plan" and "payment overdue, send proof" are genuinely
  // different actions, not the same button wearing a different label.
  // Doesn't try to pre-fill a tenant name in the mailto subject —
  // Subscription's exact shape isn't confirmed to include one, and
  // guessing at a field that might not exist risks a broken interpolation
  // for the sake of a nice-to-have.
  const handleCtaClick = () => {
    if (isPastDue) {
      window.location.href = 'mailto:accounts@handyflow.co.za?subject=' +
        encodeURIComponent('Proof of payment')
    } else {
      navigate('/billing')
    }
  }

  return (
    <div style={{
      minHeight: '100vh',
      background: 'linear-gradient(135deg, #0F172A 0%, #1B3A6B 55%, #0D9488 100%)',
      display: 'flex', alignItems: 'center', justifyContent: 'center',
      padding: '24px 16px', fontFamily: "'Inter', system-ui, sans-serif",
    }}>
      <div style={{ width: '100%', maxWidth: 460 }}>
        <div style={{ textAlign: 'center', marginBottom: 24 }}>
          <div style={{ display: 'inline-flex', alignItems: 'center', gap: 10 }}>
            <div style={{ width: 38, height: 38, background: '#0D9488', borderRadius: 10, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
              <Building2 size={19} color="white" strokeWidth={2.5} />
            </div>
            <span style={{ fontSize: 20, fontWeight: 800, color: 'white', letterSpacing: '-0.5px' }}>HandyFlow</span>
          </div>
        </div>

        <div style={{ background: 'white', borderRadius: 20, padding: 36, textAlign: 'center', boxShadow: '0 24px 80px rgba(0,0,0,0.3)' }}>
          <div style={{ width: 56, height: 56, borderRadius: '50%', background: '#FEF2F2', display: 'flex', alignItems: 'center', justifyContent: 'center', margin: '0 auto 18px' }}>
            <ShieldAlert size={28} color="#DC2626" />
          </div>
          <h1 style={{ fontSize: 21, fontWeight: 800, color: '#0F172A', margin: '0 0 10px' }}>{copy.title}</h1>
          <p style={{ fontSize: 14, color: '#64748B', lineHeight: 1.6, margin: '0 0 26px' }}>{copy.body}</p>

          <button
            onClick={handleCtaClick}
            style={{ width: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8, background: '#1B3A6B', color: 'white', border: 'none', borderRadius: 10, padding: '13px 24px', fontSize: 15, fontWeight: 700, cursor: 'pointer', marginBottom: 12 }}
          >
            {isPastDue ? <Mail size={16} /> : <CreditCard size={16} />} {copy.cta}
          </button>

          <button
            onClick={() => { logout(); navigate('/login') }}
            style={{ width: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8, background: 'none', color: '#64748B', border: 'none', fontSize: 13, fontWeight: 600, cursor: 'pointer', padding: 8 }}
          >
            <LogOut size={14} /> Sign out
          </button>

          <p style={{ marginTop: 18, fontSize: 12, color: '#94A3B8' }}>
            Need help? <a href="mailto:support@handyflow.co.za" style={{ color: '#1B3A6B', fontWeight: 600, textDecoration: 'none' }}>support@handyflow.co.za</a>
          </p>
        </div>
      </div>
    </div>
  )
}

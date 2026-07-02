// src/pages/projects/TabErrorBoundary.tsx
// React error boundaries must be class components — hooks cannot catch render errors.
// Usage: wrap every tab panel so one broken tab can't crash the whole detail page.
//
//   <TabErrorBoundary tab="budget">
//     <BudgetTab … />
//   </TabErrorBoundary>
//
// Resets automatically when the `tab` prop changes, so switching away from a
// broken tab and back gives a fresh attempt rather than a stuck error screen.

import React from 'react'
import { AlertTriangle, RefreshCw } from 'lucide-react'

interface Props {
  tab: string
  children: React.ReactNode
}

interface State {
  hasError: boolean
  error: Error | null
  errorTab: string | null
}

export class TabErrorBoundary extends React.Component<Props, State> {
  state: State = { hasError: false, error: null, errorTab: null }

  static getDerivedStateFromError(error: Error): Partial<State> {
    return { hasError: true, error }
  }

  static getDerivedStateFromProps(props: Props, state: State): Partial<State> | null {
    // Auto-reset when the user navigates to a different tab
    if (state.hasError && props.tab !== state.errorTab) {
      return { hasError: false, error: null, errorTab: null }
    }
    return null
  }

  componentDidCatch(error: Error, info: React.ErrorInfo) {
    console.error(`[HandyFlow] Tab "${this.props.tab}" render error:`, error, info.componentStack)
    this.setState({ errorTab: this.props.tab })
  }

  handleReset = () => {
    this.setState({ hasError: false, error: null, errorTab: null })
  }

  render() {
    if (!this.state.hasError) return this.props.children

    const msg = this.state.error?.message ?? 'Unknown error'

    return (
      <div style={{
        display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center',
        padding: '60px 24px', textAlign: 'center',
      }}>
        <div style={{
          width: 56, height: 56, borderRadius: '50%', background: '#FEF2F2',
          display: 'flex', alignItems: 'center', justifyContent: 'center', marginBottom: 16,
        }}>
          <AlertTriangle size={26} color="#DC2626" />
        </div>
        <div style={{ fontSize: 16, fontWeight: 700, color: '#0F172A', marginBottom: 6 }}>
          This tab ran into a problem
        </div>
        <div style={{
          fontSize: 13, color: '#64748B', marginBottom: 20,
          maxWidth: 420, lineHeight: 1.6,
        }}>
          {msg}
        </div>
        <button
          onClick={this.handleReset}
          style={{
            display: 'flex', alignItems: 'center', gap: 6,
            padding: '8px 16px', background: '#1B3A6B', color: '#fff',
            border: 'none', borderRadius: 8, fontSize: 13, fontWeight: 600, cursor: 'pointer',
          }}
        >
          <RefreshCw size={13} /> Try again
        </button>
        {process.env.NODE_ENV === 'development' && (
          <details style={{ marginTop: 20, textAlign: 'left', maxWidth: 600, width: '100%' }}>
            <summary style={{ fontSize: 12, color: '#94A3B8', cursor: 'pointer' }}>Stack trace</summary>
            <pre style={{
              fontSize: 11, color: '#DC2626', background: '#FEF2F2',
              padding: 12, borderRadius: 8, overflowX: 'auto', marginTop: 8,
              whiteSpace: 'pre-wrap', wordBreak: 'break-all',
            }}>
              {this.state.error?.stack}
            </pre>
          </details>
        )}
      </div>
    )
  }
}

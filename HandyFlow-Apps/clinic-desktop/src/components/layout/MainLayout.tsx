import { useUIStore } from '@/stores/ui-store'
import { cn } from '@/lib/utils'
import { Sidebar } from './Sidebar'
import { TopBar } from './TopBar'
import { Toast } from '@/components/ui/Toast'
import type { ReactNode } from 'react'

export function MainLayout({ children }: { children: ReactNode }) {
  const { sidebarOpen } = useUIStore()
  return (
    <div className="flex h-screen bg-medical-bg">
      <Sidebar />
      <div className={cn('flex flex-1 flex-col transition-all duration-300', sidebarOpen ? 'ml-64' : 'ml-16')}>
        <TopBar />
        <main className="flex-1 overflow-auto p-6">
          <div className="mx-auto max-w-7xl">{children}</div>
        </main>
      </div>
      <Toast />
    </div>
  )
}
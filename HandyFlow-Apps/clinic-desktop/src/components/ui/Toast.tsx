import { useEffect } from 'react'
import { useUIStore } from '@/stores/ui-store'
import { CheckCircle, AlertCircle, Info, X } from 'lucide-react'
import { cn } from '@/lib/utils'

export function Toast() {
  const { toast, clearToast } = useUIStore()

  useEffect(() => {
    if (toast) {
      const timer = setTimeout(clearToast, 4000)
      return () => clearTimeout(timer)
    }
  }, [toast, clearToast])

  if (!toast) return null

  const icons = {
    success: <CheckCircle className="h-5 w-5 text-emerald-500" />,
    error: <AlertCircle className="h-5 w-5 text-rose-500" />,
    info: <Info className="h-5 w-5 text-blue-500" />,
  }

  const borders = {
    success: 'border-emerald-200',
    error: 'border-rose-200',
    info: 'border-blue-200',
  }

  return (
    <div className="fixed bottom-6 right-6 z-50 animate-in slide-in-from-bottom-2">
      <div className={cn('flex items-center gap-3 rounded-lg border bg-white px-4 py-3 shadow-lg', borders[toast.type])}>
        {icons[toast.type]}
        <p className="text-sm font-medium text-medical-text">{toast.message}</p>
        <button onClick={clearToast} className="ml-2 rounded p-1 hover:bg-slate-100">
          <X className="h-4 w-4 text-medical-muted" />
        </button>
      </div>
    </div>
  )
}

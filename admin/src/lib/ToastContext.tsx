import React, { createContext, useState, useCallback } from 'react';
import { CheckCircle2, AlertCircle, Info, AlertTriangle, X } from 'lucide-react';

export type ToastType = 'success' | 'error' | 'warning' | 'info';

export interface Toast {
  id: string;
  type: ToastType;
  title?: string;
  message: string;
  duration?: number;
}

interface ToastContextType {
  showToast: (message: string, type?: ToastType, title?: string, duration?: number) => void;
  removeToast: (id: string) => void;
}

const ToastContext = createContext<ToastContextType | undefined>(undefined);

export function ToastProvider({ children }: { children: React.ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([]);

  const removeToast = useCallback((id: string) => {
    setToasts((prev) => prev.filter((t) => t.id !== id));
  }, []);

  const showToast = useCallback((
    message: string,
    type: ToastType = 'info',
    title?: string,
    duration = 3500
  ) => {
    const id = `toast_${Date.now()}_${Math.random().toString(36).substring(2, 7)}`;
    const newToast: Toast = { id, type, title, message, duration };
    setToasts((prev) => [...prev.slice(-4), newToast]);

    if (duration > 0) {
      setTimeout(() => {
        removeToast(id);
      }, duration);
    }
  }, [removeToast]);

  return (
    <ToastContext.Provider value={{ showToast, removeToast }}>
      {children}
      {/* Toast floating container */}
      <div 
        aria-live="polite"
        className="fixed bottom-5 right-5 z-50 flex flex-col gap-2 pointer-events-none max-w-sm w-full px-4"
      >
        {toasts.map((toast) => {
          const isSuccess = toast.type === 'success';
          const isError = toast.type === 'error';
          const isWarning = toast.type === 'warning';

          const borderClass = isSuccess 
            ? 'border-success/40 bg-surface/95 text-textMain'
            : isError
            ? 'border-error/40 bg-surface/95 text-textMain'
            : isWarning
            ? 'border-warning/40 bg-surface/95 text-textMain'
            : 'border-primary/40 bg-surface/95 text-textMain';

          const icon = isSuccess ? (
            <CheckCircle2 size={18} className="text-success flex-shrink-0" />
          ) : isError ? (
            <AlertCircle size={18} className="text-error flex-shrink-0" />
          ) : isWarning ? (
            <AlertTriangle size={18} className="text-warning flex-shrink-0" />
          ) : (
            <Info size={18} className="text-primary flex-shrink-0" />
          );

          return (
            <div
              key={toast.id}
              role="status"
              className={`pointer-events-auto flex items-start gap-3 p-3.5 rounded-xl border shadow-xl backdrop-blur-md transition-all duration-300 animate-in fade-in slide-in-from-bottom-2 ${borderClass}`}
            >
              {icon}
              <div className="flex-1 min-w-0">
                {toast.title && (
                  <p className="text-xs font-bold text-white mb-0.5">{toast.title}</p>
                )}
                <p className="text-xs text-textMain leading-relaxed">{toast.message}</p>
              </div>
              <button
                onClick={() => removeToast(toast.id)}
                className="text-textMuted hover:text-white p-0.5 rounded-md transition-colors cursor-pointer"
                aria-label="Dismiss notification"
              >
                <X size={14} />
              </button>
            </div>
          );
        })}
      </div>
    </ToastContext.Provider>
  );
}

export { ToastContext };
export { useToast } from './useToast';

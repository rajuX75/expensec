import React, { useState } from 'react';
import { useFirebaseData, putPath, deletePath } from '../lib/firebaseApi';
import { Bell, Send, Trash2, CheckCircle2 } from 'lucide-react';
import { cn } from '../lib/utils';

export default function Notifications() {
  const { data: notifications, refresh } = useFirebaseData('notifications');
  const [loading, setLoading] = useState(false);

  const notifList = notifications 
    ? Object.entries(notifications).sort((a: any, b: any) => (b[1].timestamp || 0) - (a[1].timestamp || 0))
    : [];

  const handleSend = async (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    setLoading(true);
    const fd = new FormData(e.currentTarget);
    const id = (fd.get('id') as string) || `notif_${Date.now()}`;
    
    const payload = {
      id,
      title: fd.get('title'),
      message: fd.get('message'),
      type: fd.get('type'),
      iconEmoji: fd.get('emoji') || '🔔',
      accentColorHex: fd.get('color'),
      timestamp: Date.now(),
      showPopup: fd.get('showPopup') === 'on',
      dismissible: fd.get('dismissible') === 'on',
      active: true,
      actionUrl: fd.get('actionUrl') || null,
      actionText: fd.get('actionText') || null,
    };

    try {
      await putPath(`notifications/${id}`, payload);
      refresh();
      (e.target as HTMLFormElement).reset();
    } catch (err: any) {
      alert(`Error sending notification: ${err.message}`);
    } finally {
      setLoading(false);
    }
  };

  const handleDelete = async (id: string) => {
    if (!confirm('Are you sure you want to delete this notification?')) return;
    try {
      await deletePath(`notifications/${id}`);
      refresh();
    } catch (err: any) {
      alert(`Error deleting: ${err.message}`);
    }
  };

  const handleToggleActive = async (id: string, currentState: boolean) => {
    try {
      await putPath(`notifications/${id}/active`, !currentState);
      refresh();
    } catch (err: any) {
      alert(`Error toggling: ${err.message}`);
    }
  };

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-2xl font-bold tracking-tight text-white flex items-center gap-2">
          <Bell className="text-primary" /> Notifications
        </h2>
        <p className="text-textMuted mt-1 text-sm">Send real-time alerts and popups to all Android users.</p>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <div className="panel">
          <div className="panel-header">Compose Notification</div>
          <div className="panel-body">
            <form onSubmit={handleSend} className="space-y-4">
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="text-xs font-bold text-textMuted uppercase mb-1 block">Notification ID</label>
                  <input name="id" placeholder="Auto-generated if empty" className="input-field" />
                </div>
                <div>
                  <label className="text-xs font-bold text-textMuted uppercase mb-1 block">Type</label>
                  <select name="type" className="input-field">
                    <option value="INFO">ℹ️ Info</option>
                    <option value="SUCCESS">✅ Success</option>
                    <option value="WARNING">⚠️ Warning</option>
                    <option value="PROMO">🎁 Promo</option>
                    <option value="UPDATE">🔄 Update</option>
                  </select>
                </div>
              </div>

              <div>
                <label className="text-xs font-bold text-textMuted uppercase mb-1 block">Title *</label>
                <input name="title" required placeholder="Short headline" className="input-field" />
              </div>

              <div>
                <label className="text-xs font-bold text-textMuted uppercase mb-1 block">Message</label>
                <textarea name="message" rows={3} placeholder="Detailed body text" className="input-field" />
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="text-xs font-bold text-textMuted uppercase mb-1 block">Icon Emoji</label>
                  <input name="emoji" placeholder="🎉" defaultValue="🎉" className="input-field" />
                </div>
                <div>
                  <label className="text-xs font-bold text-textMuted uppercase mb-1 block">Accent Color</label>
                  <input name="color" type="color" defaultValue="#6366f1" className="h-10 w-full rounded cursor-pointer bg-surface2 border border-border p-1" />
                </div>
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="text-xs font-bold text-textMuted uppercase mb-1 block">Action URL (Optional)</label>
                  <input name="actionUrl" placeholder="https://..." className="input-field" />
                </div>
                <div>
                  <label className="text-xs font-bold text-textMuted uppercase mb-1 block">Action Text</label>
                  <input name="actionText" placeholder="Open" className="input-field" />
                </div>
              </div>

              <div className="space-y-3 p-4 bg-surface2 rounded-xl border border-border">
                <label className="flex items-center gap-3 cursor-pointer">
                  <input type="checkbox" name="showPopup" defaultChecked className="w-4 h-4 accent-primary" />
                  <span className="text-sm font-medium">Show as Popup 🔔 (Pops up on app open)</span>
                </label>
                <label className="flex items-center gap-3 cursor-pointer">
                  <input type="checkbox" name="dismissible" defaultChecked className="w-4 h-4 accent-primary" />
                  <span className="text-sm font-medium">Dismissible (Allow user to close)</span>
                </label>
              </div>

              <button type="submit" disabled={loading} className="btn btn-primary w-full gap-2">
                <Send size={16} /> {loading ? 'Sending...' : 'Publish Notification'}
              </button>
            </form>
          </div>
        </div>

        <div className="panel flex flex-col">
          <div className="panel-header">Live Notifications</div>
          <div className="panel-body flex-1 overflow-auto space-y-3">
            {notifList.length === 0 && <p className="text-textMuted text-sm text-center py-10">No notifications active.</p>}
            
            {notifList.map(([key, n]: any) => (
              <div key={key} className={cn("bg-surface2 border border-border rounded-xl p-4 flex items-start gap-4 transition-all", n.active === false && "opacity-50 grayscale")}>
                <div className="text-2xl mt-1">{n.iconEmoji || '🔔'}</div>
                <div className="flex-1 min-w-0">
                  <div className="flex items-center justify-between gap-2">
                    <h3 className="font-semibold text-white truncate">{n.title}</h3>
                    <div className="flex items-center gap-1">
                      <button 
                        onClick={() => handleToggleActive(key, n.active !== false)}
                        className="p-1.5 text-textMuted hover:text-white bg-surface rounded hover:bg-surface border border-transparent hover:border-border transition-all"
                        title={n.active !== false ? "Deactivate" : "Activate"}
                      >
                        {n.active !== false ? <CheckCircle2 size={16} className="text-success" /> : <div className="w-4 h-4 rounded-full border-2 border-textMuted" />}
                      </button>
                      <button 
                        onClick={() => handleDelete(key)}
                        className="p-1.5 text-textMuted hover:text-error bg-surface rounded hover:bg-error/10 border border-transparent hover:border-error/20 transition-all"
                      >
                        <Trash2 size={16} />
                      </button>
                    </div>
                  </div>
                  <p className="text-sm text-textMuted mt-1 line-clamp-2">{n.message}</p>
                  <div className="flex flex-wrap gap-2 mt-3">
                    <span className="px-2 py-0.5 rounded-full text-[10px] font-bold bg-primary/20 text-primary">{n.type}</span>
                    {n.showPopup && <span className="px-2 py-0.5 rounded-full text-[10px] font-bold bg-warning/20 text-warning">POPUP</span>}
                    <span className="text-xs text-textMuted ml-auto">{key}</span>
                  </div>
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}

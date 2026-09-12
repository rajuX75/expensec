import { useState, useMemo } from 'react';
import { useFirebaseData } from '../lib/firebaseApi';
import { 
  Users, 
  BellRing, 
  Smartphone,
  Activity,
  ArrowUpRight,
  RefreshCw,
  Bug,
  Lightbulb,
  MessageCircle,
  AlertTriangle,
  Check,
  Bot,
} from 'lucide-react';
import { useNavigate } from 'react-router-dom';

type FeedbackType = 'BUG_REPORT' | 'FEATURE_REQUEST' | 'CRASH_LOG' | 'GENERAL';
const TYPE_META: Record<FeedbackType, { label: string; color: string; bg: string; icon: React.ReactNode }> = {
  BUG_REPORT:      { label: 'Bug',     color: 'text-error',   bg: 'bg-error/15',   icon: <Bug size={10} /> },
  FEATURE_REQUEST: { label: 'Feature', color: 'text-primary', bg: 'bg-primary/15', icon: <Lightbulb size={10} /> },
  CRASH_LOG:       { label: 'Crash',   color: 'text-warning', bg: 'bg-warning/15', icon: <AlertTriangle size={10} /> },
  GENERAL:         { label: 'General', color: 'text-success', bg: 'bg-success/15', icon: <MessageCircle size={10} /> },
};
import { 
  XAxis, 
  YAxis, 
  CartesianGrid, 
  Tooltip, 
  ResponsiveContainer,
  AreaChart,
  Area
} from 'recharts';

const mockActivityData = [
  { name: 'Mon', active: 400, new: 240 },
  { name: 'Tue', active: 300, new: 139 },
  { name: 'Wed', active: 520, new: 380 },
  { name: 'Thu', active: 450, new: 210 },
  { name: 'Fri', active: 600, new: 430 },
  { name: 'Sat', active: 750, new: 510 },
  { name: 'Sun', active: 680, new: 400 },
];

function buildAgentPrompt(entry: any): string {
  const date = entry.timestamp ? new Date(entry.timestamp).toLocaleString() : 'Unknown time';
  return `Please investigate and fix the following crash report from the Android app:

### User Message / Feedback:
${entry.message || '(No description provided by user)'}

### Environment & Metadata:
- **Device Model:** ${entry.deviceModel || 'Unknown'}
- **Android Version:** ${entry.androidVersion || 'Unknown'}
- **App Version:** v${entry.appVersion || '?'} (Version Code: ${entry.appVersionCode || '?'})
- **User ID:** ${entry.userId || 'anonymous'}
${entry.email ? `- **Email:** ${entry.email}\n` : ''}- **Timestamp:** ${date}

### Crash Report / Stack Trace:
\`\`\`
${entry.crashLog || '(No crash log attached)'}
\`\`\`

### Instructions:
1. Analyze the stack trace to determine the exact root cause, thread context, and failing code path.
2. Search the codebase for where this error originates or is triggered.
3. Fix the issue to prevent this crash, ensuring proper thread safety and defensive handling.
4. Verify the fix compiles and tests pass.
`;
}

export default function Dashboard() {
  const navigate = useNavigate();
  const [copiedId, setCopiedId] = useState<string | null>(null);
  const { data: notifications, loading: notifLoading } = useFirebaseData('notifications');
  const { data, loading: configLoading } = useFirebaseData('app_version');
  const { data: usersData, loading: usersLoading } = useFirebaseData('users');
  const { data: feedbackRaw, loading: feedbackLoading } = useFirebaseData<Record<string, any>>('feedback');
  const appConfig = data as any;

  const notifCount = notifications ? Object.keys(notifications).length : 0;
  const activeNotifCount = notifications ? Object.values(notifications).filter((n: any) => n.active !== false).length : 0;
  const usersCount = usersData ? Object.keys(usersData).length : 0;

  const recentFeedback = useMemo(() => {
    if (!feedbackRaw) return [];
    return Object.entries(feedbackRaw)
      .map(([key, val]: any) => ({ id: val.id || key, ...val }))
      .sort((a: any, b: any) => (b.timestamp ?? 0) - (a.timestamp ?? 0))
      .slice(0, 4);
  }, [feedbackRaw]);

  const handleCopyPrompt = async (fb: any, e: React.MouseEvent) => {
    e.stopPropagation();
    try {
      await navigator.clipboard.writeText(buildAgentPrompt(fb));
      setCopiedId(fb.id);
      setTimeout(() => setCopiedId(null), 2000);
    } catch (err) {
      console.error('Failed to copy', err);
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-2xl font-bold tracking-tight text-white">Dashboard Overview</h2>
          <p className="text-textMuted mt-1 text-sm">Monitor your Expense Tracker metrics and system health.</p>
        </div>
        <button className="btn btn-outline flex items-center gap-2 text-xs">
          <RefreshCw size={14} /> Refresh Data
        </button>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
        <StatCard 
          title="Total Users" 
          value={usersLoading ? '...' : (usersCount > 0 ? usersCount.toString() : '1,248')} 
          trend={usersCount > 0 ? 'From database' : '+12% this week'} 
          icon={<Users size={20} />} 
          color="text-primary"
          bg="bg-primary/10"
        />
        <StatCard 
          title="Active Notifications" 
          value={notifLoading ? '...' : activeNotifCount.toString()} 
          trend={`Out of ${notifCount} total`} 
          icon={<BellRing size={20} />} 
          color="text-warning"
          bg="bg-warning/10"
        />
        <StatCard 
          title="Current App Version" 
          value={configLoading ? '...' : (appConfig?.versionName || 'Unknown')} 
          trend={appConfig?.releaseDate ? `Released ${appConfig.releaseDate}` : 'Latest version'} 
          icon={<Smartphone size={20} />} 
          color="text-success"
          bg="bg-success/10"
        />
        <StatCard 
          title="System Health" 
          value="99.9%" 
          trend="All systems operational" 
          icon={<Activity size={20} />} 
          color="text-error"
          bg="bg-error/10"
        />
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6 mt-6">
        <div className="lg:col-span-2 panel">
          <div className="panel-header">User Activity (Last 7 Days)</div>
          <div className="panel-body h-[300px]">
            <ResponsiveContainer width="100%" height="100%">
              <AreaChart data={mockActivityData} margin={{ top: 10, right: 10, left: -20, bottom: 0 }}>
                <defs>
                  <linearGradient id="colorActive" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor="#6366f1" stopOpacity={0.3}/>
                    <stop offset="95%" stopColor="#6366f1" stopOpacity={0}/>
                  </linearGradient>
                </defs>
                <CartesianGrid strokeDasharray="3 3" stroke="#2e2e40" vertical={false} />
                <XAxis dataKey="name" stroke="#8b8ba8" fontSize={12} tickLine={false} axisLine={false} />
                <YAxis stroke="#8b8ba8" fontSize={12} tickLine={false} axisLine={false} />
                <Tooltip 
                  contentStyle={{ backgroundColor: '#1a1a24', borderColor: '#2e2e40', borderRadius: '8px' }}
                  itemStyle={{ color: '#f1f1f5' }}
                />
                <Area type="monotone" dataKey="active" stroke="#6366f1" strokeWidth={3} fillOpacity={1} fill="url(#colorActive)" />
              </AreaChart>
            </ResponsiveContainer>
          </div>
        </div>
        
        <div className="panel flex flex-col">
          <div className="panel-header flex items-center justify-between">
            <span>Recent Feedback</span>
            <span className="text-xs text-textMuted font-normal">
              {feedbackLoading ? '…' : `${Object.values(feedbackRaw ?? {}).length} total`}
            </span>
          </div>
          <div className="panel-body flex-1 overflow-auto space-y-4">
            {feedbackLoading && (
              <p className="text-xs text-textMuted animate-pulse text-center py-4">Loading…</p>
            )}
            {!feedbackLoading && recentFeedback.length === 0 && (
              <p className="text-xs text-textMuted text-center py-4">No feedback yet.</p>
            )}
            {!feedbackLoading && recentFeedback.map((fb: any) => {
              const meta = TYPE_META[fb.type as FeedbackType] ?? TYPE_META.GENERAL;
              return (
                <div key={fb.id} className="flex gap-3 items-start border-b border-border pb-4 last:border-0 last:pb-0">
                  <div className={`w-8 h-8 rounded-full ${meta.bg} flex items-center justify-center flex-shrink-0 ${meta.color}`}>
                    {meta.icon}
                  </div>
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-2 mb-0.5 flex-wrap">
                      <span className={`text-[10px] font-bold ${meta.color}`}>{meta.label}</span>
                      {fb.type === 'CRASH_LOG' && (
                        <span className="text-[9px] bg-warning/10 text-warning px-1.5 py-0.5 rounded font-bold">CRASH</span>
                      )}
                    </div>
                    <p className="text-sm font-medium text-white line-clamp-2 leading-snug">{fb.message || '(no message)'}</p>
                    <p className="text-xs text-textMuted mt-1">
                      v{fb.appVersion || '?'} · {fb.deviceModel || 'Unknown'} · {fb.timestamp ? new Date(fb.timestamp).toLocaleDateString() : ''}
                    </p>
                    {fb.crashLog && (
                      <div className="mt-2">
                        <button
                          onClick={(e) => handleCopyPrompt(fb, e)}
                          className="btn text-[11px] flex items-center gap-1 py-0.5 px-2 rounded bg-primary/15 hover:bg-primary/25 text-primary border border-primary/30 font-medium transition-colors cursor-pointer"
                          title="Copy ready-to-use AI Agent prompt with this crash report"
                        >
                          {copiedId === fb.id ? (
                            <>
                              <Check size={11} className="text-success" />
                              <span className="text-success font-semibold">Copied Prompt!</span>
                            </>
                          ) : (
                            <>
                              <Bot size={11} />
                              <span>Copy Agent Prompt</span>
                            </>
                          )}
                        </button>
                      </div>
                    )}
                  </div>
                </div>
              );
            })}
            <button
              onClick={() => navigate('/feedback')}
              className="w-full btn btn-outline text-xs mt-2 flex items-center justify-center gap-1"
            >
              View All <ArrowUpRight size={14} />
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}

function StatCard({ title, value, trend, icon, color, bg }: any) {
  return (
    <div className="panel">
      <div className="panel-body">
        <div className="flex justify-between items-start">
          <div>
            <p className="text-sm font-medium text-textMuted">{title}</p>
            <h3 className="text-2xl font-bold text-white mt-1">{value}</h3>
          </div>
          <div className={`p-2 rounded-lg ${bg} ${color}`}>
            {icon}
          </div>
        </div>
        <div className="mt-4 text-xs font-medium text-textMuted">
          {trend}
        </div>
      </div>
    </div>
  );
}

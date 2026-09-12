import { useState, useMemo } from 'react';
import { useFirebaseData } from '../lib/firebaseApi';
import {
  Bug,
  Lightbulb,
  MessageCircle,
  AlertTriangle,
  ChevronDown,
  ChevronUp,
  RefreshCw,
  Search,
  Filter,
  Smartphone,
  Clock,
  User,
  Mail,
  Copy,
  Check,
  Bot,
  Terminal,
} from 'lucide-react';

type FeedbackType = 'BUG_REPORT' | 'FEATURE_REQUEST' | 'CRASH_LOG' | 'GENERAL';

interface FeedbackEntry {
  id: string;
  type: FeedbackType;
  message: string;
  appVersion: string;
  appVersionCode: number;
  deviceModel: string;
  androidVersion: string;
  timestamp: number;
  userId: string;
  email?: string;
  crashLog?: string;
}

const TYPE_META: Record<FeedbackType, { label: string; color: string; bg: string; icon: React.ReactNode }> = {
  BUG_REPORT:      { label: 'Bug Report',       color: 'text-error',   bg: 'bg-error/15',   icon: <Bug size={12} /> },
  FEATURE_REQUEST: { label: 'Feature Request',  color: 'text-primary', bg: 'bg-primary/15', icon: <Lightbulb size={12} /> },
  CRASH_LOG:       { label: 'Crash Log',        color: 'text-warning', bg: 'bg-warning/15', icon: <AlertTriangle size={12} /> },
  GENERAL:         { label: 'General',          color: 'text-success', bg: 'bg-success/15', icon: <MessageCircle size={12} /> },
};

function TypeBadge({ type }: { type: FeedbackType }) {
  const meta = TYPE_META[type] ?? TYPE_META.GENERAL;
  return (
    <span className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-bold ${meta.bg} ${meta.color}`}>
      {meta.icon} {meta.label}
    </span>
  );
}

function buildCrashReportText(entry: FeedbackEntry): string {
  const date = entry.timestamp ? new Date(entry.timestamp).toLocaleString() : 'Unknown time';
  return `=== Crash Report ===
Time     : ${date}
Device   : ${entry.deviceModel || 'Unknown'}
Android  : ${entry.androidVersion || 'Unknown'}
App Ver  : ${entry.appVersion || '?'} (${entry.appVersionCode || '?'})
User ID  : ${entry.userId || 'anonymous'}${entry.email ? `\nEmail    : ${entry.email}` : ''}

--- User Message ---
${entry.message || '(No description provided)'}

--- Stack Trace ---
${entry.crashLog || '(No crash log attached)'}
`;
}

function buildAgentPrompt(entry: FeedbackEntry): string {
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

function FeedbackCard({ entry }: { entry: FeedbackEntry }) {
  const [expanded, setExpanded] = useState(false);
  const [copiedKind, setCopiedKind] = useState<'report' | 'prompt' | null>(null);

  const date = entry.timestamp
    ? new Date(entry.timestamp).toLocaleString()
    : 'Unknown time';

  const copyToClipboard = async (text: string, kind: 'report' | 'prompt') => {
    try {
      await navigator.clipboard.writeText(text);
      setCopiedKind(kind);
      setTimeout(() => setCopiedKind(null), 2200);
    } catch (err) {
      console.error('Failed to copy', err);
    }
  };

  const hasCrash = Boolean(entry.crashLog || entry.type === 'CRASH_LOG');

  return (
    <div className={`panel mb-3 transition-all ${hasCrash ? 'border-warning/30 hover:border-warning/50' : ''}`}>
      <div className="panel-body">
        {/* Header row */}
        <div className="flex items-start justify-between gap-3">
          <div className="flex-1 min-w-0">
            <div className="flex flex-wrap items-center gap-2 mb-2">
              <TypeBadge type={entry.type} />
              <span className="text-[10px] text-textMuted flex items-center gap-1">
                <Clock size={10} /> {date}
              </span>
            </div>
            <p className="text-sm text-white leading-relaxed font-medium">
              {entry.message || '(no message)'}
            </p>
          </div>
        </div>

        {/* Device & app info */}
        <div className="mt-3 flex flex-wrap gap-x-4 gap-y-1 text-xs text-textMuted">
          <span className="flex items-center gap-1">
            <Smartphone size={11} /> {entry.deviceModel || 'Unknown device'} · Android {entry.androidVersion || '?'}
          </span>
          <span className="flex items-center gap-1">
            App v{entry.appVersion || '?'} ({entry.appVersionCode || '?'})
          </span>
          {entry.email && (
            <span className="flex items-center gap-1">
              <Mail size={11} /> {entry.email}
            </span>
          )}
          <span className="flex items-center gap-1">
            <User size={11} /> {entry.userId || 'anonymous'}
          </span>
        </div>

        {/* Crash log accordion */}
        {entry.crashLog && (
          <div className="mt-3">
            <button
              onClick={() => setExpanded(!expanded)}
              className="flex items-center gap-1 text-xs text-warning hover:text-warning/80 font-medium transition-colors"
            >
              {expanded ? <ChevronUp size={12} /> : <ChevronDown size={12} />}
              {expanded ? 'Hide' : 'Show'} crash stack trace
            </button>
            {expanded && (
              <pre className="mt-2 p-3 bg-surface2 border border-border rounded-lg text-[10px] text-textMuted overflow-x-auto whitespace-pre-wrap max-h-56 overflow-y-auto leading-relaxed font-mono">
                {entry.crashLog}
              </pre>
            )}
          </div>
        )}

        {/* Action bar for Copy Crash Report & Agent Prompt */}
        <div className="mt-3 pt-3 border-t border-border/80 flex flex-wrap items-center justify-between gap-2">
          {hasCrash ? (
            <div className="flex flex-wrap items-center gap-2">
              {/* Copy Crash Report button */}
              <button
                onClick={() => copyToClipboard(buildCrashReportText(entry), 'report')}
                className="btn btn-outline text-xs flex items-center gap-1.5 py-1 px-2.5 rounded-lg border-border hover:bg-surface2 transition-colors cursor-pointer"
                title="Copy crash report with user message and device info"
              >
                {copiedKind === 'report' ? (
                  <>
                    <Check size={12} className="text-success" />
                    <span className="text-success font-semibold">Copied Report!</span>
                  </>
                ) : (
                  <>
                    <Copy size={12} />
                    <span>Copy Crash Report</span>
                  </>
                )}
              </button>

              {/* Copy Agent Prompt button */}
              <button
                onClick={() => copyToClipboard(buildAgentPrompt(entry), 'prompt')}
                className="btn text-xs flex items-center gap-1.5 py-1 px-2.5 rounded-lg bg-primary/20 hover:bg-primary/30 text-primary border border-primary/40 font-semibold transition-colors cursor-pointer"
                title="Copy ready-to-use AI Agent prompt with crash report and user message"
              >
                {copiedKind === 'prompt' ? (
                  <>
                    <Check size={12} className="text-success" />
                    <span className="text-success font-semibold">Copied Agent Prompt!</span>
                  </>
                ) : (
                  <>
                    <Bot size={13} />
                    <span>Copy Agent Prompt</span>
                  </>
                )}
              </button>
            </div>
          ) : (
            <div className="flex flex-wrap items-center gap-2">
              {/* Fallback copy button for regular bug report or feedback */}
              <button
                onClick={() => copyToClipboard(buildAgentPrompt(entry), 'prompt')}
                className="btn btn-outline text-xs flex items-center gap-1.5 py-1 px-2.5 rounded-lg text-textMuted hover:text-white cursor-pointer"
                title="Copy this report formatted as an AI Agent prompt"
              >
                {copiedKind === 'prompt' ? (
                  <>
                    <Check size={12} className="text-success" />
                    <span className="text-success">Copied Prompt!</span>
                  </>
                ) : (
                  <>
                    <Terminal size={12} />
                    <span>Copy Agent Prompt</span>
                  </>
                )}
              </button>
            </div>
          )}

          <span className="text-[10px] text-textMuted font-mono">
            ID: {entry.id?.slice(0, 8)}...
          </span>
        </div>
      </div>
    </div>
  );
}

const ALL_TYPES: FeedbackType[] = ['BUG_REPORT', 'CRASH_LOG', 'FEATURE_REQUEST', 'GENERAL'];

export default function Feedback() {
  const { data: rawData, loading, error, refresh } = useFirebaseData<Record<string, FeedbackEntry>>('feedback');
  const [search, setSearch] = useState('');
  const [typeFilter, setTypeFilter] = useState<FeedbackType | 'ALL'>('ALL');

  const entries: FeedbackEntry[] = useMemo(() => {
    if (!rawData) return [];
    return Object.entries(rawData)
      .map(([key, val]) => ({ ...val, id: val.id || key }))
      .sort((a, b) => (b.timestamp ?? 0) - (a.timestamp ?? 0));
  }, [rawData]);

  const filtered = useMemo(() => {
    return entries.filter((e) => {
      const matchType = typeFilter === 'ALL' || e.type === typeFilter;
      const q = search.toLowerCase();
      const matchSearch =
        !q ||
        e.message?.toLowerCase().includes(q) ||
        e.email?.toLowerCase().includes(q) ||
        e.userId?.toLowerCase().includes(q) ||
        e.deviceModel?.toLowerCase().includes(q);
      return matchType && matchSearch;
    });
  }, [entries, typeFilter, search]);

  // Counts per type
  const counts = useMemo(() => {
    const c: Record<string, number> = { ALL: entries.length };
    for (const t of ALL_TYPES) c[t] = entries.filter((e) => e.type === t).length;
    return c;
  }, [entries]);

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between flex-wrap gap-3">
        <div>
          <h2 className="text-2xl font-bold tracking-tight text-white flex items-center gap-2">
            <Bug className="text-error" /> Feedback & Bug Reports
          </h2>
          <p className="text-textMuted mt-1 text-sm">
            Live user feedback and crash reports from Firebase — {entries.length} total entries.
          </p>
        </div>
        <button onClick={refresh} className="btn btn-outline flex items-center gap-2 text-xs cursor-pointer">
          <RefreshCw size={14} /> Refresh
        </button>
      </div>

      {/* Type filter pills */}
      <div className="flex flex-wrap gap-2">
        {(['ALL', ...ALL_TYPES] as const).map((t) => {
          const meta = t === 'ALL' ? null : TYPE_META[t];
          const active = typeFilter === t;
          return (
            <button
              key={t}
              onClick={() => setTypeFilter(t)}
              className={`px-3 py-1.5 rounded-full text-xs font-semibold transition-all border cursor-pointer ${
                active
                  ? (meta ? `${meta.bg} ${meta.color} border-transparent` : 'bg-surface2 text-white border-border')
                  : 'text-textMuted border-border hover:border-surface2 hover:text-textMain'
              }`}
            >
              {t === 'ALL' ? 'All' : TYPE_META[t].label}
              <span className="ml-1.5 opacity-70">{counts[t] ?? 0}</span>
            </button>
          );
        })}
      </div>

      {/* Search bar */}
      <div className="flex items-center bg-surface border border-border px-3 py-2 rounded-xl gap-2">
        <Search size={14} className="text-textMuted flex-shrink-0" />
        <input
          type="text"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder="Search by message, email, user ID or device…"
          className="bg-transparent border-none text-sm text-white outline-none flex-1 placeholder:text-textMuted"
        />
        {search && (
          <button onClick={() => setSearch('')} className="text-textMuted hover:text-white text-xs cursor-pointer">✕</button>
        )}
        <Filter size={13} className="text-textMuted flex-shrink-0" />
        <span className="text-xs text-textMuted flex-shrink-0">{filtered.length} results</span>
      </div>

      {/* States */}
      {loading && (
        <div className="panel">
          <div className="panel-body text-center py-12 text-textMuted text-sm animate-pulse">
            Loading feedback from Firebase…
          </div>
        </div>
      )}

      {error && (
        <div className="panel border border-error/30">
          <div className="panel-body text-center py-8 text-error text-sm">
            ⚠ Failed to load feedback: {error}
          </div>
        </div>
      )}

      {!loading && !error && filtered.length === 0 && (
        <div className="panel">
          <div className="panel-body text-center py-12 text-textMuted text-sm">
            {entries.length === 0 ? 'No feedback submitted yet.' : 'No entries match your filters.'}
          </div>
        </div>
      )}

      {/* Feed */}
      {!loading && filtered.map((entry) => (
        <FeedbackCard key={entry.id} entry={entry} />
      ))}
    </div>
  );
}

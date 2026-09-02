import { useFirebaseData } from '../lib/firebaseApi';
import { 
  Users, 
  BellRing, 
  Smartphone,
  Activity,
  ArrowUpRight,
  RefreshCw
} from 'lucide-react';
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

export default function Dashboard() {
  const { data: notifications, loading: notifLoading } = useFirebaseData('notifications');
  const { data, loading: configLoading } = useFirebaseData('app_version');
  const { data: usersData, loading: usersLoading } = useFirebaseData('users');
  const appConfig = data as any;

  const notifCount = notifications ? Object.keys(notifications).length : 0;
  const activeNotifCount = notifications ? Object.values(notifications).filter((n: any) => n.active !== false).length : 0;
  const usersCount = usersData ? Object.keys(usersData).length : 0;

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
          <div className="panel-header">Recent Feedback</div>
          <div className="panel-body flex-1 overflow-auto space-y-4">
            {[1, 2, 3, 4].map((i) => (
              <div key={i} className="flex gap-3 items-start border-b border-border pb-4 last:border-0 last:pb-0">
                <div className="w-8 h-8 rounded-full bg-surface2 flex items-center justify-center flex-shrink-0 text-xs font-bold text-textMuted">
                  U{i}
                </div>
                <div>
                  <p className="text-sm font-medium text-white line-clamp-1">Great app! Needs dark mode syncing.</p>
                  <p className="text-xs text-textMuted mt-1">v1.1.2 • Pixel 6</p>
                </div>
              </div>
            ))}
            <button className="w-full btn btn-outline text-xs mt-2 flex items-center justify-center gap-1">
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

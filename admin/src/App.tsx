
import { BrowserRouter, Routes, Route, NavLink, Navigate } from 'react-router-dom';
import { 
  LayoutDashboard, 
  Bell, 
  Settings, 
  Users, 
  Activity,
  Wallet
} from 'lucide-react';
import Dashboard from './pages/Dashboard';
import Notifications from './pages/Notifications';
import AppConfig from './pages/AppConfig';
import UserLogs from './pages/UserLogs';

function Sidebar() {
  const navItems = [
    { name: 'Dashboard', path: '/', icon: LayoutDashboard },
    { name: 'Users & Logs', path: '/users', icon: Users },
    { name: 'Notifications', path: '/notifications', icon: Bell },
    { name: 'App Config', path: '/config', icon: Settings },
  ];

  return (
    <aside className="w-64 bg-surface border-r border-border h-screen sticky top-0 flex flex-col hidden md:flex">
      <div className="p-6 flex items-center gap-3 border-b border-border">
        <div className="bg-primary/20 text-primary p-2 rounded-lg">
          <Wallet size={24} />
        </div>
        <div>
          <h1 className="font-bold text-lg leading-tight tracking-tight text-white">ExpenseX</h1>
          <span className="text-[10px] uppercase font-bold tracking-widest text-textMuted">Admin Panel</span>
        </div>
      </div>
      
      <nav className="flex-1 p-4 space-y-1">
        {navItems.map((item) => (
          <NavLink
            key={item.path}
            to={item.path}
            className={({ isActive }) =>
              `flex items-center gap-3 px-4 py-3 rounded-xl transition-all font-medium text-sm ${
                isActive 
                  ? 'bg-primary/10 text-primary hover:bg-primary/15' 
                  : 'text-textMuted hover:bg-surface2 hover:text-textMain'
              }`
            }
          >
            <item.icon size={18} />
            {item.name}
          </NavLink>
        ))}
      </nav>

      <div className="p-6 border-t border-border">
        <div className="flex items-center gap-3 bg-surface2 p-3 rounded-xl border border-border">
          <div className="w-8 h-8 rounded-full bg-primary/20 flex items-center justify-center text-primary font-bold">
            A
          </div>
          <div>
            <p className="text-sm font-semibold text-white leading-tight">Admin User</p>
            <p className="text-xs text-textMuted">Superadmin</p>
          </div>
        </div>
      </div>
    </aside>
  );
}

function Topbar() {
  return (
    <header className="h-16 border-b border-border bg-surface/50 backdrop-blur flex items-center justify-between px-6 sticky top-0 z-10">
      <div className="flex items-center gap-2 text-sm text-textMuted font-medium">
        <Activity size={16} className="text-success animate-pulse" />
        System Operational
      </div>
      <div className="flex items-center gap-4">
        <button className="relative text-textMuted hover:text-white transition-colors">
          <Bell size={20} />
          <span className="absolute -top-1 -right-1 w-2 h-2 bg-primary rounded-full ring-2 ring-background"></span>
        </button>
      </div>
    </header>
  );
}

function App() {
  return (
    <BrowserRouter>
      <div className="flex min-h-screen bg-background">
        <Sidebar />
        <main className="flex-1 flex flex-col min-w-0">
          <Topbar />
          <div className="flex-1 overflow-auto p-6 lg:p-8">
            <div className="max-w-6xl mx-auto">
              <Routes>
                <Route path="/" element={<Dashboard />} />
                <Route path="/notifications" element={<Notifications />} />
                <Route path="/config" element={<AppConfig />} />
                <Route path="/users" element={<UserLogs />} />
                <Route path="*" element={<Navigate to="/" replace />} />
              </Routes>
            </div>
          </div>
        </main>
      </div>
    </BrowserRouter>
  );
}

export default App;

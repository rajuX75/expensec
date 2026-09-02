import { useFirebaseData } from '../lib/firebaseApi';
import { Users, Download, Search } from 'lucide-react';

export default function UserLogs() {
  const { data: usersData, loading } = useFirebaseData('users');

  const usersList = usersData 
    ? Object.entries(usersData).map(([id, val]: any) => ({ id, ...val }))
    : [];

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-2xl font-bold tracking-tight text-white flex items-center gap-2">
            <Users className="text-primary" /> Users & Activity
          </h2>
          <p className="text-textMuted mt-1 text-sm">Monitor registered users and their device telemetry.</p>
        </div>
        <button className="btn btn-outline flex items-center gap-2 text-xs">
          <Download size={14} /> Export CSV
        </button>
      </div>

      <div className="panel">
        <div className="panel-header flex justify-between items-center">
          <span>Registered Users</span>
          <div className="flex items-center bg-surface2 px-3 py-1.5 rounded-lg border border-border">
            <Search size={14} className="text-textMuted mr-2" />
            <input 
              type="text" 
              placeholder="Search feedback..." 
              className="bg-transparent border-none text-xs text-white outline-none w-48"
            />
          </div>
        </div>
        <div className="panel-body p-0">
          <div className="overflow-x-auto">
            <table className="w-full text-left border-collapse">
              <thead>
                <tr className="bg-surface2/50 border-b border-border text-xs text-textMuted uppercase tracking-wider">
                  <th className="px-6 py-4 font-semibold">User</th>
                  <th className="px-6 py-4 font-semibold">Status</th>
                  <th className="px-6 py-4 font-semibold">Device</th>
                  <th className="px-6 py-4 font-semibold">Last Active</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {loading && (
                  <tr>
                    <td colSpan={4} className="px-6 py-8 text-center text-textMuted text-sm">
                      Loading users from database...
                    </td>
                  </tr>
                )}
                {!loading && usersList.length === 0 && (
                  <tr>
                    <td colSpan={4} className="px-6 py-8 text-center text-textMuted text-sm">
                      No users found in database.
                    </td>
                  </tr>
                )}
                {!loading && usersList.map((user: any) => (
                  <tr key={user.id} className="hover:bg-surface2/50 transition-colors">
                    <td className="px-6 py-4">
                      <div className="flex items-center gap-3">
                        <div className="w-8 h-8 rounded-full bg-primary/20 flex items-center justify-center text-primary font-bold text-xs flex-shrink-0">
                          {user.name ? user.name.charAt(0).toUpperCase() : (user.email ? user.email.charAt(0).toUpperCase() : 'U')}
                        </div>
                        <div>
                          <p className="text-sm font-medium text-white">{user.name || user.displayName || 'Unknown User'}</p>
                          <p className="text-xs text-textMuted mt-0.5">{user.email || user.id}</p>
                        </div>
                      </div>
                    </td>
                    <td className="px-6 py-4">
                      <span className={`px-2.5 py-1 rounded-full text-[10px] font-bold ${
                        user.status === 'Active' || !user.status 
                          ? 'bg-success/20 text-success' 
                          : 'bg-surface2 border border-border text-textMuted'
                      }`}>
                        {user.status || 'Active'}
                      </span>
                    </td>
                    <td className="px-6 py-4">
                      <p className="text-sm text-white">{user.device || user.deviceModel || 'Unknown Device'}</p>
                      <p className="text-xs text-textMuted mt-0.5">Android {user.android || user.androidVersion || '?'} • v{user.appVersion || '?'}</p>
                    </td>
                    <td className="px-6 py-4">
                      <p className="text-sm text-textMain">
                        {user.lastActive || user.createdAt ? new Date(user.lastActive || user.createdAt || Date.now()).toLocaleString() : 'N/A'}
                      </p>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </div>
  );
}

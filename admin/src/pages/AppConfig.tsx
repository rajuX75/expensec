import React, { useState, useEffect } from 'react';
import { useFirebaseData, putPath } from '../lib/firebaseApi';
import { Settings, Save, Smartphone } from 'lucide-react';

export default function AppConfig() {
  const { data: config, refresh } = useFirebaseData('app_version');
  const [loading, setLoading] = useState(false);
  const [formData, setFormData] = useState<any>({});
  
  // Database connection state
  const [dbUrl, setDbUrl] = useState(localStorage.getItem('fb_db_url') || 'https://expenstracke-default-rtdb.firebaseio.com');
  const [dbToken, setDbToken] = useState(localStorage.getItem('fb_db_token') || '');

  useEffect(() => {
    if (config) setFormData(config);
  }, [config]);

  const handleSaveConnection = (e: React.FormEvent) => {
    e.preventDefault();
    localStorage.setItem('fb_db_url', dbUrl);
    localStorage.setItem('fb_db_token', dbToken);
    alert('Database credentials saved! Refreshing page...');
    window.location.reload();
  };

  const handleChange = (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => {
    const { name, value, type } = e.target;
    let val: any = value;
    if (type === 'number') val = Number(value);
    if (type === 'checkbox') val = (e.target as HTMLInputElement).checked;
    
    setFormData((prev: any) => ({ ...prev, [name]: val }));
  };

  const handleSave = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    try {
      await putPath('app_version', formData);
      alert('App version updated successfully!');
      refresh();
    } catch (err: any) {
      alert(`Error saving: ${err.message}`);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-2xl font-bold tracking-tight text-white flex items-center gap-2">
          <Settings className="text-primary" /> Configuration
        </h2>
        <p className="text-textMuted mt-1 text-sm">Manage database credentials and OTA app updates.</p>
      </div>

      <div className="panel">
        <div className="panel-header bg-warning/10 text-warning border-b-warning/20">🔑 Firebase Connection Security</div>
        <div className="panel-body">
          <form onSubmit={handleSaveConnection} className="flex gap-4 items-end">
            <div className="flex-1">
              <label className="text-xs font-bold text-textMuted uppercase mb-1 block">Database URL</label>
              <input value={dbUrl} onChange={e => setDbUrl(e.target.value)} className="input-field" />
            </div>
            <div className="flex-1">
              <label className="text-xs font-bold text-textMuted uppercase mb-1 block">Database Secret / Auth Token</label>
              <input type="password" value={dbToken} onChange={e => setDbToken(e.target.value)} placeholder="Required for 401 Unauthorized errors" className="input-field" />
            </div>
            <button type="submit" className="btn bg-warning text-warning-900 hover:bg-warning/90 text-black font-bold h-10">
              Save & Connect
            </button>
          </form>
          <p className="text-[11px] text-textMuted mt-3">
            If you are getting a <strong>401 (Unauthorized)</strong> error, you need to provide your Firebase Database Secret here (found in Firebase Console → Project Settings → Service Accounts → Database Secrets).
          </p>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <div className="panel">
          <div className="panel-header">Remote Version Control</div>
          <div className="panel-body">
            <form onSubmit={handleSave} className="space-y-4">
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="text-xs font-bold text-textMuted uppercase mb-1 block">Version Name</label>
                  <input name="versionName" value={formData.versionName || ''} onChange={handleChange} placeholder="e.g. 1.2.0" className="input-field" />
                </div>
                <div>
                  <label className="text-xs font-bold text-textMuted uppercase mb-1 block">Version Code</label>
                  <input name="versionCode" type="number" value={formData.versionCode || ''} onChange={handleChange} className="input-field" />
                </div>
              </div>

              <div>
                <label className="text-xs font-bold text-textMuted uppercase mb-1 block">Minimum Supported Version Code</label>
                <input name="minSupportedVersionCode" type="number" value={formData.minSupportedVersionCode || ''} onChange={handleChange} className="input-field" />
                <p className="text-[10px] text-textMuted mt-1">Users with a version code below this will be forced to update.</p>
              </div>

              <div>
                <label className="text-xs font-bold text-textMuted uppercase mb-1 block">Release Title</label>
                <input name="releaseTitle" value={formData.releaseTitle || ''} onChange={handleChange} className="input-field" />
              </div>

              <div>
                <label className="text-xs font-bold text-textMuted uppercase mb-1 block">Release Notes</label>
                <textarea name="releaseNotes" rows={4} value={formData.releaseNotes || ''} onChange={handleChange} className="input-field" />
              </div>

              <div>
                <label className="text-xs font-bold text-textMuted uppercase mb-1 block">APK Download URL</label>
                <input name="downloadUrl" value={formData.downloadUrl || ''} onChange={handleChange} className="input-field" />
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="text-xs font-bold text-textMuted uppercase mb-1 block">APK Size (MB)</label>
                  <input name="apkSizeMb" type="number" step="0.1" value={formData.apkSizeMb || ''} onChange={handleChange} className="input-field" />
                </div>
                <div>
                  <label className="text-xs font-bold text-textMuted uppercase mb-1 block">Release Date</label>
                  <input name="releaseDate" value={formData.releaseDate || ''} onChange={handleChange} placeholder="YYYY-MM-DD" className="input-field" />
                </div>
              </div>

              <div className="p-4 bg-surface2 rounded-xl border border-border">
                <label className="flex items-center gap-3 cursor-pointer">
                  <input type="checkbox" name="isMandatory" checked={formData.isMandatory || false} onChange={handleChange} className="w-4 h-4 accent-primary" />
                  <div>
                    <span className="text-sm font-medium text-white block">Mandatory Update</span>
                    <span className="text-xs text-textMuted">Prevent users from skipping this update</span>
                  </div>
                </label>
              </div>

              <button type="submit" disabled={loading} className="btn btn-primary w-full gap-2 mt-4">
                <Save size={16} /> {loading ? 'Saving...' : 'Save App Version'}
              </button>
            </form>
          </div>
        </div>

        <div className="panel h-fit">
          <div className="panel-header">Device Preview</div>
          <div className="panel-body flex justify-center bg-surface2 py-10">
            {/* Mock Android Update Dialog */}
            <div className="w-[320px] bg-surface rounded-2xl shadow-xl border border-border overflow-hidden">
              <div className="p-6 text-center space-y-3">
                <div className="w-16 h-16 mx-auto bg-primary/20 rounded-full flex items-center justify-center text-primary">
                  <Smartphone size={32} />
                </div>
                <h3 className="text-lg font-bold text-white">Update Available</h3>
                <p className="text-sm text-textMuted">Version {formData.versionName || '1.0.0'} is available.</p>
                
                <div className="text-left bg-surface2 p-3 rounded-xl border border-border mt-4">
                  <p className="text-xs font-bold text-primary mb-1">What's New</p>
                  <p className="text-xs text-white whitespace-pre-wrap">{formData.releaseNotes || 'Bug fixes and performance improvements.'}</p>
                </div>
                
                <p className="text-[10px] text-textMuted pt-2">Size: {formData.apkSizeMb || 0} MB</p>

                <div className="flex flex-col gap-2 pt-2">
                  <button className="btn btn-primary w-full py-3 rounded-xl">Update Now</button>
                  {!formData.isMandatory && (
                    <button className="btn text-textMuted hover:text-white py-2">Later</button>
                  )}
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

import { useState, useEffect } from 'react';

// Store config in localStorage
export const getDbConfig = () => {
  return {
    url: localStorage.getItem('fb_db_url') || 'https://expenstracke-default-rtdb.firebaseio.com',
    token: localStorage.getItem('fb_db_token') || ''
  };
};

export const setDbConfig = (url: string, token: string) => {
  localStorage.setItem('fb_db_url', url);
  localStorage.setItem('fb_db_token', token);
};

const getBaseUrl = () => {
  const { url, token } = getDbConfig();
  const cleanUrl = url.replace(/\/$/, '');
  const authQuery = token ? `?auth=${encodeURIComponent(token)}` : '';
  return { cleanUrl, authQuery };
};

export async function testConnection() {
  const { cleanUrl, authQuery } = getBaseUrl();
  const res = await fetch(`${cleanUrl}/.info/connected.json${authQuery}`);
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  return true;
}

export async function fetchPath(path: string) {
  const { cleanUrl, authQuery } = getBaseUrl();
  const res = await fetch(`${cleanUrl}/${path}.json${authQuery}`);
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  return await res.json();
}

export async function putPath(path: string, data: any) {
  const { cleanUrl, authQuery } = getBaseUrl();
  const res = await fetch(`${cleanUrl}/${path}.json${authQuery}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data)
  });
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  return await res.json();
}

export async function deletePath(path: string) {
  const { cleanUrl, authQuery } = getBaseUrl();
  const res = await fetch(`${cleanUrl}/${path}.json${authQuery}`, {
    method: 'DELETE'
  });
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  return true;
}

// Hook to auto-fetch data
export function useFirebaseData<T>(path: string) {
  const [data, setData] = useState<T | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const refresh = async () => {
    setLoading(true);
    try {
      const d = await fetchPath(path);
      setData(d);
      setError(null);
    } catch (err: any) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    refresh();
  }, [path]);

  return { data, loading, error, refresh };
}

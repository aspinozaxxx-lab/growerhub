import { useSyncExternalStore } from 'react';

const QUERY = '(max-width: 680px)';
const mediaQuery = () => (typeof window !== 'undefined' ? window.matchMedia?.(QUERY) : null);
const snapshot = () => mediaQuery()?.matches ?? false;
const serverSnapshot = () => false;
const subscribe = (onChange) => {
  const query = mediaQuery();
  query?.addEventListener('change', onChange);
  return () => query?.removeEventListener('change', onChange);
};

export default function useCompactLayout() {
  return useSyncExternalStore(subscribe, snapshot, serverSnapshot);
}

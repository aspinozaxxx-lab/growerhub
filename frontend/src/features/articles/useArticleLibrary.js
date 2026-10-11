import { useSyncExternalStore } from 'react';
import { getArticles } from '../../content/articles';

const STORAGE_KEY = 'growerhub.article-library.v1';
const empty = Object.freeze({ saved: [], recent: [], ready: false, persistent: true });
const knownIds = new Set(getArticles('ru').map((article) => article.id));
const listeners = new Set();
let snapshot = empty;

const cleanIds = (ids) => Array.isArray(ids)
  ? [...new Set(ids.filter((id) => typeof id === 'string' && knownIds.has(id)))] : [];
const read = () => {
  try {
    const value = JSON.parse(window.localStorage.getItem(STORAGE_KEY) || '{}');
    return { saved: cleanIds(value?.saved), recent: cleanIds(value?.recent).slice(0, 12), ready: true, persistent: true };
  } catch {
    return { ...empty, ready: true, persistent: false };
  }
};
const emit = () => listeners.forEach((listener) => listener());
const onStorage = (event) => {
  if (event.key !== null && event.key !== STORAGE_KEY) return;
  snapshot = read();
  emit();
};
const subscribe = (listener) => {
  if (!listeners.size) {
    if (snapshot.ready && snapshot.persistent) snapshot = read();
    window.addEventListener('storage', onStorage);
  }
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
    if (!listeners.size) window.removeEventListener('storage', onStorage);
  };
};
const getSnapshot = () => {
  if (!snapshot.ready) snapshot = read();
  return snapshot;
};
const getServerSnapshot = () => empty;

const update = (change) => {
  // Pered zapisyu uchityvaem izmeneniya iz drugoj vkladki.
  const previous = getSnapshot();
  const current = previous.persistent ? read() : previous;
  const next = change(current);
  if (!next) return;
  try {
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify({ saved: next.saved, recent: next.recent }));
    snapshot = { ...next, persistent: true };
  } catch {
    snapshot = { ...next, persistent: false };
  }
  emit();
};
export const toggleSavedArticle = (id) => {
  if (!knownIds.has(id)) return;
  update((current) => ({ ...current, saved: current.saved.includes(id)
    ? current.saved.filter((value) => value !== id) : [id, ...current.saved] }));
};
export const rememberArticle = (id) => {
  if (!knownIds.has(id)) return;
  update((current) => current.recent[0] === id ? null : ({
    ...current, recent: [id, ...current.recent.filter((value) => value !== id)].slice(0, 12),
  }));
};
export const clearRecentArticles = () => update((current) => ({ ...current, recent: [] }));
export default function useArticleLibrary() {
  return useSyncExternalStore(subscribe, getSnapshot, getServerSnapshot);
}

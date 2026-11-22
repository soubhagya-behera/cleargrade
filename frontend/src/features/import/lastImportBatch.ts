export const LAST_IMPORT_BATCH_ID_KEY = 'cleargrade:last-import-batch-id';

function sessionStorageOrNull(): Storage | null {
  try {
    if (typeof window === 'undefined') return null;
    return window.sessionStorage ?? null;
  } catch {
    return null;
  }
}

export function readLastImportBatchId(): number | null {
  const storage = sessionStorageOrNull();
  if (!storage) return null;
  try {
    const raw = storage.getItem(LAST_IMPORT_BATCH_ID_KEY);
    if (raw === null) return null;
    const trimmed = raw.trim();
    if (!/^\d+$/.test(trimmed)) {
      storage.removeItem(LAST_IMPORT_BATCH_ID_KEY);
      return null;
    }
    const id = Number(trimmed);
    if (!Number.isSafeInteger(id) || id <= 0) {
      storage.removeItem(LAST_IMPORT_BATCH_ID_KEY);
      return null;
    }
    return id;
  } catch {
    return null;
  }
}

export function saveLastImportBatchId(id: number): void {
  const storage = sessionStorageOrNull();
  if (!storage) return;
  try {
    storage.setItem(LAST_IMPORT_BATCH_ID_KEY, String(id));
  } catch {
    // Storage can be unavailable or full; the current result still renders.
  }
}

export function clearLastImportBatchId(): void {
  const storage = sessionStorageOrNull();
  if (!storage) return;
  try {
    storage.removeItem(LAST_IMPORT_BATCH_ID_KEY);
  } catch {
    // Ignore storage failures.
  }
}

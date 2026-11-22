import type { CsvDownload } from '../../api/client';

export function saveCsvDownload(download: CsvDownload, fallbackFilename: string): void {
  const objectUrl = URL.createObjectURL(download.blob);
  const anchor = document.createElement('a');
  anchor.href = objectUrl;
  anchor.download = download.filename || fallbackFilename;
  anchor.style.position = 'fixed';
  anchor.style.left = '-10000px';
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  window.setTimeout(() => URL.revokeObjectURL(objectUrl), 0);
}

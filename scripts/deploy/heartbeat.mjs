import { randomUUID } from 'node:crypto';

export function backupHeartbeatUrl(settings) {
  const value = settings.CHANTER_BACKUP_HEARTBEAT_URL;
  if (!value) return null;
  try {
    const url = new URL(value);
    if (/[\s\0]/.test(value) || url.protocol !== 'https:' || !url.hostname.endsWith('.sentry.io')
      || url.username || url.password || url.port || url.search || url.hash
      || !/^\/api\/[0-9]{1,20}\/cron\/chanter-backup-verification\/[a-f0-9]{32}\/$/.test(url.pathname)) throw new Error();
    return url.href;
  } catch { throw new Error('Invalid private backup heartbeat configuration'); }
}

/** A successful ingestion response is acceptance, not evidence of operator notification. */
export async function sendBackupHeartbeat(settings, environment, receipt, send = fetch) {
  const target = backupHeartbeatUrl(settings);
  if (!target) return { status: 'disabled' };
  const checked = Date.parse(receipt?.checkedAt);
  if (!['production', 'staging'].includes(environment) || receipt?.status !== 'ok' || receipt.stale !== false
    || !/^[a-f0-9]{40}$/.test(receipt.release ?? '') || !Number.isFinite(checked)
    || checked > Date.now() + 1000 || Date.now() - checked > 60_000) throw new Error('Heartbeat requires a fresh verified backup');
  const url = new URL(target);
  url.searchParams.set('status', 'ok');
  url.searchParams.set('environment', environment);
  url.searchParams.set('check_in_id', randomUUID());
  try {
    const response = await send(url, { method: 'POST', redirect: 'error', credentials: 'omit', referrerPolicy: 'no-referrer',
      signal: AbortSignal.timeout(2000) });
    await response.body?.cancel();
    return { status: response.status === 202 ? 'accepted' : 'unconfirmed' };
  } catch { return { status: 'unconfirmed' }; }
}

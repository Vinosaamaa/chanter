/** A failed first migration still constrains later binaries, even without a successful release receipt. */
export function assertMigrationFloor(release, floor) {
  if (floor === null) return;
  if (!floor || !Number.isSafeInteger(floor.schemaEpoch) || floor.schemaEpoch < 1
      || !/^[a-f0-9]{40}$/.test(floor.commit ?? '')) throw new Error('Invalid persisted migration floor; inspect recovery state');
  if (release.schemaEpoch < floor.schemaEpoch) throw new Error('Deployment cannot cross the persisted migration floor; fix forward');
}

/** Project repository health into a bounded, credential-free operator receipt. */
export function summarizeBackup(info, now = Date.now()) {
  const stanza = Array.isArray(info) ? info.find(value => value.name === 'chanter') : null;
  if (!stanza || stanza.status?.code !== 0) throw new Error('Backup repository is unavailable or incomplete');
  const currentDatabase = (stanza.db ?? []).filter(value => value['repo-key'] === 1 && Number.isSafeInteger(value.id))
    .reduce((found, item) => Math.max(found, item.id), 0);
  if (!currentDatabase) throw new Error('Backup repository has no current database identity');
  const backups = (stanza.backup ?? []).filter(value => value.error === false
    && value.database?.['repo-key'] === 1 && value.database?.id === currentDatabase
    && ['full', 'diff', 'incr'].includes(value.type)
    && /^[0-9]{8}-[0-9]{6}F(?:_[0-9]{8}-[0-9]{6}[DI])?$/.test(value.label ?? '')
    && Number.isSafeInteger(value.timestamp?.stop) && value.timestamp.stop > 0
    && value.timestamp.stop * 1000 <= now + 300000);
  const latest = backups.reduce((found, item) => !found || item.timestamp.stop > found.timestamp.stop
    || (item.timestamp.stop === found.timestamp.stop && item.label > found.label) ? item : found, null);
  const fullLabel = latest?.label.split('_')[0];
  const full = backups.find(item => item.type === 'full' && item.label === fullLabel);
  if (!latest || !full) throw new Error('No complete recoverable backup chain is available');
  const byLabel = new Map(backups.map(item => [item.label, item]));
  const pending = [latest];
  const checked = new Set();
  while (pending.length) {
    const item = pending.pop();
    if (checked.has(item.label)) continue;
    checked.add(item.label);
    if (item.type === 'full' && item.label === fullLabel) continue;
    if (item.label.split('_')[0] !== fullLabel || !Array.isArray(item.reference)
        || !item.reference.includes(fullLabel) || typeof item.prior !== 'string') {
      throw new Error('Backup dependency chain is incomplete');
    }
    for (const label of new Set([...item.reference, item.prior])) {
      const dependency = byLabel.get(label);
      if (!dependency || dependency.label.split('_')[0] !== fullLabel
          || dependency.timestamp.stop > item.timestamp.stop || dependency.label >= item.label) throw new Error('Backup dependency chain is incomplete');
      pending.push(dependency);
    }
  }
  const configSnapshot = latest.annotation?.['config-snapshot'];
  if (!/^[a-f0-9]{8,64}$/.test(configSnapshot ?? '')) throw new Error('Database backup has no matching configuration snapshot');
  const backupRelease = latest.annotation?.release;
  if (!/^[a-f0-9]{40}$/.test(backupRelease ?? '')) throw new Error('Database backup has no valid release identity');
  return { label: latest.label, type: latest.type, completedAt: new Date(latest.timestamp.stop * 1000).toISOString(),
    configSnapshot, backupRelease,
    fullCompletedAt: new Date(full.timestamp.stop * 1000).toISOString(),
    stale: now - latest.timestamp.stop * 1000 > 30 * 3600000 || now - full.timestamp.stop * 1000 > 8 * 86400000 };
}

export function backupUnits(stateDir, nodeExecutable = '/usr/bin/node') {
  if ([stateDir, nodeExecutable].some(value => !/^\/[A-Za-z0-9_./-]+$/.test(value) || value.split('/').includes('..'))) {
    throw new Error('Backup timers require an absolute Linux state path without shell or unit substitutions');
  }
  return Object.fromEntries(Object.entries({ full: 'Sun *-*-* 02:00:00 UTC',
    incr: 'Mon..Sat *-*-* 02:00:00 UTC', check: '*-*-* *:00/10:00 UTC' }).flatMap(([type, schedule]) => {
    const name = `chanter-backup-${type}`;
    return [[`${name}.service`, `[Unit]\nDescription=Chanter database backup ${type}\nAfter=docker.service network-online.target\nWants=network-online.target\n\n[Service]\nType=oneshot\nUMask=0077\nNice=10\nIOSchedulingClass=best-effort\nIOSchedulingPriority=7\nTimeoutStartSec=3h\nExecStart=${nodeExecutable} ${stateDir}/backup-runner.mjs ${stateDir} ${type}\n`],
      [`${name}.timer`, `[Unit]\nDescription=Chanter database backup ${type} schedule\n\n[Timer]\nOnCalendar=${schedule}\nPersistent=true\nRandomizedDelaySec=${type === 'check' ? '30' : '600'}\n\n[Install]\nWantedBy=timers.target\n`]];
  }));
}

/** Production backup policy. Secrets are returned only for private container configuration. */
export function backupEnvironment(settings, environment = 'production') {
  if (!['staging', 'production'].includes(environment)) throw new Error('Invalid backup environment');
  const required = (key) => {
    const value = settings[key];
    if (typeof value !== 'string' || !value.trim()) throw new Error(`${key} is required`);
    if (/[\r\n\0]/.test(value) || value.trim() !== value) throw new Error(`${key} has invalid characters`);
    return value;
  };
  // Parse only after checking control characters: URL parsers otherwise normalize them away.
  const rawEndpoint = settings.CHANTER_BACKUP_S3_ENDPOINT;
  if (typeof rawEndpoint !== 'string' || !rawEndpoint.trim()) throw new Error('Backup HTTPS origin is required');
  let endpoint;
  try { endpoint = new URL(rawEndpoint); } catch { throw new Error('Backup endpoint must be an HTTPS origin'); }
  if (/[\s\0]/.test(rawEndpoint) || endpoint.protocol !== 'https:' || !endpoint.hostname
      || endpoint.username || endpoint.password || endpoint.search || endpoint.hash
      || !['', '/'].includes(endpoint.pathname) || endpoint.port === '0') {
    throw new Error('Backup endpoint must be an HTTPS origin');
  }
  const bucket = required('CHANTER_BACKUP_S3_BUCKET');
  if (!/^[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]$/.test(bucket) || bucket.includes('..')
      || /^\d+\.\d+\.\d+\.\d+$/.test(bucket)) throw new Error('Invalid backup bucket');
  const region = required('CHANTER_BACKUP_S3_REGION');
  if (!/^[a-z0-9][a-z0-9-]{0,62}$/.test(region)) throw new Error('Invalid backup region');
  const accessKey = required('CHANTER_BACKUP_S3_ACCESS_KEY');
  const secretKey = required('CHANTER_BACKUP_S3_SECRET_KEY');
  const cipherPass = required('CHANTER_BACKUP_CIPHER_PASS');
  if (Buffer.byteLength(cipherPass) < 32) throw new Error('Backup encryption passphrase requires at least 32 bytes');
  return {
    PGBACKREST_STANZA: 'chanter',
    PGBACKREST_PG1_PATH: '/var/lib/postgresql/data',
    PGBACKREST_PG1_SOCKET_PATH: '/var/run/postgresql',
    PGBACKREST_PG1_USER: 'chanter_admin',
    PGBACKREST_REPO1_TYPE: 's3',
    PGBACKREST_REPO1_PATH: `/chanter/${environment}`,
    PGBACKREST_REPO1_S3_ENDPOINT: endpoint.hostname,
    PGBACKREST_REPO1_STORAGE_PORT: endpoint.port || '443',
    PGBACKREST_REPO1_STORAGE_VERIFY_TLS: 'y',
    PGBACKREST_REPO1_S3_BUCKET: bucket,
    PGBACKREST_REPO1_S3_REGION: region,
    PGBACKREST_REPO1_S3_URI_STYLE: 'path',
    PGBACKREST_REPO1_S3_KEY: accessKey,
    PGBACKREST_REPO1_S3_KEY_SECRET: secretKey,
    PGBACKREST_REPO1_CIPHER_TYPE: 'aes-256-cbc',
    PGBACKREST_REPO1_CIPHER_PASS: cipherPass,
    PGBACKREST_REPO1_RETENTION_FULL: '2',
    PGBACKREST_REPO1_RETENTION_HISTORY: '30',
    PGBACKREST_COMPRESS_TYPE: 'zst',
    PGBACKREST_COMPRESS_LEVEL: '1',
    PGBACKREST_PROCESS_MAX: '1',
    PGBACKREST_START_FAST: 'y',
    PGBACKREST_ARCHIVE_ASYNC: 'n',
    PGBACKREST_LOG_LEVEL_CONSOLE: 'warn',
    PGBACKREST_LOG_LEVEL_FILE: 'off',
    PGBACKREST_LOCK_PATH: '/tmp/pgbackrest',
  };
}

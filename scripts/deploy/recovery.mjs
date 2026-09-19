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

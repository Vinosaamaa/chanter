/** Error reporting is independent of trace export and disabled until a receiver is configured. */
export function errorEnvironment(settings) {
  const dsn = settings.CHANTER_ERRORS_DSN ?? '';
  if (!dsn) return { CHANTER_ERRORS_ENABLED: 'false' };
  let url;
  try {
    url = new URL(dsn);
    if (url.protocol !== 'https:' || !url.hostname.endsWith('.sentry.io') || !/^[a-f0-9]{32}$/.test(url.username)
        || url.password || !/^\/[0-9]{1,20}$/.test(url.pathname) || url.search || url.hash || url.port
        || /[\s\0]/.test(dsn)) throw new Error();
  } catch { throw new Error('Error reporting requires a valid Sentry HTTPS DSN'); }
  return { CHANTER_ERRORS_ENABLED: 'true', CHANTER_ERRORS_DSN: url.href };
}

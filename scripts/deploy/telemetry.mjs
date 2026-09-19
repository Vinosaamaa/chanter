/** Export remains visibly disabled until a private HTTPS receiver is configured. */
export function telemetryEnvironment(settings) {
  const endpoint = settings.CHANTER_TELEMETRY_ENDPOINT ?? '';
  const authorization = settings.CHANTER_TELEMETRY_AUTHORIZATION ?? '';
  if (!endpoint && !authorization) return { CHANTER_TELEMETRY_ENABLED: 'false', OTEL_TRACES_EXPORTER: 'none', OTEL_METRICS_EXPORTER: 'none' };
  let url;
  try { url = new URL(endpoint); } catch { throw new Error('Telemetry requires a private HTTPS trace endpoint'); }
  if (url.protocol !== 'https:' || url.username || url.password || url.search || url.hash
      || !url.hostname || !url.pathname.endsWith('/v1/traces') || /[\s\0]/.test(endpoint)
      || !authorization.trim() || /[\r\n\0]/.test(authorization) || authorization.length > 2048) {
    throw new Error('Telemetry requires a private HTTPS trace endpoint and valid authorization');
  }
  return { CHANTER_TELEMETRY_ENABLED: 'true', OTEL_TRACES_EXPORTER: 'otlp', OTEL_EXPORTER_OTLP_PROTOCOL: 'http/protobuf',
    OTEL_METRICS_EXPORTER: 'otlp', OTEL_METRIC_EXPORT_INTERVAL: '60000',
    OTEL_INSTRUMENTATION_MICROMETER_ENABLED: 'true',
    OTEL_EXPORTER_OTLP_METRICS_ENDPOINT: endpoint.slice(0, -'traces'.length) + 'metrics',
    OTEL_EXPORTER_OTLP_METRICS_HEADERS: `Authorization=${encodeURIComponent(authorization)}`,
    OTEL_EXPORTER_OTLP_TRACES_ENDPOINT: endpoint, OTEL_EXPORTER_OTLP_TRACES_HEADERS: `Authorization=${encodeURIComponent(authorization)}`,
    OTEL_JAVAAGENT_EXTENSIONS: '/app/telemetry/privacy.jar', OTEL_JAVAAGENT_LOGGING: 'application',
    OTEL_TRACES_SAMPLER: 'traceidratio', OTEL_TRACES_SAMPLER_ARG: '0.05' };
}

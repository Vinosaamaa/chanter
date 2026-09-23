export function audioStatsAccumulator() {
  const reports = new Map()
  return {
    record(peer, items) {
      const streams = reports.get(peer) ?? new Map()
      reports.set(peer, streams)
      // Removed RTP objects disappear from getStats; their IDs are never reused.
      // Retain observed counters so removal cannot erase reception evidence.
      for (const item of items) {
        if (item.kind !== 'audio' || !['inbound-rtp', 'outbound-rtp'].includes(item.type)) continue
        streams.set(item.id, {
          sent: item.type === 'outbound-rtp' ? item.bytesSent ?? 0 : 0,
          received: item.type === 'inbound-rtp' ? item.bytesReceived ?? 0 : 0,
          energy: item.type === 'inbound-rtp' ? item.totalAudioEnergy ?? 0 : 0,
        })
      }
    },
    totals() {
      let sent = 0, received = 0, energy = 0
      for (const streams of reports.values()) for (const item of streams.values()) {
        sent += item.sent; received += item.received; energy += item.energy
      }
      return { sent, received, energy }
    },
  }
}

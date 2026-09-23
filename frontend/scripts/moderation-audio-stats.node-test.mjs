import assert from 'node:assert/strict'
import test from 'node:test'
import { audioStatsAccumulator } from '../e2e/moderation-audio-stats.mjs'

const inbound = (id, bytesReceived, totalAudioEnergy = 1) => ({ id, kind: 'audio', type: 'inbound-rtp', bytesReceived, totalAudioEnergy })

test('removing an RTP report after moderation does not erase observed received bytes', () => {
  const stats = audioStatsAccumulator(), peer = {}
  stats.record(peer, [inbound('removed-track', 66226)])
  const afterRemoval = stats.totals()
  stats.record(peer, [])
  assert.deepEqual(stats.totals(), afterRemoval)
})

test('continued reception remains detectable after another report disappears', () => {
  const stats = audioStatsAccumulator(), peer = {}
  stats.record(peer, [inbound('old', 100), inbound('live', 20)])
  const before = stats.totals().received
  stats.record(peer, [inbound('live', 30)])
  assert.equal(stats.totals().received, before + 10)
})

test('new streams and separate peer connections count independently without counting snapshots twice', () => {
  const stats = audioStatsAccumulator(), first = {}, second = {}
  stats.record(first, [inbound('same-id', 100)])
  stats.record(first, [inbound('same-id', 100)])
  stats.record(second, [inbound('same-id', 50)])
  stats.record(first, [inbound('new-id', 5), { id: 'video', kind: 'video', type: 'inbound-rtp', bytesReceived: 1000 }])
  assert.equal(stats.totals().received, 155)
})

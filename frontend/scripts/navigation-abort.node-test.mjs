import assert from 'node:assert/strict'
import test from 'node:test'
import { isExpectedRequestAbort } from '../e2e/browser-health.mjs'

test('only a request belonging to the replaced document may report a navigation abort', () => {
  for (const error of ['net::ERR_ABORTED', 'NS_BINDING_ABORTED', 'Load request cancelled']) {
    assert.equal(isExpectedRequestAbort(false, error, undefined, true), true)
    assert.equal(isExpectedRequestAbort(false, error, undefined, false), false)
    assert.equal(isExpectedRequestAbort(false, error, 403, true), false)
  }
  assert.equal(isExpectedRequestAbort(false, 'net::ERR_CONNECTION_RESET', undefined, true), false)
  assert.equal(isExpectedRequestAbort(false, 'NS_ERROR_NET_TIMEOUT', undefined, true), false)
})
import { navigationAbortTracker } from '../e2e/browser-health.mjs'

test('a started or failed navigation does not replace the original document', () => {
  const tracker = navigationAbortTracker(), api = {}, navigation = {}
  tracker.started(api, false)
  tracker.started(navigation, true)
  tracker.failed(api)
  assert.equal(tracker.wasReplaced(api), false)
  tracker.failed(navigation)
  tracker.committed(navigation)
  assert.equal(tracker.wasReplaced(api), false)
})
test('only successful commit of the current navigation releases old-document aborts', () => {
  const tracker = navigationAbortTracker(), api = {}, first = {}, redirect = {}, unrelated = {}
  tracker.started(api, false)
  tracker.started(first, true)
  tracker.failed(api)
  tracker.started(redirect, true, first)
  tracker.committed(unrelated)
  assert.equal(tracker.wasReplaced(api), false)
  tracker.committed(redirect)
  assert.equal(tracker.wasReplaced(api), true)
  const currentApi = {}
  tracker.started(currentApi, false)
  tracker.failed(currentApi)
  assert.equal(tracker.wasReplaced(currentApi), false)
})

test('old-document polling during pending navigation needs a successful commit too', () => {
  for (const commits of [true, false]) {
    const tracker = navigationAbortTracker(), navigation = {}, poll = {}
    tracker.started(navigation, true)
    tracker.started(poll, false)
    tracker.failed(poll)
    if (commits) tracker.committed(navigation)
    else tracker.failed(navigation)
    assert.equal(tracker.wasReplaced(poll), commits)
  }
})

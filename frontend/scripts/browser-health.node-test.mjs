import assert from 'node:assert/strict'
import test from 'node:test'
import { isExpectedRequestAbort } from '../e2e/browser-health.mjs'

test('bodyless response aborts require an observed successful 204', () => {
  assert.equal(isExpectedRequestAbort(false, 'net::ERR_ABORTED', 204), true)
  assert.equal(isExpectedRequestAbort(false, 'net::ERR_ABORTED', undefined), false)
  assert.equal(isExpectedRequestAbort(false, 'net::ERR_ABORTED', 200), false)
  assert.equal(isExpectedRequestAbort(false, 'net::ERR_ABORTED', 401), false)
  assert.equal(isExpectedRequestAbort(false, 'net::ERR_CONNECTION_RESET', 204), false)
})

test('navigation cancellation is distinct from a failed API request', () => {
  assert.equal(isExpectedRequestAbort(true, 'net::ERR_ABORTED', undefined), true)
  assert.equal(isExpectedRequestAbort(true, 'NS_BINDING_ABORTED', undefined), true)
  assert.equal(isExpectedRequestAbort(true, 'net::ERR_CONNECTION_REFUSED', undefined), false)
})

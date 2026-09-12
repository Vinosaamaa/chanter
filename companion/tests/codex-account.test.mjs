import assert from 'node:assert/strict';
import test from 'node:test';
import { accountSummary, limitSummary, modelSummary } from '../src/codex-account.mjs';

test('account summaries expose the ChatGPT category and reject other account types', () => {
  assert.deepEqual(accountSummary({ account: null, requiresOpenaiAuth: true }), {
    state: 'signed-out', provider: 'codex', plan: null,
  });
  assert.deepEqual(accountSummary({ account: { type: 'chatgpt', email: 'private@example.invalid',
    planType: 'plus', accountId: 'private-id' }, requiresOpenaiAuth: true }), {
    state: 'subscription', provider: 'codex', plan: 'plus',
  });
  for (const type of ['apiKey', 'chatgptAuthTokens', 'future-auth']) {
    assert.deepEqual(accountSummary({ account: { type, apiKey: 'private-key' } }), {
      state: 'unsupported-auth', provider: 'codex', plan: null,
    });
  }
});

test('unknown usage stays unknown and model-specific limits do not leak account metadata', () => {
  assert.deepEqual(limitSummary({}), { primary: null, secondary: null, spendControlReached: null });
  assert.deepEqual(limitSummary({ accountId: 'private-id', rateLimitsByLimitId: { codex: {
    primary: { usedPercent: 0, windowDurationMins: 300, resetsAt: 1800000000 },
    secondary: null, spendControlReached: false, credits: { balance: 'private-balance' },
  } } }), { primary: { usedPercent: 0, windowDurationMins: 300, resetsAt: 1800000000 },
    secondary: null, spendControlReached: false });
  assert.equal(limitSummary({ rateLimits: { primary: { usedPercent: -1 } } }).primary, null);
});

test('native model summaries retain selection identity and omit hidden or malformed models', () => {
  assert.deepEqual(modelSummary({ id: 'gpt-6-astra', model: 'gpt-6-astra', displayName: 'Astra', hidden: false,
    inputModalities: ['text'], supportedReasoningEfforts: [{ reasoningEffort: 'high', description: 'Private extra' }] }),
  { id: 'gpt-6-astra', model: 'gpt-6-astra', label: 'Astra', reasoningEfforts: ['high'] });
  assert.equal(modelSummary({ id: 'hidden', model: 'hidden', displayName: 'Hidden', hidden: true }), null);
  assert.equal(modelSummary({ id: 'unsafe\nvalue', model: 'model', displayName: 'Label' }), null);
  for (const invalid of [{ inputModalities: {} }, { supportedReasoningEfforts: {} }]) {
    assert.equal(modelSummary({ id: 'model', model: 'model', displayName: 'Label', hidden: false, ...invalid }), null);
  }
});

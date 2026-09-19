const PLANS = new Set(['free', 'go', 'plus', 'pro', 'team', 'business', 'enterprise', 'education']);

/** Expose entitlement category only, never provider identity or credentials. */
export function accountSummary(response) {
  const account = response?.account;
  return {
    state: account == null ? 'signed-out' : account.type === 'chatgpt' && PLANS.has(account.planType) ? 'subscription' : 'unsupported-auth',
    provider: 'codex',
    plan: account?.type === 'chatgpt' && PLANS.has(account.planType) ? account.planType : null,
  };
}

export function limitSummary(response) {
  const limits = response?.rateLimitsByLimitId?.codex ?? response?.rateLimits;
  return { primary: windowSummary(limits?.primary), secondary: windowSummary(limits?.secondary),
    spendControlReached: typeof limits?.spendControlReached === 'boolean' ? limits.spendControlReached : null };
}

function windowSummary(window) {
  if (!Number.isInteger(window?.usedPercent) || window.usedPercent < 0 || window.usedPercent > 100) return null;
  return { usedPercent: window.usedPercent,
    windowDurationMins: Number.isSafeInteger(window.windowDurationMins) && window.windowDurationMins > 0 ? window.windowDurationMins : null,
    resetsAt: Number.isSafeInteger(window.resetsAt) && window.resetsAt > 0 ? window.resetsAt : null };
}

export function modelSummary(model) {
  const identifier = /^[a-zA-Z0-9][a-zA-Z0-9._:/-]{0,127}$/;
  if (!model || model.hidden !== false || !identifier.test(model.id ?? '') || !identifier.test(model.model ?? '')
      || (model.inputModalities != null && (!Array.isArray(model.inputModalities) || !model.inputModalities.includes('text')))
      || (model.supportedReasoningEfforts != null && !Array.isArray(model.supportedReasoningEfforts))
      || typeof model.displayName !== 'string' || model.displayName.length < 1 || model.displayName.length > 80
      || /[\x00-\x1f\x7f]/.test(model.displayName)) return null;
  return { id: model.id, model: model.model, label: model.displayName,
    reasoningEfforts: [...new Set((model.supportedReasoningEfforts ?? []).slice(0, 16)
      .map((option) => option?.reasoningEffort).filter((effort) => /^[a-z][a-z0-9_-]{0,31}$/.test(effort ?? '')))] };
}

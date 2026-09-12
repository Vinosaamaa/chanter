package com.chanter.agent.infra;

import java.net.URI;

/** xAI API credentials and usage belong to the configured xAI account, not to OpenAI. */
public final class XaiLlmChatClient extends ResponsesLlmChatClient {
    public XaiLlmChatClient(URI endpoint, String key, String model) { super(endpoint, key, model, "xai"); }
}

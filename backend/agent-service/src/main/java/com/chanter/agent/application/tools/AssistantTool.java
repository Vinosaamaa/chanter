package com.chanter.agent.application.tools;

import com.chanter.agent.application.AssistantGrantScopeService.GrantScope;
import java.util.Map;

public interface AssistantTool {

    String name();

    String description();

    Map<String, Object> inputSchema();

    Object invoke(GrantScope scope, Map<String, Object> arguments);
}

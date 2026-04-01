# Agent V2 Step 1 + Step 2 Checklist (Demo-First)

## Goal
Implement orchestrator + query tools so Floating AI can reason over tiered context and execute read tools reliably.

## Step 1: Core Agent Orchestrator Scaffolding

1. Confirm tool name constants are stable
- Class: `AgentToolNames`
- Methods/signatures:
  - `public static final String GET_TODAY_TASKS`
  - `public static final String GET_OVERDUE_TASKS`
  - `public static final String FIND_TASKS`

2. Confirm execution context model exists
- Class: `AgentExecutionContext`
- Methods/signatures:
  - `public static AgentExecutionContext create(Application application)`
  - `public TaskDatabase getDatabase()`
  - `public TaskRepository getTaskRepository()`
  - `public long getNowMillis()`

3. Confirm tool contract interface exists
- Interface: `AgentTool`
- Methods/signatures:
  - `String getName()`
  - `JSONObject getSchema()`
  - `ToolResult execute(ToolCall call, AgentExecutionContext context) throws Exception`
  - `default boolean isMutation()`
  - `default boolean requiresConfirmation(ToolCall call)`

4. Confirm tool call/result models exist
- Class: `ToolCall`
- Methods/signatures:
  - `public static ToolCall fromEnvelope(AgentResponseEnvelope envelope)`
  - `public String getCallId()`
  - `public String getToolName()`
  - `public JSONObject getArguments()`

- Class: `ToolResult`
- Methods/signatures:
  - `public static ToolResult success(String callId, String toolName, JSONObject data)`
  - `public static ToolResult failure(String callId, String toolName, String errorCode, String errorMessage)`
  - `public JSONObject toJson()`

5. Confirm tool registry exists
- Class: `AgentToolRegistry`
- Methods/signatures:
  - `public static AgentToolRegistry withDefaultQueryTools()`
  - `public void register(AgentTool tool)`
  - `public AgentTool get(String toolName)`
  - `public JSONArray getToolSchemas()`

6. Confirm tiered context assembler exists
- Class: `AgentContextAssembler`
- Methods/signatures:
  - `public String buildTieredContextBlock(String userMessage)`
  - `public JSONObject buildTieredContextJson(String userMessage)`
- Tier logic included:
  - Tier 0: now/timezone/counts/top priority
  - Tier 1: overdue/due soon/recent completed

7. Confirm orchestrator shell exists
- Class: `AgentOrchestrator`
- Methods/signatures:
  - `public void handleUserMessage(String userMessage, Callback callback)`
  - `public JSONArray getToolSchemas()`
  - internal routing to tool dispatch and response rendering

## Step 2: Query Tools (Demo Core)

1. Get today tasks
- Class: `GetTodayTasksTool`
- Methods/signatures:
  - `public String getName()`
  - `public JSONObject getSchema()`
  - `public ToolResult execute(ToolCall call, AgentExecutionContext context)`
- Args:
  - `limit`
  - `includeCompleted`

2. Get overdue tasks
- Class: `GetOverdueTasksTool`
- Methods/signatures:
  - `public String getName()`
  - `public JSONObject getSchema()`
  - `public ToolResult execute(ToolCall call, AgentExecutionContext context)`
- Args:
  - `limit`

3. Find tasks
- Class: `FindTasksTool`
- Methods/signatures:
  - `public String getName()`
  - `public JSONObject getSchema()`
  - `public ToolResult execute(ToolCall call, AgentExecutionContext context)`
- Args:
  - `queryText`
  - `fromDateMillis`
  - `toDateMillis`
  - `priorityMin`
  - `priorityMax`
  - `includeCompleted`
  - `limit`

## Tomorrow Start Order (Recommended)

1. Integrate orchestrator into `FloatingAssistantService` message pipeline.
2. Route non-CHAT actions to `ToolCall` dispatch via `AgentOrchestrator`.
3. Render `ToolResult` summary text to chat.
4. Integrate orchestrator into `AiAssistantActivity` for parity.
5. Verify 3 demo prompts:
- "What is due today?"
- "Show overdue tasks"
- "Find coding tasks this week"

## Build Check
Run:
- `./gradlew :app:compileDebugJavaWithJavac`

Expected:
- Build success with new agent scaffolding and query tools.

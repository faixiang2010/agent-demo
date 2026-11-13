import { StateGraph, Annotation, START, END } from "@langchain/langgraph";
import { HumanMessage, AIMessage, SystemMessage } from "@langchain/core/messages";

const AgentState = Annotation.Root({
  messages: Annotation<any[]>({
    reducer: (x: any[], y: any[]) => x.concat(y),
  }),
  nextAction: Annotation<string>,
});

async function router(state: typeof AgentState.State) {
  const lastMessage = state.messages[state.messages.length - 1];
  
  if (lastMessage.content.toLowerCase().includes("search")) {
    return "search_tool";
  }
  return "respond";
}

async function searchTool(state: typeof AgentState.State) {
  console.log("🔍 Executing search tool...");
  return {
    messages: [new AIMessage("I found some relevant information for you!")],
    nextAction: "respond",
  };
}

async function respond(state: typeof AgentState.State) {
  console.log("💬 Generating response...");
  const responses = [
    "I understand your question. Let me help you with that.",
    "That's an interesting point! Here's what I think...",
    "Great question! Based on my analysis...",
  ];
  const randomResponse = responses[Math.floor(Math.random() * responses.length)];
  
  return {
    messages: [new AIMessage(randomResponse)],
    nextAction: END,
  };
}

const workflow = new StateGraph(AgentState)
  .addNode("agent", router)
  .addNode("search_tool", searchTool)
  .addNode("respond", respond)
  .addEdge(START, "agent")
  .addConditionalEdges("agent", router, {
    search_tool: "search_tool",
    respond: "respond",
  })
  .addEdge("search_tool", "respond")
  .addEdge("respond", END);

const app = workflow.compile();

async function runAdvancedExample() {
  console.log("🎯 Advanced LangGraph Example with Conditional Edges\n");
  console.log("=" .repeat(50));

  const testQueries = [
    "Hello, how are you?",
    "Can you search for information about AI?",
    "What's the weather like today?",
  ];

  for (const query of testQueries) {
    console.log(`\n❓ User: ${query}`);
    
    const result = await app.invoke({
      messages: [new HumanMessage(query)],
      nextAction: "",
    });

    console.log(`🤖 Assistant: ${result.messages[result.messages.length - 1].content}`);
    console.log("-".repeat(50));
  }

  console.log("\n✅ Advanced example completed!");
}

runAdvancedExample().catch(console.error);
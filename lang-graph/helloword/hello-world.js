const { StateGraph, Annotation, START, END } = require("@langchain/langgraph");
const { HumanMessage, AIMessage } = require("@langchain/core/messages");

const StateAnnotation = Annotation.Root({
  messages: Annotation({
    reducer: (x, y) => x.concat(y),
  }),
  greeting: Annotation(),
});

async function chatbot(state) {
  return {
    messages: [new AIMessage("Hello! I'm a chatbot powered by LangGraph.")],
    greeting: "Hello from LangGraph!",
  };
}

const graph = new StateGraph(StateAnnotation)
  .addNode("chatbot", chatbot)
  .addEdge(START, "chatbot")
  .addEdge("chatbot", END)
  .compile();

async function main() {
  console.log("🚀 Running LangGraph Hello World Example...\n");

  const result = await graph.invoke({
    messages: [new HumanMessage("Hello!")],
    greeting: "",
  });

  console.log("✅ Graph execution completed!\n");
  console.log("📝 Result:");
  console.log("- Messages:", result.messages.map(m => m.content).join("\n  "));
  console.log("- Greeting:", result.greeting);
  
  console.log("\n📊 Full State Object:");
  console.log(JSON.stringify(result, null, 2));
}

main().catch(console.error);
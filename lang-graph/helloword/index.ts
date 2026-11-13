import { StateGraph, Annotation, START, END } from "@langchain/langgraph";
import { HumanMessage, AIMessage } from "@langchain/core/messages";

const StateAnnotation = Annotation.Root({
  messages: Annotation<BaseMessage[]>({
    reducer: (x, y) => x.concat(y),
  }),
  greeting: Annotation<string>,
});

async function chatbot(state: typeof StateAnnotation.State) {
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
  console.log("- Messages:", result.messages.map((m: BaseMessage) => m.content).join("\n  "));
  console.log("- Greeting:", result.greeting);
}

main().catch(console.error);
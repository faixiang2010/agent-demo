const { StateGraph, MessagesAnnotation, START, END } = require("@langchain/langgraph");
const { HumanMessage, AIMessage } = require("@langchain/core/messages");

async function chatbot(state) {
  const lastMessage = state.messages[state.messages.length - 1];
  console.log(`👤 User: ${lastMessage.content}`);
  
  const response = `You said: "${lastMessage.content}". This is a response from LangGraph!`;
  console.log(`🤖 Bot: ${response}\n`);
  
  return {
    messages: [new AIMessage(response)],
  };
}

const graph = new StateGraph(MessagesAnnotation)
  .addNode("chatbot", chatbot)
  .addEdge(START, "chatbot")
  .addEdge("chatbot", END)
  .compile();

async function runSimpleChat() {
  console.log("💬 Simple Chatbot with MessagesAnnotation\n");
  console.log("=" .repeat(50));
  
  const messages = [
    new HumanMessage("Hello!"),
    new HumanMessage("How are you?"),
    new HumanMessage("What can you do?"),
  ];
  
  for (const message of messages) {
    await graph.invoke({
      messages: [message],
    });
  }
  
  console.log("=" .repeat(50));
  console.log("✅ Simple chat completed!");
}

runSimpleChat().catch(console.error);
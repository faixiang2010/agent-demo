const { StateGraph, MessagesAnnotation, START, END } = require("@langchain/langgraph");
const { HumanMessage, AIMessage, SystemMessage } = require("@langchain/core/messages");
const { ChatOllama } = require("@langchain/community/chat_models/ollama");

const model = new ChatOllama({
  baseUrl: "http://localhost:11434",
  model: "llama3.2",
  temperature: 0.7,
});

async function chatbot(state) {
  const lastMessage = state.messages[state.messages.length - 1];
  console.log(`\n👤 User: ${lastMessage.content}`);
  console.log("🤖 Bot is thinking (using local Ollama model)...");
  
  const systemMessage = new SystemMessage(
    "You are a helpful assistant powered by LangGraph and Ollama (local LLM). " +
    "Be friendly, concise, and helpful in your responses."
  );
  
  const response = await model.invoke([systemMessage, ...state.messages]);
  
  console.log(`✅ Response received!`);
  
  return {
    messages: [response],
  };
}

const graph = new StateGraph(MessagesAnnotation)
  .addNode("chatbot", chatbot)
  .addEdge(START, "chatbot")
  .addEdge("chatbot", END)
  .compile();

async function runChatWithOllama() {
  console.log("🤖 Chatbot with Local LLM (Ollama + Llama 3.2)\n");
  console.log("=" .repeat(60));
  console.log("⚠️  Make sure Ollama is running locally!");
  console.log("   Download: https://ollama.ai");
  console.log("   Run: ollama serve && ollama pull llama3.2");
  console.log("=" .repeat(60));
  
  try {
    const messages = [
      new HumanMessage("Hello! Can you introduce yourself?"),
      new HumanMessage("What is LangGraph?"),
      new HumanMessage("Give me a simple coding example in JavaScript."),
    ];
    
    for (const message of messages) {
      const result = await graph.invoke({
        messages: [message],
      });
      
      const aiResponse = result.messages[result.messages.length - 1];
      console.log(`\n📝 AI Response:\n${aiResponse.content}`);
      console.log("-".repeat(60));
      
      // 添加延迟
      await new Promise(resolve => setTimeout(resolve, 500));
    }
    
    console.log("\n✅ Chat completed successfully!");
    
  } catch (error) {
    if (error.message.includes("ECONNREFUSED") || error.message.includes("fetch failed")) {
      console.error("\n❌ Error: Cannot connect to Ollama!");
      console.error("\n🔧 To fix this:");
      console.error("   1. Install Ollama from https://ollama.ai");
      console.error("   2. Start Ollama: ollama serve");
      console.error("   3. Pull model: ollama pull llama3.2");
      console.error("   4. Run this script again\n");
    } else {
      console.error("\n❌ Error:", error.message);
    }
  }
}

runChatWithOllama();
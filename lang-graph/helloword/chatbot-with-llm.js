const { StateGraph, MessagesAnnotation, START, END } = require("@langchain/langgraph");
const { HumanMessage, AIMessage, SystemMessage } = require("@langchain/core/messages");
const { ChatOpenAI } = require("@langchain/openai");

const model = new ChatOpenAI({
  modelName: "gpt-3.5-turbo",
  temperature: 0.7,
});

async function chatbot(state) {
  const lastMessage = state.messages[state.messages.length - 1];
  console.log(`\n👤 User: ${lastMessage.content}`);
  console.log("🤖 Bot is thinking...");
  
  const systemMessage = new SystemMessage(
    "You are a helpful assistant powered by LangGraph and OpenAI. " +
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

async function runChatWithLLM() {
  console.log("🤖 Chatbot with Real LLM (OpenAI GPT-3.5-turbo)\n");
  console.log("=" .repeat(60));
  console.log("⚠️  Make sure you have set OPENAI_API_KEY environment variable!");
  console.log("=" .repeat(60));
  
  try {
    const messages = [
      new HumanMessage("Hello! Can you introduce yourself?"),
      new HumanMessage("What is LangGraph?"),
      new HumanMessage("Can you give me a simple example?"),
    ];
    
    for (const message of messages) {
      const result = await graph.invoke({
        messages: [message],
      });
      
      const aiResponse = result.messages[result.messages.length - 1];
      console.log(`\n📝 AI Response: ${aiResponse.content}`);
      console.log("-".repeat(60));
      
      // 添加延迟，避免 API 速率限制
      await new Promise(resolve => setTimeout(resolve, 1000));
    }
    
    console.log("\n✅ Chat completed successfully!");
    
  } catch (error) {
    if (error.message.includes("API key")) {
      console.error("\n❌ Error: OpenAI API key not configured!");
      console.error("\n🔧 To fix this:");
      console.error("   1. Get your API key from https://platform.openai.com/api-keys");
      console.error("   2. Set environment variable:");
      console.error('      $env:OPENAI_API_KEY="your-api-key-here"');
      console.error("   3. Run this script again\n");
    } else {
      console.error("\n❌ Error:", error.message);
    }
  }
}

runChatWithLLM();
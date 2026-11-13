const { StateGraph, MessagesAnnotation, START, END, Annotation } = require("@langchain/langgraph");
const { HumanMessage, AIMessage } = require("@langchain/core/messages");

const StateAnnotation = Annotation.Root({
  messages: Annotation({
    reducer: (x, y) => x.concat(y),
  }),
  category: Annotation(),
});

async function categorize(state) {
  const lastMessage = state.messages[state.messages.length - 1].content.toLowerCase();
  
  let category;
  if (lastMessage.includes("hello") || lastMessage.includes("hi")) {
    category = "greeting";
  } else if (lastMessage.includes("help") || lastMessage.includes("question")) {
    category = "question";
  } else {
    category = "general";
  }
  
  console.log(`📂 Categorized as: ${category}`);
  return { category };
}

async function handleGreeting(state) {
  console.log("😊 Handling greeting...");
  return {
    messages: [new AIMessage("Hello there! Nice to meet you! 👋")],
  };
}

async function handleQuestion(state) {
  console.log("❓ Handling question...");
  return {
    messages: [new AIMessage("I'd be happy to help you with that question! 📚")],
  };
}

async function handleGeneral(state) {
  console.log("💬 Handling general message...");
  return {
    messages: [new AIMessage("Thanks for sharing that with me! 🌟")],
  };
}

function routeByCategory(state) {
  return state.category;
}

const graph = new StateGraph(StateAnnotation)
  .addNode("categorize", categorize)
  .addNode("greeting", handleGreeting)
  .addNode("question", handleQuestion)
  .addNode("general", handleGeneral)
  .addEdge(START, "categorize")
  .addConditionalEdges("categorize", routeByCategory, {
    greeting: "greeting",
    question: "question",
    general: "general",
  })
  .addEdge("greeting", END)
  .addEdge("question", END)
  .addEdge("general", END)
  .compile();

async function runConditionalExample() {
  console.log("🎯 Conditional Edges Example\n");
  console.log("=" .repeat(60));
  
  const testMessages = [
    "Hello! How are you?",
    "Can you help me with a question?",
    "I just wanted to say something.",
  ];
  
  for (let i = 0; i < testMessages.length; i++) {
    console.log(`\n--- Test ${i + 1} ---`);
    console.log(`Input: "${testMessages[i]}"\n`);
    
    const result = await graph.invoke({
      messages: [new HumanMessage(testMessages[i])],
      category: "",
    });
    
    const response = result.messages[result.messages.length - 1];
    console.log(`Response: "${response.content}"`);
    console.log("-".repeat(60));
  }
  
  console.log("\n✅ Conditional edges example completed!");
}

runConditionalExample().catch(console.error);
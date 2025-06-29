package com.daebecodin.springaimcpragstudybudydemo;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class QuizController {

    private final ChatClient chatClient;

    // constructor injection of dependencies
    public QuizController(ChatClient.Builder builder, VectorStore vectorStore) {
        this.chatClient = builder // initializing the chat client with a fluent api pattern

                // grabs data from the vector store and adds to the model
                // context for an accurate response to the client
                .defaultAdvisors(new QuestionAnswerAdvisor(vectorStore))
                .build(); // build the chat client
    }


    @GetMapping("/quiz")
    public String quizMe(@RequestParam(defaultValue="Quiz me on how to create a spring web application") String query) {
        return chatClient.prompt()
                .user(query) // the user message is what the client inputs
                .call() // blocking call so the response is not streamed to a client
                .content(); // only returning string content of response
    }


}

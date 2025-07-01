package com.daebecodin.springaimcpragstudybudydemo;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.ParagraphPdfDocumentReader;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class QuizController {

    private final ChatClient chatClient;
    private final DocumentIngestion documentIngestion;

    // constructor injection of dependencies
    public QuizController(ChatClient chatClient, VectorStore vectorStore, DocumentIngestion documentIngestion) {
        this.chatClient = chatClient;
        this.documentIngestion = documentIngestion;

    }


    @GetMapping("/quiz")
    public String quizMe(@RequestParam(defaultValue="Generate a 10 question quiz on the course material on the documents provided to context") String query) {
        return chatClient.prompt()
                .user(query) // the user message is what the client inputs
//                .advisors(questionAnswerAdvisor)
                .call() // blocking call so the response is not streamed to a client
//                .entity(QuizQuestions.class); // return the responses back in json format
                .content(); // only returning string content of response
    }

    // PDF Document Reader
    @GetMapping("/linked-bag")
    public List<Document> linkedBagImplementations() {
        return documentIngestion.linkedBagImplementations();

    }


}

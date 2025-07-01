package com.daebecodin.springaimcpragstudybudydemo.quiz;

import com.daebecodin.springaimcpragstudybudydemo.document.DocumentIngestion;
import org.apache.tika.exception.TikaException;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@RestController
public class QuizController {

   private final ChatClient primaryChatClient;
   private final ChatClient openAiChatClient;
   private final ChatClient bedrockChatClient;
   private final DocumentIngestion documentIngestion;
   private final VectorStore vectorStore;
   private final QuestionAnswerAdvisor questionAnswerAdvisor;

    // constructor injection of dependencies
    public QuizController(ChatClient primaryChatClient, // Primary (Bedrock) client
                         @Qualifier("openai") ChatClient openAiChatClient,
                         @Qualifier("bedrock") ChatClient bedrockChatClient,
                          DocumentIngestion documentIngestion,
                          QuestionAnswerAdvisor questionAnswerAdvisor, // allows rag while model switching
                          VectorStore vectorStore
    ) {
        this.primaryChatClient = primaryChatClient;
        this.openAiChatClient = openAiChatClient;
        this.bedrockChatClient = bedrockChatClient;
        this.documentIngestion = documentIngestion;
        this.questionAnswerAdvisor = questionAnswerAdvisor;
        this.vectorStore = vectorStore;
    }


    @GetMapping("/quiz")
    public String quizMe(@RequestParam(defaultValue="quiz me on spring mvc") String query,
                         @RequestParam(defaultValue="bedrock") String model) { // pick a model
        
        ChatClient selectedClient = switch (model.toLowerCase()) {
            case "openai" -> openAiChatClient;
            case "bedrock" -> bedrockChatClient;
            default -> primaryChatClient;
        };
        
        return selectedClient.prompt()
                .user(query) // the user message is what the client inputs
                .advisors(questionAnswerAdvisor)
                .call() // blocking call so the response is not streamed to a client
//                .entity(QuizQuestions.class); // return the responses back in json format
                .content(); // only returning string content of response
    }

    // PDF Document Reader
    @GetMapping("/document")
    public List<Document> linkedBagImplementations() {
        return documentIngestion.linkedBagImplementations();

    }

    @PostMapping("/upload")
    public ResponseEntity<ExtractedDocument> uploadDocument(@RequestParam("file") MultipartFile file) {
        try {
            String extractedText = documentIngestion.extractTextFromDocument(file);
            ExtractedDocument extractedDocument = new ExtractedDocument(file.getOriginalFilename(), extractedText);
            return ResponseEntity.ok(extractedDocument);
        } catch (IOException | TikaException e ) {
            return ResponseEntity.internalServerError().build();
        }
    }

    private static class ExtractedDocument {
        private String fileName;
        private String content;

        public ExtractedDocument(String fileName, String content) {
            this.fileName = fileName;
            this.content = content;
        }

        public String getFileName() {
            return fileName;
        }

        public String getContent() {
            return content;
        }
    }

    // Debug endpoint to see what documents are retrieved for a query
    @GetMapping("/debug/search")
    public List<Document> debugSearch(
            @RequestParam String query,
            @RequestParam(defaultValue = "5") int topK) {

        List<Document> docs = Optional.ofNullable(
                vectorStore.similaritySearch(SearchRequest.builder()
                .query(query)
                .topK(topK)
                .build()))
                .orElse(Collections.emptyList());

        return docs;



    }

    // Debug endpoint to show retrieved context with similarity scores
    @GetMapping("/debug/context")
    public ResponseEntity<List<Document>> debugContext(
            @RequestParam String query,
            @RequestParam(defaultValue = "3") int topK) {
        List<Document> docs = Optional.ofNullable(vectorStore.similaritySearch(SearchRequest.builder()
                .query(query)
                .topK(topK)
                .build()))
                .orElse(Collections.emptyList());

        
        return ResponseEntity.ok(docs);
    }

    private static class DebugContext {
        private final String contentPreview;
        private final Object metadata;

        public DebugContext(String contentPreview, Object metadata) {
            this.contentPreview = contentPreview;
            this.metadata = metadata;
        }

        public String getContentPreview() {
            return contentPreview;
        }

        public Object getMetadata() {
            return metadata;
        }
    }

}



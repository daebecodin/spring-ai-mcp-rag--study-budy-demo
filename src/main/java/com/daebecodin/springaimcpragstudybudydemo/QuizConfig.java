package com.daebecodin.springaimcpragstudybudydemo;


import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.VectorStoreChatMemoryAdvisor;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class QuizConfig {

    /**
     * Providing a memory for the chat
     * @param vectorStore This is the class representation of the underlying vector database messages will be stored
     * @return
     */
    @Bean
    QuestionAnswerAdvisor questionAnswerAdvisor(VectorStore vectorStore) {
        return new QuestionAnswerAdvisor(vectorStore);
    }

    /**
     * Creating a bean for our Embedding Model
     * @param embeddingModel This is the bean initialization for our PgVectorStore
     * @return
     */
    @Bean
    VectorStore vectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel) {
        return PgVectorStore.builder(jdbcTemplate, embeddingModel).build();
    }
    @Bean
    ChatClient chatClient(ChatClient.Builder builder, // Configuration builder for the chat client
                          VectorStore vectorStore, // vector database representation for the chat
                          QuestionAnswerAdvisor qaAdvisor // chat memory
    ) {

        // default system prompt; possibly make a resource
        var system = """
                You are an ai powered assistant who primary job is to provide study help for students. You will not give answers to a students question;
                You will guidee the student to answers based on the course material in your context and general knowledge
                
                Do _not_ include any indication of what you're thinking. Nothing should be sent to the client between <thinking> tags.
                Just give the answer.
                
                When making quizzes, only quiz on the course concepts; ignore anything related to the document company or education company. purely on education things
                
                If you are asked what model you are you can answer
                """;

        return builder // configuration builder that the chat client instance was created with
                .defaultSystem(system) // system prompt
                .defaultAdvisors(VectorStoreChatMemoryAdvisor.builder(vectorStore).build(), qaAdvisor) // stores our chat into a vector database
                .build(); // building an immutable object
    }
}

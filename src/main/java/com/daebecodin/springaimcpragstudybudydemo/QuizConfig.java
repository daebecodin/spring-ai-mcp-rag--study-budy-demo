package com.daebecodin.springaimcpragstudybudydemo;


import org.springframework.ai.bedrock.converse.BedrockProxyChatModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.VectorStoreChatMemoryAdvisor;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class QuizConfig {

    /**
     * OpenAI ChatClient bean
     * @param openAiChatModel the model provided by OpenAI
     * @return a created bean of the model
     */
    @Bean
    @Qualifier("openai")
    public ChatClient openAiChatClient(@Qualifier("openAiChatModel") OpenAiChatModel openAiChatModel) {
        return ChatClient.create(openAiChatModel);
    }

    /**
     * Bedrock ChatClient bean
     * @param bedrockProxyChatModel amazon nova lite model
     * @return instantiated bean of the model
     */
    @Bean 
    @Qualifier("bedrock")
    public ChatClient bedrockChatClient(@Qualifier("bedrockProxyChatModel") BedrockProxyChatModel bedrockProxyChatModel) {
        return ChatClient.create(bedrockProxyChatModel);
    }

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
    public TikaDocumentReader tikaDocumentReader(@Value("classpath:/docs/")Resource defaultResource) {
        return new TikaDocumentReader(defaultResource);
    }
    
    /**
     * Primary ChatClient bean - uses Bedrock by default
     */
    @Bean
    @Primary
    ChatClient chatClient(@Qualifier("bedrockProxyChatModel") BedrockProxyChatModel bedrockProxyChatModel, // Use Bedrock as primary model
                          VectorStore vectorStore, // vector database representation for the chat
                          QuestionAnswerAdvisor qaAdvisor // chat memory
    ) {

        // default system prompt; possibly make a resource
        var system = """
                You are an ai powered assistant who primary job is to provide study help for students. You will not give answers to a students question;
                You will guide the student to answers based on the course material in your context and general knowledge
                
                Do _not_ include any indication of what you're thinking. Nothing should be sent to the client between <thinking> tags.
                Just give the answer.
                
                When making quizzes, only quiz on the course concepts; ignore anything related to the document company or education company.
                
                Create quizzes of 5 question; make sure they are about information from the provided context; do not provide the answers.
                Answers will be provided in the next request
                
                Format for Quiz
                
                Quiz
                1.Question
                a.
                b.
                c.
                d.
                
                2.Question
                a.
                b.
                c.
                d.
                
                3.Question
                a.
                b.
                c.
                d.
                
                4.Question
                a.
                b.
                c.
                d.
                
                5.Question
                a.
                b.
                c.
                d.
                
                
                Here is a general format
                
                """;

        return ChatClient.builder(bedrockProxyChatModel) // Use specific model instead of generic builder
                .defaultSystem(system) // system prompt
                .defaultAdvisors(VectorStoreChatMemoryAdvisor.builder(vectorStore).build(), qaAdvisor) // stores our chat into a vector database
                .build(); // building an immutable object
    }
}

package com.daebecodin.springaimcpragstudybudydemo.data;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Order(1) // Ensure this runs before DocumentIngestion
public class DatabaseInitializer {
    
    private static final Logger logger = LoggerFactory.getLogger(DatabaseInitializer.class);
    
    private final JdbcTemplate jdbcTemplate;
    
    public DatabaseInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }
    
    @PostConstruct
    public void initializeDatabase() {
        try {
            // Check if the processed_documents table exists
            String checkTableQuery = """
                SELECT EXISTS (
                    SELECT FROM information_schema.tables 
                    WHERE table_schema = 'public' 
                    AND table_name = 'processed_documents'
                );
                """;
            
            Boolean tableExists = jdbcTemplate.queryForObject(checkTableQuery, Boolean.class);
            
            if (Boolean.FALSE.equals(tableExists)) {
                logger.info("Creating processed_documents table...");
                
                String createTableQuery = """
                    CREATE TABLE processed_documents (
                        id BIGSERIAL PRIMARY KEY,
                        filename VARCHAR(255) UNIQUE NOT NULL,
                        file_size BIGINT,
                        processed_at TIMESTAMP,
                        chunk_count INTEGER
                    );
                    """;
                
                jdbcTemplate.execute(createTableQuery);
                logger.info("Successfully created processed_documents table");
            } else {
                logger.info("processed_documents table already exists");
            }
            
        } catch (Exception e) {
            logger.error("Error initializing database: {}", e.getMessage(), e);
            // Don't throw the exception to allow the application to continue
            // The JPA auto-creation will handle it as fallback
        }
    }
}

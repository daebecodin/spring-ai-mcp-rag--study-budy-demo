package com.daebecodin.springaimcpragstudybudydemo.data;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Order(1) // Ensure this run before DocumentIngestion
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
            // this query will be passed in for checking
            String checkTableQuery = """
                SELECT EXISTS (
                    SELECT FROM information_schema.tables
                    WHERE table_schema = 'public'
                    AND table_name = 'processed_documents'
                );
                """;

            /** Jdbc queryForObject()
             * the queryForObject method accepts the query for the object and the return type of statement;
             * in our case we make a query for the table, pass the query and pass the Boolean class; we want to return a boolean result
             * we want to compare the query for a table passed in with a possible existing table in the database
             */
            Boolean tableExists = jdbcTemplate.queryForObject(checkTableQuery, Boolean.class);

            // if no table when we create one
            if (Boolean.FALSE.equals(tableExists)) {
                logger.info("Creating processed_documents table...");

                // creating a query
                String createTableQuery = """
                    CREATE TABLE processed_documents (
                        id BIGSERIAL PRIMARY KEY,
                        filename VARCHAR(255) UNIQUE NOT NULL,
                        file_size BIGINT,
                        processed_at TIMESTAMP,
                        chunk_count INTEGER
                    );
                    """;

                /** Jdbc execute()
                 * the execute method accepts a statement; in our case a query;
                 * that statement is then processed and initialized
                 * in order to access what is created, we must use the method provided by the Statement interface
                 * */
                jdbcTemplate.execute(createTableQuery);
                logger.info("Successfully created processed_documents table");
            } else {
                logger.info("processed_documents table already exists");
            }
            
        } catch (Exception e) {
            logger.error("Error initializing database: {}", e.getMessage(), e);
            // Not throwing the exception to allow the application to continue
            // The JPA auto-creation will handle it as fallback
        }
    }
}

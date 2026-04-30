package com.recipekg.planner.config;

import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.http.HTTPRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GraphDbConfig {

    @Value("${graphdb.url}")
    private String graphDbUrl;

    @Bean
    public Repository rdf4jRepository() {
        HTTPRepository repo = new HTTPRepository(graphDbUrl);
        repo.init();
        return repo;
    }
}
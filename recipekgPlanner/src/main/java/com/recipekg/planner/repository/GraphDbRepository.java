package com.recipekg.planner.repository;

import com.recipekg.planner.model.RecipeCandidate;
import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.query.TupleQuery;
import org.eclipse.rdf4j.query.TupleQueryResult;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
public class GraphDbRepository {

    private final Repository db;


    public GraphDbRepository(Repository db) {
        this.db = db;
    }

    public List<RecipeCandidate> executeSparql(String sparqlQuery) {
        List<RecipeCandidate> candidates = new ArrayList<>();

        try (RepositoryConnection conn = db.getConnection()) {

            TupleQuery query = conn.prepareTupleQuery(sparqlQuery);

            try (TupleQueryResult result = query.evaluate()) {
                while (result.hasNext()) {
                    BindingSet row = result.next();

                    String uri = row.getValue("recipe").stringValue();
                    String label = row.getValue("recipeLabel").stringValue();


                    List<String> usdaIds = new ArrayList<>();
                    if (row.hasBinding("usdaIds")) {
                        String idString = row.getValue("usdaIds").stringValue();


                        if (idString != null && !idString.trim().isEmpty()) {

                            usdaIds = Arrays.asList(idString.split(","));
                        }
                    }


                    candidates.add(new RecipeCandidate(uri, label, usdaIds));
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to execute SPARQL query via RDF4J", e);
        }

        return candidates;
    }
}
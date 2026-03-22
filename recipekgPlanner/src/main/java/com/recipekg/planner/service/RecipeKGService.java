package com.recipekg.planner.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

@Service
@RequiredArgsConstructor
public class RecipeKGService {

    private final WebClient webClient;

    public String fetchMealsByGoal(String goal) {
        String sparql = """
PREFIX heals: <http://idea.rpi.edu/heals/kb/>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>

SELECT DISTINCT ?meal ?label ?qty ?unit
WHERE {
  ?meal a heals:recipe .
  ?meal heals:uses ?use .

  OPTIONAL {
     ?use heals:ing_name ?name .
     ?name rdfs:label ?label .
  }

  OPTIONAL { ?use heals:ing_quantity ?qty }
  OPTIONAL { ?use heals:ing_unit ?unit }

}
LIMIT 30
        """.formatted(goal);

//        FILTER(!CONTAINS(LCASE(STR(?label)), "peanut"))
        String result = webClient.post()
                .uri("http://localhost:7200/repositories/recipekg")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/sparql-results+json")
                .body(BodyInserters.fromFormData("query", sparql))
                .retrieve()
                .bodyToMono(String.class)
                .block();

        System.out.println("KG RESULT = " + result);

        return result;
    }
}

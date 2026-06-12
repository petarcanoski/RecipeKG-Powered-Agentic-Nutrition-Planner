package com.recipekg.planner.service.agents;

import com.recipekg.planner.model.UserProfile;
import com.recipekg.planner.model.WeeklyFeedback;
import com.recipekg.planner.service.ai.NvidiaChatClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProgressAgentService {

    private final NvidiaChatClient nvidiaChatClient;

    public String generateNextWeekPlan(
            UserProfile profile,
            String previousPlanJson,
            WeeklyFeedback feedback
    ) {

        String prompt = """
You are an adaptive performance planner.

User finished previous week.

You must create improved 7-day plan.

Consider:
- adherence level
- weight progress
- sickness
- previous structure
- maintain safety

Return STRICT JSON ONLY

FORMAT:

{
 "weekPlan":[
   {
     "day":"",
     "breakfast":"",
     "lunch":"",
     "dinner":"",
     "workout":"",
     "notes":""
   }
 ]
}

PROFILE:
Age: %d
Weight: %.1f
Goal: %s
Diseases: %s
Allergies: %s
Activity: %s

FEEDBACK:
Adherence: %d
WeightChange: %.2f
Sickness: %s
Notes: %s

PREVIOUS PLAN:
%s
""".formatted(
                profile.getAge(),
                profile.getWeight(),
                profile.getGoal(),
                profile.getDiseases(),
                profile.getAllergies(),
                profile.getActivityLevel(),
                feedback.getAdherenceScore(),
                feedback.getWeightChange(),
                feedback.getSickness(),
                feedback.getNotes(),
                previousPlanJson
        );

        return nvidiaChatClient.complete(prompt);
    }
}

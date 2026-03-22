package com.recipekg.planner.service;

import org.springframework.stereotype.Service;

@Service
public class ProgressBrainService {

    public int adjustCalories(
            String goal,
            double currentWeight,
            double previousWeight,
            int currentTarget
    ) {

        double diff = currentWeight - previousWeight;

        if (goal.equalsIgnoreCase("BULK")) {

            if (diff < 0.25) return currentTarget + 200;
            if (diff > 0.75) return currentTarget - 150;
        }

        if (goal.equalsIgnoreCase("CUT")) {

            if (diff > -0.3) return currentTarget - 200;
            if (diff < -1.0) return currentTarget + 150;
        }

        if (goal.equalsIgnoreCase("MAINTAIN")) {

            if (Math.abs(diff) > 0.4) return currentTarget - (int)(diff * 200);
        }

        return currentTarget;
    }
}

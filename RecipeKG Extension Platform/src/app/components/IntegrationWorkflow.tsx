import { useMemo, useState } from "react";
import { ChevronLeft, ChevronRight } from "lucide-react";
import { Button } from "./ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "./ui/card";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "./ui/table";

type Meal = {
  name: string;
  image: string;
};

type DayPlan = {
  dayLabel: string;
  breakfast: Meal;
  snackOne: Meal;
  lunch: Meal;
  snackTwo: Meal;
  dinner: Meal;
  workout: string;
};

type WeekPlan = {
  title: string;
  days: DayPlan[];
};

const DAY_NAMES = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"];
const BASE_WEEK_START = new Date(2026, 2, 16);
const MS_PER_DAY = 24 * 60 * 60 * 1000;

const breakfastMeals: Meal[] = [
  { name: "Greek Yogurt and Berries", image: "https://picsum.photos/seed/breakfast-yogurt/160/100" },
  { name: "Avocado Toast", image: "https://picsum.photos/seed/breakfast-avocado/160/100" },
  { name: "Banana Oat Pancakes", image: "https://picsum.photos/seed/breakfast-pancakes/160/100" },
  { name: "Spinach Omelette", image: "https://picsum.photos/seed/breakfast-omelette/160/100" },
  { name: "Chia Pudding", image: "https://picsum.photos/seed/breakfast-chia/160/100" },
  { name: "Berry Smoothie Bowl", image: "https://picsum.photos/seed/breakfast-smoothie/160/100" },
  { name: "Whole Grain Cereal", image: "https://picsum.photos/seed/breakfast-cereal/160/100" },
];

const snackOneMeals: Meal[] = [
  { name: "Apple Slices and Peanut Butter", image: "https://picsum.photos/seed/snack-apple/160/100" },
  { name: "Trail Mix", image: "https://picsum.photos/seed/snack-trailmix/160/100" },
  { name: "Cucumber and Hummus", image: "https://picsum.photos/seed/snack-hummus/160/100" },
  { name: "Protein Bar", image: "https://picsum.photos/seed/snack-proteinbar/160/100" },
  { name: "Cottage Cheese Cup", image: "https://picsum.photos/seed/snack-cottage/160/100" },
  { name: "Orange and Almonds", image: "https://picsum.photos/seed/snack-orange/160/100" },
  { name: "Rice Cakes", image: "https://picsum.photos/seed/snack-ricecakes/160/100" },
];

const lunchMeals: Meal[] = [
  { name: "Chicken Quinoa Bowl", image: "https://picsum.photos/seed/lunch-quinoa/160/100" },
  { name: "Salmon and Veggie Wrap", image: "https://picsum.photos/seed/lunch-wrap/160/100" },
  { name: "Lentil Soup", image: "https://picsum.photos/seed/lunch-lentils/160/100" },
  { name: "Turkey Sandwich", image: "https://picsum.photos/seed/lunch-sandwich/160/100" },
  { name: "Tofu Stir Fry", image: "https://picsum.photos/seed/lunch-tofu/160/100" },
  { name: "Veggie Pasta", image: "https://picsum.photos/seed/lunch-pasta/160/100" },
  { name: "Bean Burrito Bowl", image: "https://picsum.photos/seed/lunch-burrito/160/100" },
];

const snackTwoMeals: Meal[] = [
  { name: "Carrot Sticks and Dip", image: "https://picsum.photos/seed/snack-carrot/160/100" },
  { name: "Greek Yogurt Cup", image: "https://picsum.photos/seed/snack-yogurt/160/100" },
  { name: "Fruit Salad", image: "https://picsum.photos/seed/snack-fruit/160/100" },
  { name: "Boiled Eggs", image: "https://picsum.photos/seed/snack-eggs/160/100" },
  { name: "Cheese and Crackers", image: "https://picsum.photos/seed/snack-cheese/160/100" },
  { name: "Roasted Chickpeas", image: "https://picsum.photos/seed/snack-chickpeas/160/100" },
  { name: "Banana and Walnut", image: "https://picsum.photos/seed/snack-banana/160/100" },
];

const dinnerMeals: Meal[] = [
  { name: "Baked Salmon and Greens", image: "https://picsum.photos/seed/dinner-salmon/160/100" },
  { name: "Grilled Chicken and Rice", image: "https://picsum.photos/seed/dinner-chicken/160/100" },
  { name: "Veggie Curry", image: "https://picsum.photos/seed/dinner-curry/160/100" },
  { name: "Shrimp Stir Fry", image: "https://picsum.photos/seed/dinner-shrimp/160/100" },
  { name: "Stuffed Peppers", image: "https://picsum.photos/seed/dinner-peppers/160/100" },
  { name: "Mushroom Risotto", image: "https://picsum.photos/seed/dinner-risotto/160/100" },
  { name: "Turkey Meatballs", image: "https://picsum.photos/seed/dinner-meatballs/160/100" },
];

const workoutPlans = [
  "30 min brisk walk + 10 min stretch",
  "Upper body strength (40 min)",
  "Yoga flow and mobility (35 min)",
  "Lower body strength (40 min)",
  "Cycling or cardio intervals (30 min)",
  "Core and balance training (25 min)",
  "Active recovery: light walk and stretch",
];

function formatDateLabel(date: Date) {
  const dayNumber = date.getDate();
  const monthNumber = date.getMonth() + 1;
  const day = dayNumber < 10 ? `0${dayNumber}` : String(dayNumber);
  const month = monthNumber < 10 ? `0${monthNumber}` : String(monthNumber);
  const year = date.getFullYear();
  return `${day}.${month}.${year}`;
}

function addDays(date: Date, days: number) {
  return new Date(date.getTime() + days * MS_PER_DAY);
}

function mealByOffset(options: Meal[], offset: number) {
  const index = ((offset % options.length) + options.length) % options.length;
  return options[index];
}

function textByOffset(options: string[], offset: number) {
  const index = ((offset % options.length) + options.length) % options.length;
  return options[index];
}

function buildWeekPlan(weekIndex: number): WeekPlan {
  const weekStart = addDays(BASE_WEEK_START, weekIndex * 7);
  const weekEnd = addDays(weekStart, 6);
  const title = `7-Day Meal & Workout Plan ${formatDateLabel(weekStart)} - ${formatDateLabel(weekEnd)}`;

  const days = DAY_NAMES.map((dayName, dayIndex) => {
    const mealOffset = weekIndex * 7 + dayIndex;
    return {
      dayLabel: dayName,
      breakfast: mealByOffset(breakfastMeals, mealOffset),
      snackOne: mealByOffset(snackOneMeals, mealOffset + 1),
      lunch: mealByOffset(lunchMeals, mealOffset + 2),
      snackTwo: mealByOffset(snackTwoMeals, mealOffset + 3),
      dinner: mealByOffset(dinnerMeals, mealOffset + 4),
      workout: textByOffset(workoutPlans, mealOffset),
    };
  });

  return {
    title,
    days,
  };
}

function MealCell({ meal }: { meal: Meal }) {
  return (
    <div className="py-1">
      <p className="text-sm font-medium text-gray-700 whitespace-normal">{meal.name}</p>
    </div>
  );
}

export function IntegrationWorkflow() {
  const [weekCursor, setWeekCursor] = useState(0);

  const currentWeek = useMemo(() => {
    return buildWeekPlan(weekCursor);
  }, [weekCursor]);

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h2 className="text-2xl font-semibold text-gray-900">Integration Meal & Workout Planner</h2>
          <p className="mt-1 text-gray-600">
            Weekly meal plan preview with breakfast, snacks, lunch, and dinner suggestions.
          </p>
        </div>
      </div>

      <Card key={currentWeek.title}>
        <CardHeader>
          <CardTitle>{currentWeek.title}</CardTitle>
          <CardDescription>Balanced meal ideas for every day in the selected week.</CardDescription>
        </CardHeader>
        <CardContent>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead className="w-[84px]">Day</TableHead>
                <TableHead>Breakfast</TableHead>
                <TableHead>Snack</TableHead>
                <TableHead>Lunch</TableHead>
                <TableHead>Snack</TableHead>
                <TableHead>Dinner</TableHead>
                <TableHead>Workout</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {currentWeek.days.map((day) => (
                <TableRow key={day.dayLabel}>
                  <TableCell className="align-top font-semibold text-gray-900">{day.dayLabel}</TableCell>
                  <TableCell className="align-top whitespace-normal"><MealCell meal={day.breakfast} /></TableCell>
                  <TableCell className="align-top whitespace-normal"><MealCell meal={day.snackOne} /></TableCell>
                  <TableCell className="align-top whitespace-normal"><MealCell meal={day.lunch} /></TableCell>
                  <TableCell className="align-top whitespace-normal"><MealCell meal={day.snackTwo} /></TableCell>
                  <TableCell className="align-top whitespace-normal"><MealCell meal={day.dinner} /></TableCell>
                  <TableCell className="align-top whitespace-normal text-sm text-gray-700">{day.workout}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </CardContent>
      </Card>

      <div className="flex flex-wrap items-center justify-center gap-3 pt-2">
        <Button variant="outline" onClick={() => setWeekCursor(weekCursor - 1)}>
          <ChevronLeft className="mr-2 h-4 w-4" />
          Previous Week
        </Button>
        <Button onClick={() => setWeekCursor(weekCursor + 1)}>
          Next Week
          <ChevronRight className="ml-2 h-4 w-4" />
        </Button>
      </div>
    </div>
  );
}

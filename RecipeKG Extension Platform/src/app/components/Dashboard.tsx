import { useMemo, useState } from "react";
import {
  BrainCircuit,
  CalendarDays,
  ChevronDown,
  Loader2,
  Settings,
  Sparkles,
  UserRound,
} from "lucide-react";
import { Link } from "react-router";
import { useAuth } from "../auth/AuthContext";
import { Button } from "./ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "./ui/card";
import { Textarea } from "./ui/textarea";

type ProfileParameter = {
  label: string;
  value: string;
  wide?: boolean;
};

type PanelState = {
  status: "idle" | "loading" | "sent";
  message: string;
};

type MacroSummary = {
  calories: number;
  protein: number;
  carbs: number;
  fat: number;
  sugar: number;
  sodium: number;
};

type MealPlan = {
  slot: string;
  recipeName: string;
  ingredients: string[];
  servings: number;
  totalMacros: MacroSummary;
  reason: string;
};

type DailyPlan = {
  day: number;
  meals: MealPlan[];
  totalMacros: MacroSummary;
  rationale: string;
};

type NutritionPlanResponse = {
  goalStatus: string;
  summary: string;
  days: DailyPlan[];
  weeklyTotals: MacroSummary;
};

const placeholderProfile: ProfileParameter[] = [
  { label: "Name", value: "Not loaded" },
  { label: "Surname", value: "Not loaded" },
  { label: "Age", value: "Not loaded" },
  { label: "Gender", value: "Not loaded" },
  { label: "Height", value: "Not loaded" },
  { label: "Weight", value: "Not loaded" },
  { label: "Blood type", value: "Not loaded" },
  { label: "Activity level", value: "Not loaded" },
  { label: "Goal", value: "Not loaded" },
  { label: "Allergies", value: "None", wide: true },
  { label: "Diseases", value: "None", wide: true },
];

const accountProfileKeys: Partial<Record<string, string>> = {
  Name: "name",
  Surname: "surname",
  Age: "age",
  Gender: "gender",
  Height: "height",
  Weight: "weight",
  "Blood type": "bloodType",
  "Activity level": "activityLevel",
  Goal: "goal",
  Allergies: "allergies",
  Diseases: "diseases",
};

const testNutritionPlanEndpoint = "http://localhost:8080/test-nutrition-plan";

const recipeKgLoadingMessages = [
  "Gathering user profile information",
  "Checking allergies and medical constraints",
  "Querying RecipeKG candidates",
  "Estimating servings",
  "Calculating meal macros",
  "Balancing daily totals",
  "Composing the weekly nutrition plan",
  "Preparing the result",
];

function buildGeminiPrompt(parameters: ProfileParameter[]) {
  const profileLines = parameters
    .map((parameter) => `${parameter.label}: ${parameter.value}`)
    .join("\n");

  return `Generate a complete 7-day nutrition plan using these user parameters:

${profileLines}

Return days, meals, ingredients, servings, per-meal macros, daily macro totals, weekly macro totals, and a brief rationale for each day.`;
}

function PromptPanel({
  title,
  description,
  body,
  result,
  buttonLabel,
  buttonIcon: ButtonIcon,
  state,
  onSend,
}: {
  title: string;
  description: string;
  body: React.ReactNode;
  result: React.ReactNode;
  buttonLabel: string;
  buttonIcon: typeof BrainCircuit;
  state: PanelState;
  onSend: () => void;
}) {
  const isLoading = state.status === "loading";

  return (
    <Card className="flex min-h-[330px] flex-col">
      <CardHeader className="border-b pb-4">
        <div className="flex items-start gap-3">
          <div>
            <CardTitle className="text-lg">{title}</CardTitle>
            <CardDescription>{description}</CardDescription>
          </div>
        </div>
      </CardHeader>

      <CardContent className="flex flex-1 flex-col gap-4 p-5">
        <div className="flex-1 rounded-md border bg-gray-50 p-4 text-sm leading-6 text-gray-700">
          {body}
        </div>

        {state.message && (
          <div className="rounded-md border border-blue-100 bg-blue-50 px-3 py-2 text-sm text-blue-800">
            {state.message}
          </div>
        )}

        <Button
          onClick={onSend}
          disabled={isLoading}
          className="ml-auto w-full sm:w-auto"
        >
          {isLoading ? (
            <Loader2 className="h-4 w-4 animate-spin" />
          ) : (
            <ButtonIcon className="h-4 w-4" />
          )}
          {buttonLabel}
        </Button>

        {result}
      </CardContent>
    </Card>
  );
}

function MacroGrid({ macros }: { macros: MacroSummary }) {
  const values = [
    { label: "Calories", value: macros.calories },
    { label: "Protein", value: `${macros.protein} g` },
    { label: "Carbs", value: `${macros.carbs} g` },
    { label: "Fat", value: `${macros.fat} g` },
    { label: "Sugar", value: `${macros.sugar} g` },
    { label: "Sodium", value: `${macros.sodium} mg` },
  ];

  return (
    <div className="grid gap-2 sm:grid-cols-2 xl:grid-cols-3">
      {values.map((item) => (
        <div key={item.label} className="rounded-md border bg-white px-3 py-2">
          <p className="text-xs font-medium uppercase tracking-wide text-gray-500">
            {item.label}
          </p>
          <p className="mt-1 text-sm font-semibold text-gray-900">
            {item.value}
          </p>
        </div>
      ))}
    </div>
  );
}

function GeneratedPlan({ plan }: { plan: NutritionPlanResponse }) {
  const [openDay, setOpenDay] = useState<number>(plan.days[0]?.day ?? 1);

  return (
    <div className="overflow-hidden rounded-md border bg-white">
      <div className="border-b bg-gray-50 p-4">
        <div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
          <div>
            <div className="flex items-center gap-2">
              <CalendarDays className="h-5 w-5 text-blue-600" />
              <h3 className="text-lg font-semibold text-gray-900">
                Generated RecipeKG plan
              </h3>
            </div>
            <p className="mt-1 text-sm text-gray-600">{plan.summary}</p>
          </div>
          <div className="rounded-md border bg-gray-50 px-3 py-2 text-sm font-medium text-gray-700">
            Status: {plan.goalStatus}
          </div>
        </div>
      </div>

      <div className="space-y-5 p-4">
        <div className="space-y-2">
          <h3 className="text-sm font-semibold text-gray-900">Weekly totals</h3>
          <MacroGrid macros={plan.weeklyTotals} />
        </div>

        <div className="space-y-3">
          {plan.days.map((day) => {
            const isOpen = openDay === day.day;

            return (
              <div key={day.day} className="overflow-hidden rounded-md border bg-white">
                <button
                  type="button"
                  onClick={() => setOpenDay(isOpen ? 0 : day.day)}
                  className="flex w-full items-center justify-between gap-3 border-b bg-gray-50 px-4 py-3 text-left"
                >
                  <div>
                    <p className="font-semibold text-gray-900">Day {day.day}</p>
                    <p className="text-sm text-gray-600">{day.rationale}</p>
                  </div>
                  <ChevronDown
                    className={`h-4 w-4 shrink-0 text-gray-500 transition-transform ${
                      isOpen ? "rotate-180" : ""
                    }`}
                  />
                </button>

                {isOpen && (
                  <div className="space-y-4 p-4">
                    <MacroGrid macros={day.totalMacros} />

                    <div className="grid gap-4">
                      {day.meals.map((meal) => (
                        <div key={`${day.day}-${meal.slot}`} className="rounded-md border bg-gray-50 p-4">
                          <div className="flex items-start justify-between gap-3">
                            <div>
                              <p className="text-xs font-medium uppercase tracking-wide text-gray-500">
                                {meal.slot}
                              </p>
                              <h4 className="mt-1 font-semibold text-gray-900">
                                {meal.recipeName}
                              </h4>
                            </div>
                            <span className="rounded-md border bg-white px-2 py-1 text-xs font-medium text-gray-700">
                              {meal.servings} serving
                            </span>
                          </div>

                          <p className="mt-3 text-sm text-gray-600">{meal.reason}</p>

                          <div className="mt-4">
                            <p className="text-sm font-medium text-gray-900">Ingredients</p>
                            <ul className="mt-2 space-y-1 text-sm text-gray-600">
                              {meal.ingredients.map((ingredient) => (
                                <li key={ingredient}>{ingredient}</li>
                              ))}
                            </ul>
                          </div>

                          <div className="mt-4 grid grid-cols-2 gap-2 text-sm">
                            <div className="rounded-md bg-white px-2 py-1">
                              {meal.totalMacros.calories} kcal
                            </div>
                            <div className="rounded-md bg-white px-2 py-1">
                              {meal.totalMacros.protein} g protein
                            </div>
                            <div className="rounded-md bg-white px-2 py-1">
                              {meal.totalMacros.carbs} g carbs
                            </div>
                            <div className="rounded-md bg-white px-2 py-1">
                              {meal.totalMacros.fat} g fat
                            </div>
                          </div>
                        </div>
                      ))}
                    </div>
                  </div>
                )}
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
}

function RecipeKgLoading({ message }: { message: string }) {
  return (
    <div className="flex flex-col items-center gap-4 rounded-md border bg-blue-50 p-6 text-center">
      <div className="relative flex h-16 w-16 items-center justify-center rounded-full border bg-white">
        <div className="absolute h-16 w-16 animate-ping rounded-full bg-blue-100" />
        <Loader2 className="relative h-7 w-7 animate-spin text-blue-600" />
      </div>
      <div className="space-y-1 transition-all duration-300">
        <p className="text-lg font-semibold text-gray-900">
          Generating RecipeKG plan
        </p>
        <p className="text-sm text-gray-600">{message}</p>
      </div>
    </div>
  );
}

function GeminiPlaceholderResult({ message }: { message: string }) {
  if (!message) {
    return null;
  }

  return (
    <div className="rounded-md border bg-gray-50 p-4">
      <p className="text-sm font-semibold text-gray-900">Gemini result</p>
      <p className="mt-1 text-sm text-gray-600">{message}</p>
    </div>
  );
}

export function Dashboard() {
  const { profile, isProfileLoading } = useAuth();
  const [generatedPlan, setGeneratedPlan] = useState<NutritionPlanResponse | null>(null);
  const [recipeKgState, setRecipeKgState] = useState<PanelState>({
    status: "idle",
    message: "",
  });
  const [geminiState, setGeminiState] = useState<PanelState>({
    status: "idle",
    message: "",
  });

  const profileParameters = useMemo(() => {
    return placeholderProfile.map((parameter) => {
      const accountKey = accountProfileKeys[parameter.label] as
        | keyof typeof profile
        | undefined;
      const accountValue = accountKey ? profile?.[accountKey] : undefined;

      return {
        ...parameter,
        value: Array.isArray(accountValue)
          ? accountValue.length > 0
            ? accountValue.join(", ")
            : "None"
          : typeof accountValue === "string" || typeof accountValue === "number"
            ? String(accountValue)
            : isProfileLoading
              ? "Loading"
              : parameter.value,
      };
    });
  }, [profile, isProfileLoading]);

  const geminiPrompt = useMemo(
    () => buildGeminiPrompt(profileParameters),
    [profileParameters],
  );
  const shouldMatchPanelHeights =
    recipeKgState.status === "idle" && geminiState.status === "idle" && !generatedPlan;

  async function generateRecipeKgPlan() {
    setGeneratedPlan(null);
    setRecipeKgState({
      status: "loading",
      message: recipeKgLoadingMessages[0],
    });

    let messageIndex = 0;
    const intervalId = window.setInterval(() => {
      messageIndex = (messageIndex + 1) % recipeKgLoadingMessages.length;
      setRecipeKgState({
        status: "loading",
        message: recipeKgLoadingMessages[messageIndex],
      });
    }, 900);

    try {
      const response = await fetch(testNutritionPlanEndpoint);

      if (!response.ok) {
        throw new Error("Request failed");
      }

      const plan = (await response.json()) as NutritionPlanResponse;
      window.clearInterval(intervalId);

      setRecipeKgState({
        status: "sent",
        message: "RecipeKG plan generated successfully.",
      });
      setGeneratedPlan(plan);
    } catch {
      window.clearInterval(intervalId);
      setRecipeKgState({
        status: "sent",
        message: "Unable to generate the RecipeKG test plan. Make sure the backend is running on port 8080.",
      });
    }
  }

  function sendGeminiPlaceholder() {
    const doneMessage = "Placeholder request prepared for the Gemini comparison controller.";

    setGeminiState({
      status: "loading",
      message: "Preparing request payload from the user parameters.",
    });

    window.setTimeout(() => {
      setGeminiState({
        status: "sent",
        message: doneMessage,
      });
    }, 500);
  }

  return (
    <div className="mx-auto max-w-6xl space-y-8">
      <Card className="mx-auto w-full max-w-xl">
        <CardHeader className="border-b pb-4">
          <div className="flex items-center gap-3">
            <div className="rounded-md border bg-white p-2 text-gray-700">
              <UserRound className="h-5 w-5" />
            </div>
            <div className="min-w-0 flex-1">
              <CardTitle className="text-lg">User parameters</CardTitle>
              <CardDescription>
                {isProfileLoading
                  ? "Loading profile values from registration"
                  : "Profile values from registration"}
              </CardDescription>
            </div>
            <Button asChild variant="outline" size="sm" className="shrink-0">
              <Link to="/profile">
                <Settings className="h-4 w-4" />
                Edit profile
              </Link>
            </Button>
          </div>
        </CardHeader>
        <CardContent className="grid gap-3 p-5 sm:grid-cols-2">
          {profileParameters.map((parameter) => (
            <div
              key={parameter.label}
              className={`rounded-md border bg-gray-50 px-3 py-2 ${
                parameter.wide ? "sm:col-span-2" : ""
              }`}
            >
              <p className="text-xs font-medium uppercase tracking-wide text-gray-500">
                {parameter.label}
              </p>
              <p className="mt-1 break-words text-sm font-semibold text-gray-900">
                {parameter.value}
              </p>
            </div>
          ))}
        </CardContent>
      </Card>

      <div
        className={`grid gap-6 lg:grid-cols-2 ${
          shouldMatchPanelHeights ? "items-stretch" : "items-start"
        }`}
      >
        <PromptPanel
          title="RecipeKG agent"
          description="GraphDB-backed multi-agent planner"
          buttonLabel="Generate with RecipeKG"
          buttonIcon={BrainCircuit}
          state={recipeKgState}
          onSend={generateRecipeKgPlan}
          body={
            <div className="space-y-4">
              <p>
                Our services gather your information from the parameters above
                and build the perfect weekly nutrition plan through the RecipeKG
                multi-agent pipeline.
              </p>
              <p>
                The orchestration starts with a medical agent that interprets
                allergies, diseases, blood type, goal, and activity level as
                hard constraints. A food scientist agent then queries our
                GraphDB recipe knowledge graph for safe candidate meals. The
                nutrition planner estimates servings, balances macros across
                the week, and composes a structured plan with meals,
                ingredients, rationale, and nutrition totals.
              </p>
              <p>
                This makes the RecipeKG run traceable: every generated plan is
                based on profile constraints, graph-backed recipe candidates,
                and macro calculations instead of a direct free-form prompt.
              </p>
            </div>
          }
          result={
            recipeKgState.status === "loading" ? (
              <RecipeKgLoading message={recipeKgState.message} />
            ) : generatedPlan ? (
              <GeneratedPlan plan={generatedPlan} />
            ) : null
          }
        />

        <PromptPanel
          title="Google Gemini"
          description="Direct baseline through future controller"
          buttonLabel="Generate with Google Gemini"
          buttonIcon={Sparkles}
          state={geminiState}
          onSend={sendGeminiPlaceholder}
          body={
            <div className="space-y-3">
              <p>Pregenerated prompt based on your parameters:</p>
              <Textarea
                value={geminiPrompt}
                readOnly
                className="min-h-[150px] resize-none bg-white text-sm leading-6"
              />
            </div>
          }
          result={<GeminiPlaceholderResult message={geminiState.message} />}
        />
      </div>
    </div>
  );
}

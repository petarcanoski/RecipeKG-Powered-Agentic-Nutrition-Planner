import { useEffect, useMemo, useRef, useState } from "react";
import {
  BrainCircuit,
  CalendarDays,
  ChevronDown,
  Loader2,
  Settings,
  Sparkles,
  Trophy,
  UserRound,
} from "lucide-react";
import { Link } from "react-router";
import { useAuth } from "../auth/AuthContext";
import { UserProfile } from "../auth/types";
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

type NutritionPlanJobStatus = "PENDING" | "RUNNING" | "COMPLETED" | "FAILED";

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

type NutritionPlanJobResponse = {
  jobId: string;
  userId: number;
  status: NutritionPlanJobStatus;
  message: string;
  createdAt: string;
  updatedAt: string;
  nutritionPlan: NutritionPlanResponse | null;
};

type CurrentNutritionPlanResponse = {
  status: NutritionPlanJobStatus | "NOT_FOUND";
  message: string;
  jobId: string | null;
  nutritionPlan: NutritionPlanResponse | null;
};

type VoteWinner = "RECIPE_KG" | "GEMINI" | "TIE";

type PlanComparisonScore = {
  recipeKgWins: number;
  geminiWins: number;
  ties: number;
  totalVotes: number;
};

type PlanComparisonVoteResponse = {
  id: number;
  winner: VoteWinner;
  reason: string | null;
  message: string;
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

const recipeKgLoadingMessageIntervalMs = 6000;
const recipeKgPollingIntervalMs = 4000;

function safeProfileValue(value: unknown) {
  if (Array.isArray(value)) {
    return value.length > 0 ? `[${value.join(", ")}]` : "[]";
  }

  if (value === null || value === undefined || value === "") {
    return "N/A";
  }

  return String(value);
}

function buildGeminiPrompt(profile: UserProfile | null) {
  return `You are a nutrition planning assistant.

Create a practical 7-day nutrition plan directly from the user's profile.
Use normal meal names and ingredient lists that a user could understand.

Return strict JSON only. The JSON must match this exact shape:
{
  "goalStatus": "string",
  "summary": "string",
  "days": [
    {
      "day": 1,
      "meals": [
        {
          "slot": "breakfast|lunch|dinner|snack|dessert",
          "recipeName": "string",
          "ingredients": ["string"],
          "servings": 1.0,
          "totalMacros": {
            "calories": 0,
            "protein": 0,
            "carbs": 0,
            "fat": 0,
            "sugar": 0,
            "sodium": 0
          },
          "reason": "string"
        }
      ],
      "totalMacros": {
        "calories": 0,
        "protein": 0,
        "carbs": 0,
        "fat": 0,
        "sugar": 0,
        "sodium": 0
      },
      "rationale": "string"
    }
  ],
  "weeklyTotals": {
    "calories": 0,
    "protein": 0,
    "carbs": 0,
    "fat": 0,
    "sugar": 0,
    "sodium": 0
  }
}

Rules:
1. Return exactly 7 days.
2. Each day should have breakfast, lunch, and dinner. Add snacks only when useful.
3. Respect allergies and diseases/conditions strictly.
4. If the user has diabetes or sugar restriction, keep sugar conservative.
5. If the user has hypertension or sodium restriction, keep sodium conservative.
6. Macros must be realistic estimates for the listed servings.
7. Compute day totalMacros as the sum of meal totalMacros.
8. Compute weeklyTotals as the sum of day totalMacros.
9. Do not include markdown fences or explanation outside JSON.

USER_PROFILE:
Age: ${safeProfileValue(profile?.age)}
Gender: ${safeProfileValue(profile?.gender)}
HeightCm: ${safeProfileValue(profile?.height)}
WeightKg: ${safeProfileValue(profile?.weight)}
BloodType: ${safeProfileValue(profile?.bloodType)}
ActivityLevel: ${safeProfileValue(profile?.activityLevel)}
Goal: ${safeProfileValue(profile?.goal)}
Allergies: ${safeProfileValue(profile?.allergies)}
DiseasesOrConditions: ${safeProfileValue(profile?.diseases)}`;
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
    <Card className="flex min-h-[330px] flex-col shadow-sm hover:shadow-md transition-shadow">
      <CardHeader className="border-b bg-gradient-to-r from-blue-50 to-indigo-50">
        <div className="flex items-start gap-3">
          <div>
            <CardTitle className="text-lg font-semibold">{title}</CardTitle>
            <CardDescription className="text-sm">{description}</CardDescription>
          </div>
        </div>
      </CardHeader>

      <CardContent className="flex flex-1 flex-col gap-4 p-6">
        <div className="flex-1 rounded-lg border border-gray-200 bg-white p-5 text-sm leading-6 text-gray-700 shadow-xs">
          {body}
        </div>

        {state.message && (
          <div className="rounded-lg border border-blue-200 bg-blue-50 px-4 py-3 text-sm text-blue-900 font-medium shadow-xs">
            {state.message}
          </div>
        )}

        <Button
          onClick={onSend}
          disabled={isLoading}
          className="ml-auto w-full sm:w-auto bg-blue-600 hover:bg-blue-700 text-white font-medium shadow-sm hover:shadow-md transition-all"
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
    <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
      {values.map((item) => (
        <div key={item.label} className="rounded-lg border border-gray-200 bg-gradient-to-br from-white to-gray-50 px-4 py-3 shadow-xs hover:shadow-sm transition-shadow">
          <p className="text-xs font-semibold uppercase tracking-wider text-gray-600">
            {item.label}
          </p>
          <p className="mt-2 text-base font-bold text-gray-900">
            {item.value}
          </p>
        </div>
      ))}
    </div>
  );
}

function GeneratedPlan({
  plan,
  title,
}: {
  plan: NutritionPlanResponse;
  title: string;
}) {
  const [openDay, setOpenDay] = useState<number>(plan.days[0]?.day ?? 1);

  return (
    <div className="overflow-hidden rounded-lg border border-gray-200 bg-white shadow-sm">
      <div className="border-b bg-gradient-to-r from-blue-50 to-indigo-50 px-6 py-5">
        <div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
          <div>
            <div className="flex items-center gap-2">
              <CalendarDays className="h-5 w-5 text-blue-600" />
              <h3 className="text-lg font-semibold text-gray-900">
                {title}
              </h3>
            </div>
            <p className="mt-2 text-sm text-gray-600">{plan.summary}</p>
          </div>
          <div className="rounded-lg border border-gray-200 bg-white px-4 py-2 text-sm font-medium text-gray-700 shadow-xs">
            Status: {plan.goalStatus}
          </div>
        </div>
      </div>

      <div className="space-y-6 p-6">
        <div className="space-y-3">
          <h3 className="text-sm font-semibold text-gray-900 uppercase tracking-wide">Weekly totals</h3>
          <MacroGrid macros={plan.weeklyTotals} />
        </div>

        <div className="space-y-3">
          {plan.days.map((day) => {
            const isOpen = openDay === day.day;

            return (
              <div
                key={day.day}
                className="overflow-hidden rounded-lg border border-gray-200 bg-white shadow-xs hover:shadow-sm transition-shadow"
              >
                <button
                  type="button"
                  onClick={() => setOpenDay(isOpen ? 0 : day.day)}
                  className="flex w-full items-center justify-between gap-3 border-b bg-gradient-to-r from-gray-50 to-transparent px-5 py-4 text-left hover:bg-gray-50 transition-colors"
                >
                  <div>
                    <p className="font-semibold text-gray-900">Day {day.day}</p>
                    <p className="text-sm text-gray-600 mt-1">{day.rationale}</p>
                  </div>
                  <ChevronDown
                    className={`h-5 w-5 shrink-0 text-gray-500 transition-transform`}
                    style={{
                      transform: isOpen ? 'rotate(180deg)' : 'rotate(0deg)',
                    }}
                  />
                </button>

                {isOpen && (
                  <div className="space-y-5 p-6 bg-gradient-to-b from-white to-gray-50">
                    <MacroGrid macros={day.totalMacros} />

                    <div className="grid gap-4">
                      {day.meals.map((meal) => (
                        <div
                          key={`${day.day}-${meal.slot}`}
                          className="rounded-lg border border-gray-200 bg-white p-5 shadow-xs hover:shadow-sm transition-shadow"
                        >
                          <div className="flex items-start justify-between gap-3">
                            <div>
                              <p className="text-xs font-semibold uppercase tracking-wider text-gray-600">
                                {meal.slot}
                              </p>
                              <h4 className="mt-2 font-semibold text-gray-900">
                                {meal.recipeName}
                              </h4>
                            </div>
                            <span className="rounded-md border border-blue-200 bg-blue-50 px-3 py-1 text-xs font-semibold text-blue-700 whitespace-nowrap">
                              {meal.servings} serving
                            </span>
                          </div>

                          <p className="mt-3 text-sm text-gray-600 leading-relaxed">
                            {meal.reason}
                          </p>

                          <div className="mt-4">
                            <p className="text-sm font-semibold text-gray-900 mb-2">
                              Ingredients
                            </p>
                            <ul className="space-y-1 text-sm text-gray-600">
                              {meal.ingredients.map((ingredient) => (
                                <li key={ingredient} className="flex items-start gap-2">
                                  <span className="text-blue-600 mt-1">•</span>
                                  <span>{ingredient}</span>
                                </li>
                              ))}
                            </ul>
                          </div>

                          <div className="mt-4 grid grid-cols-2 gap-3 text-sm">
                            <div className="rounded-lg border border-gray-200 bg-white px-3 py-2 font-medium text-gray-900 shadow-xs">
                              {meal.totalMacros.calories} kcal
                            </div>
                            <div className="rounded-lg border border-gray-200 bg-white px-3 py-2 font-medium text-gray-900 shadow-xs">
                              {meal.totalMacros.protein}g protein
                            </div>
                            <div className="rounded-lg border border-gray-200 bg-white px-3 py-2 font-medium text-gray-900 shadow-xs">
                              {meal.totalMacros.carbs}g carbs
                            </div>
                            <div className="rounded-lg border border-gray-200 bg-white px-3 py-2 font-medium text-gray-900 shadow-xs">
                              {meal.totalMacros.fat}g fat
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

function PlanLoading({
  title,
  message,
}: {
  title: string;
  message: string;
}) {
  return (
    <div className="flex flex-col items-center gap-4 rounded-lg border border-blue-200 bg-gradient-to-br from-blue-50 to-indigo-50 p-8 text-center shadow-sm">
      <div className="relative flex h-16 w-16 items-center justify-center rounded-full border-2 border-blue-200 bg-white shadow-md">
        <div className="absolute h-16 w-16 animate-pulse rounded-full bg-blue-200" />
        <Loader2 className="relative h-7 w-7 animate-spin text-blue-600" />
      </div>
      <div className="space-y-2 transition-all duration-300">
        <p className="text-lg font-semibold text-gray-900">
          {title}
        </p>
        <p className="text-sm text-gray-700 leading-relaxed">{message}</p>
        <p className="text-xs font-medium text-gray-600 mt-3">
          This can take up to 20 minutes.
        </p>
      </div>
    </div>
  );
}

function GeminiPlaceholderResult({ message }: { message: string }) {
  if (!message) {
    return null;
  }

  return (
    <div className="rounded-lg border border-gray-200 bg-gradient-to-br from-white to-gray-50 p-5 shadow-xs">
      <p className="text-sm font-semibold text-gray-900">Gemini result</p>
      <p className="mt-2 text-sm text-gray-600 leading-relaxed">{message}</p>
    </div>
  );
}

function PlanComparisonVotePanel({
  selectedWinner,
  reason,
  score,
  isSubmitting,
  message,
  onSelectWinner,
  onReasonChange,
  onSubmit,
}: {
  selectedWinner: VoteWinner | "";
  reason: string;
  score: PlanComparisonScore | null;
  isSubmitting: boolean;
  message: string;
  onSelectWinner: (winner: VoteWinner) => void;
  onReasonChange: (reason: string) => void;
  onSubmit: () => void;
}) {
  const options: Array<{ label: string; value: VoteWinner }> = [
    { label: "RecipeKG is better", value: "RECIPE_KG" },
    { label: "Gemini is better", value: "GEMINI" },
    { label: "Tie", value: "TIE" },
  ];

  return (
    <Card className="shadow-sm">
      <CardHeader className="border-b bg-gradient-to-r from-amber-50 to-orange-50">
        <div className="flex items-center gap-3">
          <Trophy className="h-5 w-5 text-amber-600" />
          <div>
            <CardTitle className="text-lg font-semibold">Vote on the better plan</CardTitle>
            <CardDescription className="text-sm">
              Choose the plan that looks more useful, realistic, and safe for the user profile.
            </CardDescription>
          </div>
        </div>
      </CardHeader>
      <CardContent className="space-y-5 p-6">
        {score && (
          <div className="grid gap-3 sm:grid-cols-4">
            <div className="rounded-lg border border-gray-200 bg-gradient-to-br from-white to-gray-50 px-4 py-3 shadow-xs">
              <p className="text-xs font-semibold uppercase tracking-wider text-gray-600">RecipeKG</p>
              <p className="mt-2 text-lg font-bold text-gray-900">{score.recipeKgWins}</p>
            </div>
            <div className="rounded-lg border border-gray-200 bg-gradient-to-br from-white to-gray-50 px-4 py-3 shadow-xs">
              <p className="text-xs font-semibold uppercase tracking-wider text-gray-600">Gemini</p>
              <p className="mt-2 text-lg font-bold text-gray-900">{score.geminiWins}</p>
            </div>
            <div className="rounded-lg border border-gray-200 bg-gradient-to-br from-white to-gray-50 px-4 py-3 shadow-xs">
              <p className="text-xs font-semibold uppercase tracking-wider text-gray-600">Ties</p>
              <p className="mt-2 text-lg font-bold text-gray-900">{score.ties}</p>
            </div>
            <div className="rounded-lg border border-gray-200 bg-gradient-to-br from-white to-gray-50 px-4 py-3 shadow-xs">
              <p className="text-xs font-semibold uppercase tracking-wider text-gray-600">Total</p>
              <p className="mt-2 text-lg font-bold text-gray-900">{score.totalVotes}</p>
            </div>
          </div>
        )}

        <div className="grid gap-3 sm:grid-cols-3">
          {options.map((option) => (
            <Button
              key={option.value}
              type="button"
              variant={selectedWinner === option.value ? "default" : "outline"}
              onClick={() => onSelectWinner(option.value)}
              className={selectedWinner === option.value ? "bg-blue-600 hover:bg-blue-700 shadow-sm" : ""}
            >
              {option.label}
            </Button>
          ))}
        </div>

        <Textarea
          value={reason}
          onChange={(event) => onReasonChange(event.target.value)}
          placeholder="Optional reason for your vote"
          className="min-h-[100px] rounded-lg border-gray-200 shadow-xs"
        />

        {message && (
          <div className="rounded-lg border border-blue-200 bg-blue-50 px-4 py-3 text-sm text-blue-900 font-medium shadow-xs">
            {message}
          </div>
        )}

        <Button 
          onClick={onSubmit} 
          disabled={isSubmitting || !selectedWinner}
          className="w-full bg-blue-600 hover:bg-blue-700 text-white font-medium shadow-sm hover:shadow-md transition-all"
        >
          {isSubmitting ? <Loader2 className="h-4 w-4 animate-spin" /> : <Trophy className="h-4 w-4" />}
          Submit vote
        </Button>
      </CardContent>
    </Card>
  );
}

export function Dashboard() {
  const { profile, account, isProfileLoading } = useAuth();
  const [generatedPlan, setGeneratedPlan] =
    useState<NutritionPlanResponse | null>(null);
  const [generatedGeminiPlan, setGeneratedGeminiPlan] =
    useState<NutritionPlanResponse | null>(null);
  const [selectedWinner, setSelectedWinner] = useState<VoteWinner | "">("");
  const [voteReason, setVoteReason] = useState("");
  const [voteMessage, setVoteMessage] = useState("");
  const [isVoteSubmitting, setIsVoteSubmitting] = useState(false);
  const [comparisonScore, setComparisonScore] =
    useState<PlanComparisonScore | null>(null);
  const [activeRecipeKgJobId, setActiveRecipeKgJobId] = useState<string | null>(
    null,
  );
  const [recipeKgState, setRecipeKgState] = useState<PanelState>({
    status: "idle",
    message: "",
  });
  const [geminiState, setGeminiState] = useState<PanelState>({
    status: "idle",
    message: "",
  });
  const pollingTimeoutRef = useRef<number | null>(null);
  const pollingAbortRef = useRef<AbortController | null>(null);
  const loadingMessageIntervalRef = useRef<number | null>(null);

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
    () => buildGeminiPrompt(profile),
    [profile],
  );
  const shouldMatchPanelHeights =
    recipeKgState.status === "idle" &&
    geminiState.status === "idle" &&
    !generatedPlan &&
    !generatedGeminiPlan;

  async function fetchComparisonScore() {
    try {
      const response = await fetch("http://localhost:8080/api/comparisons/score");
      if (!response.ok) {
        return;
      }

      setComparisonScore((await response.json()) as PlanComparisonScore);
    } catch {
      setComparisonScore(null);
    }
  }

  useEffect(() => {
    void fetchComparisonScore();
  }, []);

  function stopPolling() {
    if (pollingTimeoutRef.current !== null) {
      window.clearTimeout(pollingTimeoutRef.current);
      pollingTimeoutRef.current = null;
    }

    pollingAbortRef.current?.abort();
    pollingAbortRef.current = null;
  }

  function stopLoadingMessages() {
    if (loadingMessageIntervalRef.current !== null) {
      window.clearInterval(loadingMessageIntervalRef.current);
      loadingMessageIntervalRef.current = null;
    }
  }

  function startLoadingMessages(initialMessage: string) {
    stopLoadingMessages();

    setRecipeKgState({
      status: "loading",
      message: initialMessage,
    });

    let messageIndex = recipeKgLoadingMessages.indexOf(initialMessage);
    if (messageIndex < 0) {
      messageIndex = 0;
    }

    loadingMessageIntervalRef.current = window.setInterval(() => {
      messageIndex = (messageIndex + 1) % recipeKgLoadingMessages.length;
      setRecipeKgState({
        status: "loading",
        message: recipeKgLoadingMessages[messageIndex],
      });
    }, recipeKgLoadingMessageIntervalMs);
  }

  function finishRecipeKgJob(message: string, plan: NutritionPlanResponse) {
    stopPolling();
    stopLoadingMessages();
    setActiveRecipeKgJobId(null);
    setGeneratedPlan(plan);
    setRecipeKgState({
      status: "sent",
      message,
    });
  }

  function failRecipeKgJob(message: string) {
    stopPolling();
    stopLoadingMessages();
    setActiveRecipeKgJobId(null);
    setRecipeKgState({
      status: "sent",
      message,
    });
  }

  function scheduleRecipeKgPoll(jobId: string) {
    pollingTimeoutRef.current = window.setTimeout(() => {
      void pollRecipeKgJob(jobId);
    }, recipeKgPollingIntervalMs);
  }

  async function pollRecipeKgJob(jobId: string) {
    stopPolling();

    const controller = new AbortController();
    pollingAbortRef.current = controller;

    try {
      const response = await fetch(
        `http://localhost:8080/api/planner/generate/status/${jobId}`,
        { signal: controller.signal },
      );

      if (!response.ok) {
        throw new Error("Polling failed");
      }

      const job = (await response.json()) as NutritionPlanJobResponse;
      pollingAbortRef.current = null;

      if (job.status === "COMPLETED" && job.nutritionPlan) {
        finishRecipeKgJob(job.message || "RecipeKG plan generated successfully.", job.nutritionPlan);
        return;
      }

      if (job.status === "FAILED") {
        failRecipeKgJob(job.message || "RecipeKG plan generation failed.");
        return;
      }

      setActiveRecipeKgJobId(job.jobId);
      setRecipeKgState({
        status: "loading",
        message: job.message || recipeKgLoadingMessages[0],
      });
      scheduleRecipeKgPoll(job.jobId);
    } catch (error) {
      if (controller.signal.aborted) {
        return;
      }

      pollingAbortRef.current = null;
      failRecipeKgJob("Unable to poll the RecipeKG job status. Make sure the backend is running on port 8080.");
    }
  }

  function continueRecipeKgJob(jobId: string, message?: string) {
    setActiveRecipeKgJobId(jobId);
    startLoadingMessages(message || recipeKgLoadingMessages[0]);
    scheduleRecipeKgPoll(jobId);
  }

  async function generateRecipeKgPlan() {
    if (recipeKgState.status === "loading" || activeRecipeKgJobId) {
      return;
    }

    if (!account?.id) {
      setRecipeKgState({
        status: "sent",
        message: "Unable to start generation because the current user id is missing.",
      });
      return;
    }

    setGeneratedPlan(null);
    stopPolling();
    startLoadingMessages(recipeKgLoadingMessages[0]);

    try {
      const response = await fetch(
        `http://localhost:8080/api/planner/generate/${account?.id}`,
        {
          method: "POST",
        },
      );

      if (!response.ok) {
        throw new Error("Request failed");
      }

      const job = (await response.json()) as NutritionPlanJobResponse;

      if (job.status === "COMPLETED" && job.nutritionPlan) {
        finishRecipeKgJob(job.message || "RecipeKG plan generated successfully.", job.nutritionPlan);
        return;
      }

      if (job.status === "FAILED") {
        failRecipeKgJob(job.message || "RecipeKG plan generation failed.");
        return;
      }

      continueRecipeKgJob(job.jobId, job.message);
    } catch {
      failRecipeKgJob("Unable to start RecipeKG generation. Make sure the backend is running on port 8080.");
    }
  }

  useEffect(() => {
    if (!account?.id) {
      return;
    }

    const controller = new AbortController();

    async function fetchCurrentRecipeKgPlan() {
      try {
        const response = await fetch(
          `http://localhost:8080/api/planner/nutrition-plan/current/${account.id}?generatedBy=RECIPE_KG_AGENT`,
          { signal: controller.signal },
        );

        if (!response.ok) {
          throw new Error("Current plan lookup failed");
        }

        const current = (await response.json()) as CurrentNutritionPlanResponse;

        if (current.status === "COMPLETED" && current.nutritionPlan) {
          finishRecipeKgJob(current.message || "Loaded saved RecipeKG plan.", current.nutritionPlan);
          return;
        }

        if ((current.status === "PENDING" || current.status === "RUNNING") && current.jobId) {
          continueRecipeKgJob(current.jobId, current.message);
          return;
        }

        if (current.status === "FAILED") {
          failRecipeKgJob(current.message || "The last RecipeKG generation failed.");
        }
      } catch (error) {
        if (controller.signal.aborted) {
          return;
        }

        setRecipeKgState({
          status: "idle",
          message: "",
        });
      }
    }

    async function fetchCurrentGeminiPlan() {
      try {
        const response = await fetch(
          `http://localhost:8080/api/planner/nutrition-plan/current/${account.id}?generatedBy=DIRECT_GEMINI`,
          { signal: controller.signal },
        );

        if (!response.ok) {
          throw new Error("Current Gemini plan lookup failed");
        }

        const current = (await response.json()) as CurrentNutritionPlanResponse;

        if (current.status === "COMPLETED" && current.nutritionPlan) {
          setGeneratedGeminiPlan(current.nutritionPlan);
          setGeminiState({
            status: "sent",
            message: current.message || "Loaded saved Gemini plan.",
          });
          return;
        }

        if (current.status === "NOT_FOUND") {
          setGeneratedGeminiPlan(null);
          setGeminiState({
            status: "idle",
            message: "",
          });
        }
      } catch (error) {
        if (controller.signal.aborted) {
          return;
        }

        setGeminiState({
          status: "idle",
          message: "",
        });
      }
    }

    void fetchCurrentRecipeKgPlan();
    void fetchCurrentGeminiPlan();

    return () => {
      controller.abort();
      stopPolling();
      stopLoadingMessages();
    };
  }, [account?.id]);

  async function generateDirectGeminiPlan() {
    if (geminiState.status === "loading") {
      return;
    }

    if (!account?.id) {
      setGeminiState({
        status: "sent",
        message: "Unable to start Gemini generation because the current user id is missing.",
      });
      return;
    }

    setGeneratedGeminiPlan(null);
    setGeminiState({
      status: "loading",
      message: "Generating direct Gemini nutrition plan. This can take a few minutes.",
    });

    try {
      const response = await fetch(
        `http://localhost:8080/api/planner/generate-direct-gemini/${account.id}`,
        {
          method: "POST",
        },
      );

      if (!response.ok) {
        throw new Error("Request failed");
      }

      const plan = (await response.json()) as NutritionPlanResponse;
      setGeneratedGeminiPlan(plan);
      setGeminiState({
        status: "sent",
        message: "Gemini plan generated successfully.",
      });
    } catch {
      setGeminiState({
        status: "sent",
        message:
          "Unable to generate the direct Gemini plan. Make sure the backend is running on port 8080.",
      });
    }
  }

  async function submitComparisonVote() {
    if (!account?.id || !selectedWinner) {
      return;
    }

    setIsVoteSubmitting(true);
    setVoteMessage("");

    try {
      const response = await fetch("http://localhost:8080/api/comparisons/vote", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          userId: account.id,
          winner: selectedWinner,
          reason: voteReason,
        }),
      });

      if (!response.ok) {
        throw new Error("Vote failed");
      }

      const savedVote = (await response.json()) as PlanComparisonVoteResponse;
      setVoteMessage(savedVote.message || "Vote saved.");
      await fetchComparisonScore();
    } catch {
      setVoteMessage("Unable to save your vote. Make sure both plans were generated and saved.");
    } finally {
      setIsVoteSubmitting(false);
    }
  }

  return (
    <div className="mx-auto max-w-6xl space-y-8 py-2">
      <Card className="shadow-sm hover:shadow-md transition-shadow">
        <CardHeader className="border-b bg-gradient-to-r from-blue-50 to-indigo-50">
          <div className="flex items-center gap-3">
            <div className="rounded-lg border border-blue-200 bg-blue-50 p-2 text-blue-600 shadow-sm">
              <UserRound className="h-5 w-5" />
            </div>
            <div className="min-w-0 flex-1">
              <CardTitle className="text-lg font-semibold">User parameters</CardTitle>
              <CardDescription className="text-sm">
                {isProfileLoading
                  ? "Loading profile values from registration"
                  : "Profile values from registration"}
              </CardDescription>
            </div>
            <Button asChild variant="outline" size="sm" className="shrink-0 shadow-sm hover:shadow-md transition-all">
              <Link to="/profile">
                <Settings className="h-4 w-4" />
                Edit profile
              </Link>
            </Button>
          </div>
        </CardHeader>
        <CardContent className="grid gap-3 p-6 sm:grid-cols-2">
          {profileParameters.map((parameter) => (
            <div
              key={parameter.label}
              className={`rounded-lg border border-gray-200 bg-gradient-to-br from-white to-gray-50 px-4 py-3 shadow-xs hover:shadow-sm transition-shadow ${
                parameter.wide ? "sm:col-span-2" : ""
              }`}
            >
              <p className="text-xs font-semibold uppercase tracking-wider text-gray-600">
                {parameter.label}
              </p>
              <p className="mt-2 break-words text-sm font-semibold text-gray-900">
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
                nutrition planner estimates servings, balances macros across the
                week, and composes a structured plan with meals, ingredients,
                rationale, and nutrition totals.
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
              <PlanLoading
                title="Generating RecipeKG plan"
                message={recipeKgState.message}
              />
            ) : generatedPlan ? (
              <GeneratedPlan plan={generatedPlan} title="Generated RecipeKG plan" />
            ) : null
          }
        />

        <PromptPanel
          title="Google Gemini"
          description="Direct baseline through future controller"
          buttonLabel="Generate with Google Gemini"
          buttonIcon={Sparkles}
          state={geminiState}
          onSend={generateDirectGeminiPlan}
          body={
            <div className="space-y-3">
              <p>Pregenerated prompt based on your parameters:</p>
              <Textarea
                value={geminiPrompt}
                readOnly
                className="max-h-[460px] min-h-[260px] resize-none overflow-y-auto bg-white font-mono text-sm leading-6"
              />
            </div>
          }
          result={
            geminiState.status === "loading" ? (
              <PlanLoading
                title="Generating Gemini plan"
                message={geminiState.message}
              />
            ) : generatedGeminiPlan ? (
              <GeneratedPlan plan={generatedGeminiPlan} title="Generated Gemini plan" />
            ) : (
              <GeminiPlaceholderResult message={geminiState.message} />
            )
          }
        />
      </div>

      {generatedPlan && generatedGeminiPlan && (
        <PlanComparisonVotePanel
          selectedWinner={selectedWinner}
          reason={voteReason}
          score={comparisonScore}
          isSubmitting={isVoteSubmitting}
          message={voteMessage}
          onSelectWinner={setSelectedWinner}
          onReasonChange={setVoteReason}
          onSubmit={submitComparisonVote}
        />
      )}
    </div>
  );
}

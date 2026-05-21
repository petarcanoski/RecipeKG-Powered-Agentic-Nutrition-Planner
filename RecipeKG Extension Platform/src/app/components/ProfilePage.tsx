import { ArrowLeft, Save, UserRound } from "lucide-react";
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
import { Input } from "./ui/input";

type ProfileField = {
  label: string;
  value: string;
};

function formatList(values?: string[]) {
  if (!values || values.length === 0) {
    return "None";
  }

  return values.join(", ");
}

export function ProfilePage() {
  const { profile, isProfileLoading } = useAuth();

  const fields: ProfileField[] = [
    { label: "Name", value: profile?.name ?? "" },
    { label: "Surname", value: profile?.surname ?? "" },
    { label: "Email", value: profile?.email ?? "" },
    { label: "Age", value: profile?.age ? String(profile.age) : "" },
    { label: "Gender", value: profile?.gender ?? "" },
    { label: "Height", value: profile?.height ? `${profile.height} cm` : "" },
    { label: "Weight", value: profile?.weight ? `${profile.weight} kg` : "" },
    { label: "Blood type", value: profile?.bloodType ?? "" },
    { label: "Activity level", value: profile?.activityLevel ?? "" },
    { label: "Goal", value: profile?.goal ?? "" },
    { label: "Allergies", value: formatList(profile?.allergies) },
    { label: "Diseases", value: formatList(profile?.diseases) },
  ];

  return (
    <div className="mx-auto max-w-4xl space-y-6">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <div className="flex items-center gap-2">
            <UserRound className="h-6 w-6 text-blue-600" />
            <h2 className="text-2xl font-semibold text-gray-900">Profile</h2>
          </div>
          <p className="mt-1 text-gray-600">
            Review your registered parameters. Editing will be enabled later.
          </p>
        </div>

        <Button asChild variant="outline">
          <Link to="/">
            <ArrowLeft className="h-4 w-4" />
            Back to dashboard
          </Link>
        </Button>
      </div>

      <Card>
        <CardHeader className="border-b">
          <CardTitle className="text-lg">User parameters</CardTitle>
          <CardDescription>
            {isProfileLoading
              ? "Loading profile values"
              : "Current profile values from your account"}
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-5 p-5">
          <div className="grid gap-4 sm:grid-cols-2">
            {fields.map((field) => (
              <label key={field.label} className="space-y-1">
                <span className="text-sm font-medium text-gray-700">
                  {field.label}
                </span>
                <Input
                  value={isProfileLoading ? "Loading" : field.value}
                  disabled
                  readOnly
                />
              </label>
            ))}
          </div>

          <div className="rounded-md border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-800">
            Updating profile parameters is not implemented yet.
          </div>

          <Button disabled className="w-full sm:w-auto">
            <Save className="h-4 w-4" />
            Save changes
          </Button>
        </CardContent>
      </Card>
    </div>
  );
}

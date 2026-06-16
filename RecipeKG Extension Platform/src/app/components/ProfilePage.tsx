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
    <div className="mx-auto max-w-4xl space-y-8">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <div className="flex items-center gap-3">
            <div className="rounded-lg bg-gradient-to-br from-blue-50 to-indigo-50 p-2.5 border border-blue-200">
              <UserRound className="h-6 w-6 text-blue-600" />
            </div>
            <h2 className="text-3xl font-bold text-gray-900">Profile</h2>
          </div>
          <p className="mt-2 text-gray-600">
            Review your registered parameters. Editing will be enabled later.
          </p>
        </div>

        <Button asChild variant="outline" className="shadow-sm hover:shadow-md transition-all">
          <Link to="/">
            <ArrowLeft className="h-4 w-4" />
            Back to dashboard
          </Link>
        </Button>
      </div>

      <Card className="shadow-sm">
        <CardHeader className="border-b bg-gradient-to-r from-blue-50 to-indigo-50">
          <CardTitle className="text-lg font-semibold">User parameters</CardTitle>
          <CardDescription className="text-sm">
            {isProfileLoading
              ? "Loading profile values"
              : "Current profile values from your account"}
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-6 p-6">
          <div className="grid gap-5 sm:grid-cols-2">
            {fields.map((field) => (
              <label key={field.label} className="space-y-2.5">
                <span className="text-xs font-semibold uppercase tracking-wider text-gray-600">
                  {field.label}
                </span>
                <Input
                  value={isProfileLoading ? "Loading" : field.value}
                  disabled
                  readOnly
                  className="bg-gray-50 border-gray-200 text-gray-700 shadow-xs"
                />
              </label>
            ))}
          </div>

          <div className="rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-900 font-medium shadow-xs">
            Updating profile parameters is not implemented yet.
          </div>

          <Button disabled className="w-full sm:w-auto bg-gray-400 shadow-sm">
            <Save className="h-4 w-4" />
            Save changes
          </Button>
        </CardContent>
      </Card>
    </div>
  );
}

import { FormEvent, useState } from "react";
import { Navigate, useNavigate } from "react-router";
import { useAuth } from "../auth/AuthContext";
import { AccountProfile } from "../auth/types";
import { Button } from "./ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "./ui/card";
import { Input } from "./ui/input";
import { Textarea } from "./ui/textarea";

type CreateAccountForm = {
  name: string;
  surname: string;
  email: string;
  password: string;
  age: string;
  gender: string;
  height: string;
  weight: string;
  bloodType: string;
  activityLevel: string;
  goal: string;
  allergies: string;
  diseases: string;
};

const defaultCreateForm: CreateAccountForm = {
  name: "",
  surname: "",
  email: "",
  password: "",
  age: "",
  gender: "",
  height: "",
  weight: "",
  bloodType: "",
  activityLevel: "",
  goal: "",
  allergies: "",
  diseases: "",
};

function InputRow({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <label className="space-y-1">
      <span className="text-sm font-medium text-gray-700">{label}</span>
      {children}
    </label>
  );
}

export function AuthPage() {
  const { account, isAuthenticated, registerAccount, login } = useAuth();
  const navigate = useNavigate();
  const [createForm, setCreateForm] = useState<CreateAccountForm>(defaultCreateForm);
  const [loginEmail, setLoginEmail] = useState("");
  const [loginPassword, setLoginPassword] = useState("");
  const [errorMessage, setErrorMessage] = useState("");

  if (isAuthenticated) {
    return <Navigate to="/" replace />;
  }

  const accountExists = !!account;
  const [mode, setMode] = useState<"login" | "register">(accountExists ? "login" : "register");

  function submitCreateAccount(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setErrorMessage("");

    const fields = Object.keys(createForm) as Array<keyof CreateAccountForm>;
    const emptyField = fields.filter((field) => createForm[field].trim().length === 0)[0];
    if (emptyField) {
      setErrorMessage("Please fill in all fields to create your account.");
      return;
    }

    const age = Number(createForm.age);
    const height = Number(createForm.height);
    const weight = Number(createForm.weight);

    if (isNaN(age) || isNaN(height) || isNaN(weight)) {
      setErrorMessage("Age, height, and weight must be valid numbers.");
      return;
    }

    const newAccount: AccountProfile = {
      name: createForm.name.trim(),
      surname: createForm.surname.trim(),
      email: createForm.email.trim(),
      password: createForm.password,
      age,
      gender: createForm.gender.trim(),
      height,
      weight,
      bloodType: createForm.bloodType.trim(),
      activityLevel: createForm.activityLevel.trim(),
      goal: createForm.goal.trim(),
      allergies: createForm.allergies.trim(),
      diseases: createForm.diseases.trim(),
    };

    registerAccount(newAccount).then((result) => {
      if (!result.success) {
        setErrorMessage(result.message || "Unable to create account.");
        return;
      }

      navigate("/", { replace: true });
    }).catch(() => {
      setErrorMessage("An error occurred while creating your account.");
    });
  }

  function submitLogin(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setErrorMessage("");

    if (!loginEmail.trim() || !loginPassword) {
      setErrorMessage("Please provide email and password.");
      return;
    }

    login(loginEmail, loginPassword).then((result) => {
      if (!result.success) {
        setErrorMessage(result.message || "Unable to log in.");
        return;
      }

      navigate("/", { replace: true });
    }).catch(() => {
      setErrorMessage("An error occurred while logging in.");
    });
  }

  return (
    <div className="min-h-screen bg-gray-50 px-4 py-8 sm:px-6 lg:px-8">
      <div className="mx-auto w-full max-w-3xl">
        <Card>
          <CardHeader>
            <CardTitle>{mode === "register" ? "Create Your Account" : "Log In"}</CardTitle>
            <CardDescription>
              {mode === "register"
                ? "Create your profile before accessing any page in the platform."
                : "Log in with your email and password to access the platform."}
            </CardDescription>
          </CardHeader>
          <CardContent>
            <div className="mb-4 grid w-full grid-cols-2 gap-2">
              <Button
                type="button"
                variant={mode === "register" ? "default" : "outline"}
                onClick={() => {
                  setMode("register");
                  setErrorMessage("");
                }}
              >
                Register
              </Button>
              <Button
                type="button"
                variant={mode === "login" ? "default" : "outline"}
                onClick={() => {
                  setMode("login");
                  setErrorMessage("");
                }}
              >
                Login
              </Button>
            </div>

            {mode === "register" && accountExists && (
              <div className="mb-4 rounded-md border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-800">
                An account already exists on this device. Use Login to access it.
              </div>
            )}

            {errorMessage && (
              <div className="mb-4 rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">
                {errorMessage}
              </div>
            )}

            {mode === "register" ? (
              <form onSubmit={submitCreateAccount} className="space-y-4">
                <div className="grid gap-4 sm:grid-cols-2">
                  <InputRow label="Name">
                    <Input
                      value={createForm.name}
                      onChange={(event) => setCreateForm({ ...createForm, name: event.target.value })}
                    />
                  </InputRow>
                  <InputRow label="Surname">
                    <Input
                      value={createForm.surname}
                      onChange={(event) => setCreateForm({ ...createForm, surname: event.target.value })}
                    />
                  </InputRow>
                  <InputRow label="Email">
                    <Input
                      type="email"
                      value={createForm.email}
                      onChange={(event) => setCreateForm({ ...createForm, email: event.target.value })}
                    />
                  </InputRow>
                  <InputRow label="Password">
                    <Input
                      type="password"
                      value={createForm.password}
                      onChange={(event) => setCreateForm({ ...createForm, password: event.target.value })}
                    />
                  </InputRow>
                  <InputRow label="Age">
                    <Input
                      type="number"
                      min="1"
                      value={createForm.age}
                      onChange={(event) => setCreateForm({ ...createForm, age: event.target.value })}
                    />
                  </InputRow>
                  <InputRow label="Gender">
                    <Input
                      value={createForm.gender}
                      onChange={(event) => setCreateForm({ ...createForm, gender: event.target.value })}
                      placeholder="e.g. Female"
                    />
                  </InputRow>
                  <InputRow label="Height (cm)">
                    <Input
                      type="number"
                      min="1"
                      value={createForm.height}
                      onChange={(event) => setCreateForm({ ...createForm, height: event.target.value })}
                    />
                  </InputRow>
                  <InputRow label="Weight (kg)">
                    <Input
                      type="number"
                      min="1"
                      value={createForm.weight}
                      onChange={(event) => setCreateForm({ ...createForm, weight: event.target.value })}
                    />
                  </InputRow>
                  <InputRow label="Blood Type">
                    <Input
                      value={createForm.bloodType}
                      onChange={(event) => setCreateForm({ ...createForm, bloodType: event.target.value })}
                      placeholder="e.g. O+"
                    />
                  </InputRow>
                  <InputRow label="Activity Level">
                    <Input
                      value={createForm.activityLevel}
                      onChange={(event) => setCreateForm({ ...createForm, activityLevel: event.target.value })}
                      placeholder="e.g. Moderate"
                    />
                  </InputRow>
                  <InputRow label="Goal">
                    <Input
                      value={createForm.goal}
                      onChange={(event) => setCreateForm({ ...createForm, goal: event.target.value })}
                      placeholder="e.g. Weight loss"
                    />
                  </InputRow>
                </div>

                <div className="grid gap-4 sm:grid-cols-2">
                  <InputRow label="Allergies (text)">
                    <Textarea
                      value={createForm.allergies}
                      onChange={(event) => setCreateForm({ ...createForm, allergies: event.target.value })}
                      className="min-h-[90px]"
                    />
                  </InputRow>
                  <InputRow label="Diseases (text)">
                    <Textarea
                      value={createForm.diseases}
                      onChange={(event) => setCreateForm({ ...createForm, diseases: event.target.value })}
                      className="min-h-[90px]"
                    />
                  </InputRow>
                </div>

                <Button type="submit" className="w-full">
                  Create Account
                </Button>
              </form>
            ) : (
              <form onSubmit={submitLogin} className="space-y-4 max-w-md">
                <InputRow label="Email">
                  <Input
                    type="email"
                    value={loginEmail}
                    onChange={(event) => setLoginEmail(event.target.value)}
                  />
                </InputRow>
                <InputRow label="Password">
                  <Input
                    type="password"
                    value={loginPassword}
                    onChange={(event) => setLoginPassword(event.target.value)}
                  />
                </InputRow>
                <Button type="submit" className="w-full">
                  Log In
                </Button>
              </form>
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  );
}



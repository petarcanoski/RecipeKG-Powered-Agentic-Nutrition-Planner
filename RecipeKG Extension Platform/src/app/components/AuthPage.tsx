import { FormEvent, useState } from "react";
import { Navigate, useNavigate } from "react-router";
import { useAuth } from "../auth/AuthContext";
import { AccountProfile } from "../auth/types";
import { Button } from "./ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "./ui/card";
import { Input } from "./ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "./ui/select";
import { PlusIcon, XIcon } from "lucide-react";

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
  allergies: string[];
  diseases: string[];
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
  allergies: [""],
  diseases: [""],
};

const genderOptions = ["Male", "Female"];
const bloodTypeOptions = ["A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-"];
const activityLevelOptions = ["None", "Low", "Moderate", "High"];
const goalOptions = ["Maintain", "Lose weight", "Gain weight", "Gain muscle"];

function InputRow({
  label,
  children,
}: {
  label: string;
  children: React.ReactNode;
}) {
  return (
    <label className="space-y-1">
      <span className="text-sm font-medium text-gray-700">{label}</span>
      {children}
    </label>
  );
}

function SelectRow({
  label,
  value,
  placeholder,
  options,
  onValueChange,
}: {
  label: string;
  value: string;
  placeholder: string;
  options: string[];
  onValueChange: (value: string) => void;
}) {
  return (
    <div className="space-y-1">
      <span className="text-sm font-medium text-gray-700">{label}</span>
      <Select value={value} onValueChange={onValueChange}>
        <SelectTrigger>
          <SelectValue placeholder={placeholder} />
        </SelectTrigger>
        <SelectContent>
          {options.map((option) => (
            <SelectItem key={option} value={option}>
              {option}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
    </div>
  );
}

function RepeatableInputList({
  label,
  values,
  placeholder,
  addLabel,
  onChange,
}: {
  label: string;
  values: string[];
  placeholder: string;
  addLabel: string;
  onChange: (values: string[]) => void;
}) {
  function updateValue(index: number, value: string) {
    onChange(
      values.map((item, itemIndex) => (itemIndex === index ? value : item)),
    );
  }

  function removeValue(index: number) {
    const nextValues = values.filter((_, itemIndex) => itemIndex !== index);
    onChange(nextValues.length > 0 ? nextValues : [""]);
  }

  return (
    <div className="space-y-2">
      <span className="text-sm font-medium text-gray-700">{label}</span>
      <div className="space-y-2">
        {values.map((value, index) => (
          <div key={index} className="flex gap-2">
            <Input
              value={value}
              onChange={(event) => updateValue(index, event.target.value)}
              placeholder={placeholder}
            />
            <Button
              type="button"
              variant="outline"
              size="icon"
              className="shrink-0"
              onClick={() => removeValue(index)}
              aria-label={`Remove ${label.toLowerCase()} item`}
            >
              <XIcon />
            </Button>
          </div>
        ))}
      </div>
      <Button
        type="button"
        variant="outline"
        size="sm"
        className="w-full"
        onClick={() => onChange([...values, ""])}
      >
        <PlusIcon />
        {addLabel}
      </Button>
    </div>
  );
}

export function AuthPage() {
  const { account, isAuthenticated, registerAccount, login } = useAuth();
  const navigate = useNavigate();
  const [createForm, setCreateForm] =
    useState<CreateAccountForm>(defaultCreateForm);
  const [loginEmail, setLoginEmail] = useState("");
  const [loginPassword, setLoginPassword] = useState("");
  const [errorMessage, setErrorMessage] = useState("");

  if (isAuthenticated) {
    return <Navigate to="/" replace />;
  }

  const accountExists = !!account;
  const [mode, setMode] = useState<"login" | "register">(
    accountExists ? "login" : "register",
  );

  function submitCreateAccount(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setErrorMessage("");

    const requiredFields: Array<keyof CreateAccountForm> = [
      "name",
      "surname",
      "email",
      "password",
      "age",
      "gender",
      "height",
      "weight",
      "bloodType",
      "activityLevel",
      "goal",
    ];

    const emptyField = requiredFields.find((field) => createForm[field].trim().length === 0);

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
      allergies: createForm.allergies
        .map((allergy) => allergy.trim())
        .filter(Boolean),
      diseases: createForm.diseases
        .map((disease) => disease.trim())
        .filter(Boolean),
    };

    registerAccount(newAccount)
      .then((result) => {
        if (!result.success) {
          setErrorMessage(result.message || "Unable to create account.");
          return;
        }

        navigate("/", { replace: true });
      })
      .catch(() => {
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

    login(loginEmail, loginPassword)
      .then((result) => {
        if (!result.success) {
          setErrorMessage(result.message || "Unable to log in.");
          return;
        }

        navigate("/", { replace: true });
      })
      .catch(() => {
        setErrorMessage("An error occurred while logging in.");
      });
  }

  return (
    <div className="min-h-screen bg-gray-50 px-4 py-8 sm:px-6 lg:px-8">
      <div className="mx-auto w-full max-w-3xl">
        <Card>
          <CardHeader>
            <CardTitle>
              {mode === "register" ? "Create Your Account" : "Log In"}
            </CardTitle>
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
                An account already exists on this device. Use Login to access
                it.
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
                      onChange={(event) =>
                        setCreateForm({
                          ...createForm,
                          name: event.target.value,
                        })
                      }
                    />
                  </InputRow>
                  <InputRow label="Surname">
                    <Input
                      value={createForm.surname}
                      onChange={(event) =>
                        setCreateForm({
                          ...createForm,
                          surname: event.target.value,
                        })
                      }
                    />
                  </InputRow>
                  <InputRow label="Email">
                    <Input
                      type="email"
                      value={createForm.email}
                      onChange={(event) =>
                        setCreateForm({
                          ...createForm,
                          email: event.target.value,
                        })
                      }
                    />
                  </InputRow>
                  <InputRow label="Password">
                    <Input
                      type="password"
                      value={createForm.password}
                      onChange={(event) =>
                        setCreateForm({
                          ...createForm,
                          password: event.target.value,
                        })
                      }
                    />
                  </InputRow>
                  <InputRow label="Age">
                    <Input
                      type="number"
                      min="1"
                      value={createForm.age}
                      onChange={(event) =>
                        setCreateForm({
                          ...createForm,
                          age: event.target.value,
                        })
                      }
                    />
                  </InputRow>
                  <SelectRow
                    label="Gender"
                    value={createForm.gender}
                    placeholder="Select gender"
                    options={genderOptions}
                    onValueChange={(gender) =>
                      setCreateForm({ ...createForm, gender })
                    }
                  />
                  <InputRow label="Height (cm)">
                    <Input
                      type="number"
                      min="1"
                      value={createForm.height}
                      onChange={(event) =>
                        setCreateForm({
                          ...createForm,
                          height: event.target.value,
                        })
                      }
                    />
                  </InputRow>
                  <InputRow label="Weight (kg)">
                    <Input
                      type="number"
                      min="1"
                      value={createForm.weight}
                      onChange={(event) =>
                        setCreateForm({
                          ...createForm,
                          weight: event.target.value,
                        })
                      }
                    />
                  </InputRow>
                  <SelectRow
                    label="Blood Type"
                    value={createForm.bloodType}
                    placeholder="Select blood type"
                    options={bloodTypeOptions}
                    onValueChange={(bloodType) =>
                      setCreateForm({ ...createForm, bloodType })
                    }
                  />
                  <SelectRow
                    label="Activity Level"
                    value={createForm.activityLevel}
                    placeholder="Select activity level"
                    options={activityLevelOptions}
                    onValueChange={(activityLevel) =>
                      setCreateForm({ ...createForm, activityLevel })
                    }
                  />
                  <SelectRow
                    label="Goal"
                    value={createForm.goal}
                    placeholder="Select goal"
                    options={goalOptions}
                    onValueChange={(goal) =>
                      setCreateForm({ ...createForm, goal })
                    }
                  />
                </div>

                <div className="grid gap-4 sm:grid-cols-2">
                  <RepeatableInputList
                    label="Allergies"
                    values={createForm.allergies}
                    placeholder="e.g. Peanuts"
                    addLabel="Add allergy"
                    onChange={(allergies) =>
                      setCreateForm({ ...createForm, allergies })
                    }
                  />
                  <RepeatableInputList
                    label="Diseases"
                    values={createForm.diseases}
                    placeholder="e.g. Diabetes"
                    addLabel="Add disease"
                    onChange={(diseases) =>
                      setCreateForm({ ...createForm, diseases })
                    }
                  />
                </div>

                <Button type="submit" className="w-full">
                  Create Account
                </Button>
              </form>
            ) : (
              <form
                onSubmit={submitLogin}
                className="space-y-10 max-w-md mx-auto mt-5"
              >
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
                <Button type="submit" className="w-full mt-5">
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

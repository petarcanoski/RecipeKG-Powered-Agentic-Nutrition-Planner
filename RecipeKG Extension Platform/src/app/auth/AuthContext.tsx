import { createContext, useContext, useEffect, useMemo, useState } from "react";
import { AccountProfile, UserProfile } from "./types";

type AuthContextValue = {
  account: AccountProfile | null;
  profile: UserProfile | null;
  isProfileLoading: boolean;
  isAuthenticated: boolean;
  login: (email: string, password: string) => Promise<{ success: boolean; message?: string }>;
  registerAccount: (profile: AccountProfile) => Promise<{ success: boolean; message?: string }>;
  logout: () => void;
};

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

export function AuthProvider({ children }: { children: React.ReactNode }) {

  const storedUserId = typeof window === "undefined" ? null : localStorage.getItem("userId");
  const [account, setAccount] = useState<AccountProfile | null>(
    storedUserId ? { id: Number(storedUserId), email: "" } : null
  );
  const [profile, setProfile] = useState<UserProfile | null>(null);
  const [isProfileLoading, setIsProfileLoading] = useState<boolean>(!!storedUserId);
  const [isAuthenticated, setIsAuthenticated] = useState<boolean>(!!storedUserId);

  async function fetchUserProfile(userId: number) {
    setIsProfileLoading(true);

    try {
      const res = await fetch(`http://localhost:8080/api/profile/${userId}`);

      if (!res.ok) {
        setProfile(null);
        return null;
      }

      const userProfile = await res.json() as UserProfile;
      setProfile(userProfile);
      return userProfile;
    } catch {
      setProfile(null);
      return null;
    } finally {
      setIsProfileLoading(false);
    }
  }

  useEffect(() => {
    if (!account?.id || profile) {
      return;
    }

    void fetchUserProfile(account.id);
  }, [account?.id, profile]);

  const value = useMemo<AuthContextValue>(() => {

    return {

      account,
      profile,
      isProfileLoading,
      isAuthenticated,

      registerAccount: async (profile: AccountProfile) => {

        try {

          const res = await fetch("http://localhost:8080/api/auth/register", {
            method: "POST",
            headers: {
              "Content-Type": "application/json"
            },
            body: JSON.stringify({
              email: profile.email,
              password: profile.password,
              name: profile.name,
              surname: profile.surname,
              age: profile.age,
              gender: profile.gender,
              height: profile.height,
              weight: profile.weight,
              bloodType: profile.bloodType,
              activityLevel: profile.activityLevel,
              goal: profile.goal,
              allergies: profile.allergies,
              diseases: profile.diseases
            })
          });

          if (!res.ok) {
            return { success: false, message: "Register failed" };
          }

          const loginRes = await fetch("http://localhost:8080/api/auth/login", {
            method: "POST",
            headers: {
              "Content-Type": "application/json"
            },
            body: JSON.stringify({
              email: profile.email,
              password: profile.password
            })
          });

          if (loginRes.ok) {
            const user = await loginRes.json();
            const registeredProfile: AccountProfile = {
              ...profile,
              id: user.id,
              email: user.email
            };

            setAccount(registeredProfile);
            setProfile({
              name: profile.name,
              surname: profile.surname,
              email: user.email,
              age: profile.age,
              gender: profile.gender,
              height: profile.height,
              weight: profile.weight,
              bloodType: profile.bloodType,
              activityLevel: profile.activityLevel,
              goal: profile.goal,
              allergies: profile.allergies ?? [],
              diseases: profile.diseases ?? []
            });
            setIsAuthenticated(true);
            localStorage.setItem("userId", String(user.id));
          }

          return { success: true };

        } catch (e) {
          return { success: false, message: "Server not reachable" };
        }

      },

      login: async (email: string, password: string) => {

        try {

          const res = await fetch("http://localhost:8080/api/auth/login", {
            method: "POST",
            headers: {
              "Content-Type": "application/json"
            },
            body: JSON.stringify({
              email,
              password
            })
          });

          if (!res.ok) {
            return { success: false, message: "Invalid credentials" };
          }

          const user = await res.json();

          const profile: AccountProfile = {
            id: user.id,
            email: user.email
          };

          setAccount(profile);
          setIsAuthenticated(true);

          localStorage.setItem("userId", String(user.id));
          await fetchUserProfile(user.id);

          return { success: true };

        } catch (e) {
          return { success: false, message: "Server not reachable" };
        }

      },

      logout: () => {
        setAccount(null);
        setProfile(null);
        setIsProfileLoading(false);
        setIsAuthenticated(false);
        localStorage.removeItem("userId");
      }

    };

  }, [account, profile, isProfileLoading, isAuthenticated]);

  return (
      <AuthContext.Provider value={value}>
        {children}
      </AuthContext.Provider>
  );
}

export function useAuth() {

  const context = useContext(AuthContext);

  if (!context) {
    throw new Error("useAuth must be used inside AuthProvider");
  }

  return context;
}

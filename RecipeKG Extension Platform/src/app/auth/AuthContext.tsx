import { createContext, useContext, useMemo, useState } from "react";

export type AccountProfile = {
  id: number;
  email: string;
};

type AuthContextValue = {
  account: AccountProfile | null;
  isAuthenticated: boolean;
  login: (email: string, password: string) => Promise<{ success: boolean; message?: string }>;
  registerAccount: (email: string, password: string) => Promise<{ success: boolean; message?: string }>;
  logout: () => void;
};

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

export function AuthProvider({ children }: { children: React.ReactNode }) {

  const [account, setAccount] = useState<AccountProfile | null>(null);
  const [isAuthenticated, setIsAuthenticated] = useState<boolean>(false);

  const value = useMemo<AuthContextValue>(() => {

    return {

      account,
      isAuthenticated,

      registerAccount: async (email: string, password: string) => {

        try {

          const res = await fetch("http://localhost:8080/api/auth/register", {
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
            return { success: false, message: "Register failed" };
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

          return { success: true };

        } catch (e) {
          return { success: false, message: "Server not reachable" };
        }

      },

      logout: () => {
        setAccount(null);
        setIsAuthenticated(false);
        localStorage.removeItem("userId");
      }

    };

  }, [account, isAuthenticated]);

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
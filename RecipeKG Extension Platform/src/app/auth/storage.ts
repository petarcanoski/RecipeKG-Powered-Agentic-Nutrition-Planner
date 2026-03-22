import { AccountProfile } from "./types";

const ACCOUNT_KEY = "recipekq.account";

function readStorage(key: string) {
  if (typeof window === "undefined") {
    return null;
  }
  return window.localStorage.getItem(key);
}

export function getStoredAccount(): AccountProfile | null {
  const raw = readStorage(ACCOUNT_KEY);
  if (!raw) {
    return null;
  }

  try {
    return JSON.parse(raw) as AccountProfile;
  } catch {
    return null;
  }
}

export function setStoredAccount(account: AccountProfile) {
  if (typeof window === "undefined") {
    return;
  }
  window.localStorage.setItem(ACCOUNT_KEY, JSON.stringify(account));
}



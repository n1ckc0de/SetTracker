import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import * as authApi from "../api/authApi";
import { configureApiAuth } from "../api/http";
import type { AuthResponse, User } from "../api/types";

const TOKEN_KEY = "gymsync.accessToken";
const USER_KEY = "gymsync.user";

interface AuthState {
  user: User | null;
  accessToken: string | null;
  ready: boolean;
  login: (email: string, password: string) => Promise<void>;
  register: (email: string, password: string, name: string) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthState | null>(null);

function persist(auth: AuthResponse) {
  sessionStorage.setItem(TOKEN_KEY, auth.accessToken);
  sessionStorage.setItem(USER_KEY, JSON.stringify(auth.user));
}

function clearPersisted() {
  sessionStorage.removeItem(TOKEN_KEY);
  sessionStorage.removeItem(USER_KEY);
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [accessToken, setAccessToken] = useState<string | null>(null);
  const [ready, setReady] = useState(false);

  useEffect(() => {
    const token = sessionStorage.getItem(TOKEN_KEY);
    const rawUser = sessionStorage.getItem(USER_KEY);
    if (token && rawUser) {
      try {
        setAccessToken(token);
        setUser(JSON.parse(rawUser) as User);
      } catch {
        clearPersisted();
      }
    }
    setReady(true);
  }, []);

  useEffect(() => {
    configureApiAuth(() => accessToken);
  }, [accessToken]);

  const applyAuth = useCallback((auth: AuthResponse) => {
    persist(auth);
    setAccessToken(auth.accessToken);
    setUser(auth.user);
  }, []);

  const login = useCallback(
    async (email: string, password: string) => {
      applyAuth(await authApi.login(email, password));
    },
    [applyAuth],
  );

  const register = useCallback(
    async (email: string, password: string, name: string) => {
      applyAuth(await authApi.register(email, password, name));
    },
    [applyAuth],
  );

  const logout = useCallback(() => {
    clearPersisted();
    setAccessToken(null);
    setUser(null);
  }, []);

  const value = useMemo(
    () => ({ user, accessToken, ready, login, register, logout }),
    [user, accessToken, ready, login, register, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error("useAuth must be used within AuthProvider");
  }
  return ctx;
}

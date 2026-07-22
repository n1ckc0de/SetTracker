import { Client, type IMessage, type StompSubscription } from "@stomp/stompjs";
import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from "react";
import { wsBaseUrl } from "../api/http";
import type { SessionEvent } from "../api/types";
import { useAuth } from "../auth/AuthContext";

type EventHandler = (event: SessionEvent) => void;

interface SessionSocketState {
  connected: boolean;
  subscribeSession: (workoutId: string, handler: EventHandler) => () => void;
  lastUserEvent: SessionEvent | null;
}

const SessionSocketContext = createContext<SessionSocketState | null>(null);

export function SessionSocketProvider({ children }: { children: ReactNode }) {
  const { accessToken, user } = useAuth();
  const clientRef = useRef<Client | null>(null);
  const userSubRef = useRef<StompSubscription | null>(null);
  const [connected, setConnected] = useState(false);
  const [lastUserEvent, setLastUserEvent] = useState<SessionEvent | null>(null);

  useEffect(() => {
    if (!accessToken || !user) {
      setConnected(false);
      setLastUserEvent(null);
      clientRef.current?.deactivate();
      clientRef.current = null;
      return;
    }

    const client = new Client({
      brokerURL: `${wsBaseUrl()}/ws/sessions`,
      connectHeaders: {
        Authorization: `Bearer ${accessToken}`,
      },
      reconnectDelay: 3000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      onConnect: () => {
        setConnected(true);
        userSubRef.current?.unsubscribe();
        userSubRef.current = client.subscribe(`/topic/user/${user.id}/sessions`, (message: IMessage) => {
          const event = JSON.parse(message.body) as SessionEvent;
          setLastUserEvent(event);
        });
      },
      onDisconnect: () => setConnected(false),
      onWebSocketClose: () => setConnected(false),
      onStompError: () => setConnected(false),
    });

    clientRef.current = client;
    client.activate();

    return () => {
      userSubRef.current?.unsubscribe();
      userSubRef.current = null;
      void client.deactivate();
      clientRef.current = null;
      setConnected(false);
    };
  }, [accessToken, user]);

  const subscribeSession = useCallback((workoutId: string, handler: EventHandler) => {
    const client = clientRef.current;
    if (!client || !client.connected) {
      return () => undefined;
    }
    const sub = client.subscribe(`/topic/session/${workoutId}`, (message: IMessage) => {
      handler(JSON.parse(message.body) as SessionEvent);
    });
    return () => sub.unsubscribe();
  }, []);

  const value = useMemo(
    () => ({ connected, subscribeSession, lastUserEvent }),
    [connected, subscribeSession, lastUserEvent],
  );

  return (
    <SessionSocketContext.Provider value={value}>{children}</SessionSocketContext.Provider>
  );
}

export function useSessionSocket() {
  const ctx = useContext(SessionSocketContext);
  if (!ctx) {
    throw new Error("useSessionSocket must be used within SessionSocketProvider");
  }
  return ctx;
}

import { useMemo, useState } from "react";
import { Bot, MessageSquarePlus, Pencil, Send, Trash2, User } from "lucide-react";
import { Button } from "./ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "./ui/card";
import { Input } from "./ui/input";
import { ScrollArea } from "./ui/scroll-area";
import { Textarea } from "./ui/textarea";

type ChatMessage = {
  id: number;
  role: "user" | "assistant";
  content: string;
};

type ChatSession = {
  id: number;
  title: string;
  updatedAt: string;
  messages: ChatMessage[];
};

const initialSessions: ChatSession[] = [
  {
    id: 1,
    title: "Recipe Schema Mapping",
    updatedAt: "2m ago",
    messages: [
      {
        id: 1,
        role: "assistant",
        content: "Hi! I can help map source fields to your RecipeKG ontology. Which data source should we start with?",
      },
      {
        id: 2,
        role: "user",
        content: "Let's start with Open Food Facts nutrition fields.",
      },
      {
        id: 3,
        role: "assistant",
        content: "Great choice. I suggest mapping energy-kcal to nutrition.calories and sodium_100g to nutrition.sodium.",
      },
    ],
  },
  {
    id: 2,
    title: "Entity Resolution Tips",
    updatedAt: "18m ago",
    messages: [
      {
        id: 1,
        role: "assistant",
        content: "Need help reducing duplicate entities? I can suggest normalization and fuzzy-matching strategies.",
      },
    ],
  },
  {
    id: 3,
    title: "Integration Checklist",
    updatedAt: "1h ago",
    messages: [
      {
        id: 1,
        role: "assistant",
        content: "I can generate a step-by-step integration checklist for USDA FoodData Central.",
      },
    ],
  },
];

export function Dashboard() {
  const [sessions, setSessions] = useState<ChatSession[]>(initialSessions);
  const [activeSessionId, setActiveSessionId] = useState<number>(initialSessions[0].id);
  const [draftMessage, setDraftMessage] = useState("");
  const [renameSessionId, setRenameSessionId] = useState<number | null>(null);
  const [renameValue, setRenameValue] = useState("");

  const activeSession = useMemo(
    () => sessions.filter((session) => session.id === activeSessionId)[0],
    [sessions, activeSessionId],
  );

  function createSession() {
    const nextId = sessions.reduce((maxId, session) => Math.max(maxId, session.id), 0) + 1;
    const newSession: ChatSession = {
      id: nextId,
      title: `New Chat ${nextId}`,
      updatedAt: "Just now",
      messages: [
        {
          id: 1,
          role: "assistant",
          content: "New session started. Ask me anything about your RecipeKG integrations.",
        },
      ],
    };

    setSessions((prev) => [newSession, ...prev]);
    setActiveSessionId(newSession.id);
    setRenameSessionId(null);
  }

  function startRename(session: ChatSession) {
    setRenameSessionId(session.id);
    setRenameValue(session.title);
  }

  function commitRename() {
    if (!renameSessionId) {
      return;
    }

    const nextTitle = renameValue.trim();
    if (!nextTitle) {
      setRenameSessionId(null);
      setRenameValue("");
      return;
    }

    setSessions((prev) =>
      prev.map((session) =>
        session.id === renameSessionId
          ? {
              ...session,
              title: nextTitle,
              updatedAt: "Just now",
            }
          : session,
      ),
    );
    setRenameSessionId(null);
    setRenameValue("");
  }

  function cancelRename() {
    setRenameSessionId(null);
    setRenameValue("");
  }

  function deleteSession(id: number) {
    const nextSessions = sessions.filter((session) => session.id !== id);
    if (nextSessions.length === 0) {
      return;
    }

    setSessions(nextSessions);
    if (activeSessionId === id) {
      setActiveSessionId(nextSessions[0].id);
    }

    if (renameSessionId === id) {
      cancelRename();
    }
  }

  function sendMessage() {
    if (!activeSession || !draftMessage.trim()) {
      return;
    }

    const userText = draftMessage.trim();
    setDraftMessage("");

    setSessions((prev) =>
      prev.map((session) => {
        if (session.id !== activeSession.id) {
          return session;
        }

        const nextMessageId = session.messages.reduce((maxId, message) => Math.max(maxId, message.id), 0) + 1;
        const userMessage: ChatMessage = {
          id: nextMessageId,
          role: "user",
          content: userText,
        };

        const assistantMessage: ChatMessage = {
          id: nextMessageId + 1,
          role: "assistant",
          content: `Got it. I can help with: "${userText}". Would you like a mapping suggestion, validation rules, or a transformation example?`,
        };

        return {
          ...session,
          updatedAt: "Just now",
          messages: [...session.messages, userMessage, assistantMessage],
        };
      }),
    );
  }

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-2xl font-semibold text-gray-900">AI Bot</h2>
        <p className="mt-1 text-gray-600">Use the assistant to chat, map fields, and guide RecipeKG integration tasks.</p>
      </div>

      <div className="grid min-h-[680px] grid-cols-1 gap-6 lg:grid-cols-[320px_1fr]">
        <Card className="flex min-h-[680px] flex-col overflow-hidden">
          <CardHeader className="flex flex-row items-center justify-between space-y-0 border-b">
            <CardTitle className="text-base">Chat Sessions</CardTitle>
            <Button size="sm" onClick={createSession}>
              <MessageSquarePlus className="mr-2 h-4 w-4" />
              New
            </Button>
          </CardHeader>
          <ScrollArea className="flex-1">
            <CardContent className="space-y-3 p-3">
              {sessions.map((session) => {
                const isActive = session.id === activeSessionId;
                const isRenaming = session.id === renameSessionId;

                return (
                  <div
                    key={session.id}
                    className={`rounded-lg border p-3 transition ${
                      isActive ? "border-blue-300 bg-blue-50" : "border-gray-200 bg-white hover:border-gray-300"
                    }`}
                  >
                    {isRenaming ? (
                      <div className="space-y-2">
                        <Input
                          value={renameValue}
                          onChange={(event) => setRenameValue(event.target.value)}
                          onKeyDown={(event) => {
                            if (event.key === "Enter") {
                              commitRename();
                            }
                            if (event.key === "Escape") {
                              cancelRename();
                            }
                          }}
                          autoFocus
                        />
                        <div className="flex gap-2">
                          <Button size="sm" className="flex-1" onClick={commitRename}>
                            Save
                          </Button>
                          <Button size="sm" variant="outline" className="flex-1" onClick={cancelRename}>
                            Cancel
                          </Button>
                        </div>
                      </div>
                    ) : (
                      <>
                        <button
                          type="button"
                          onClick={() => setActiveSessionId(session.id)}
                          className="w-full text-left"
                        >
                          <p className="truncate text-sm font-medium text-gray-900">{session.title}</p>
                          <p className="mt-1 text-xs text-gray-500">Updated {session.updatedAt}</p>
                        </button>
                        <div className="mt-3 flex gap-2">
                          <Button
                            size="sm"
                            variant="outline"
                            className="flex-1"
                            onClick={() => startRename(session)}
                          >
                            <Pencil className="mr-2 h-3.5 w-3.5" />
                            Rename
                          </Button>
                          <Button
                            size="sm"
                            variant="outline"
                            className="flex-1 text-red-600 hover:text-red-700"
                            onClick={() => deleteSession(session.id)}
                            disabled={sessions.length === 1}
                          >
                            <Trash2 className="mr-2 h-3.5 w-3.5" />
                            Delete
                          </Button>
                        </div>
                      </>
                    )}
                  </div>
                );
              })}
            </CardContent>
          </ScrollArea>
        </Card>

        <Card className="flex min-h-[680px] flex-col overflow-hidden">
          <CardHeader className="border-b">
            <CardTitle className="text-base">{activeSession?.title ?? "No Active Session"}</CardTitle>
          </CardHeader>

          <ScrollArea className="flex-1">
            <CardContent className="space-y-4 p-4">
              {activeSession?.messages.map((message) => {
                const isAssistant = message.role === "assistant";
                return (
                  <div key={message.id} className={`flex ${isAssistant ? "justify-start" : "justify-end"}`}>
                    <div
                      className={`max-w-[80%] rounded-xl px-4 py-3 text-sm ${
                        isAssistant ? "bg-gray-100 text-gray-900" : "bg-blue-600 text-white"
                      }`}
                    >
                      <div className="mb-1 flex items-center gap-2 text-xs opacity-75">
                        {isAssistant ? <Bot className="h-3.5 w-3.5" /> : <User className="h-3.5 w-3.5" />}
                        <span>{isAssistant ? "AI Bot" : "You"}</span>
                      </div>
                      <p>{message.content}</p>
                    </div>
                  </div>
                );
              })}
            </CardContent>
          </ScrollArea>

          <div className="border-t p-4">
            <div className="flex items-end gap-3">
              <Textarea
                value={draftMessage}
                onChange={(event) => setDraftMessage(event.target.value)}
                placeholder="Send a message to the AI bot..."
                className="min-h-[90px]"
                onKeyDown={(event) => {
                  if (event.key === "Enter" && !event.shiftKey) {
                    event.preventDefault();
                    sendMessage();
                  }
                }}
              />
              <Button onClick={sendMessage} className="h-10">
                <Send className="mr-2 h-4 w-4" />
                Send
              </Button>
            </div>
          </div>
        </Card>
      </div>
    </div>
  );
}

import {
  AssistantRuntimeProvider,
  AttachmentPrimitive,
  ComposerPrimitive,
  useAui,
  useAuiEvent,
  useExternalStoreRuntime,
} from "@assistant-ui/react";
import { createContext, useContext, useEffect, useMemo, useRef, useState } from "react";
import { createRoot } from "react-dom/client";
import Markdown from "react-markdown";
import "./style.css";

const providerConfigured = document.getElementById("root").dataset.providerConfigured === "true";
const providerSetupAllowed = document.getElementById("root").dataset.providerSetupAllowed === "true";

const ZalavaChatContext = createContext(null);
const SELECTED_CONVERSATION_KEY = "zalava.chat.selectedConversation";
function rememberedConversation() { try { return sessionStorage.getItem(SELECTED_CONVERSATION_KEY); } catch { return null; } }
function rememberConversation(id) { try { sessionStorage.setItem(SELECTED_CONVERSATION_KEY, id); } catch { /* Storage is optional; ownership is checked by Zalava. */ } }
function conversationDate(value) {
  if (!value) return "Date unavailable";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? "Date unavailable" : new Intl.DateTimeFormat(undefined, { dateStyle: "medium", timeStyle: "short" }).format(date);
}
const MAX_ATTACHMENT_BYTES = 5 * 1024 * 1024;
const ACCEPT =
  ".txt,.text,.md,.markdown,.html,.htm,.pdf,.docx," +
  "text/plain,text/markdown,text/html,application/pdf," +
  "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

const CONTENT_TYPES = {
  txt: "text/plain",
  text: "text/plain",
  md: "text/markdown",
  markdown: "text/markdown",
  html: "text/html",
  htm: "text/html",
  pdf: "application/pdf",
  docx: "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
};

function messageText(message) {
  return message.content?.find((part) => part.type === "text")?.text ?? "";
}

function contentTypeFor(file) {
  if (file.type && Object.values(CONTENT_TYPES).includes(file.type)) return file.type;
  const extension = file.name?.split(".").pop()?.toLowerCase();
  return CONTENT_TYPES[extension] ?? null;
}

function toBase64(buffer) {
  const bytes = new Uint8Array(buffer);
  let binary = "";
  const chunk = 0x8000;
  for (let index = 0; index < bytes.length; index += chunk) {
    binary += String.fromCharCode.apply(null, bytes.subarray(index, index + chunk));
  }
  return btoa(binary);
}

function attachmentErrorText(payload) {
  const event = payload?.payload ?? payload;
  if (event?.reason === "not-accepted") {
    return `Unsupported attachment type: ${event.contentType || "unknown"}`;
  }
  return event?.message ?? "Zalava could not add that attachment";
}

function AttachmentErrorReporter({ onError }) {
  useAuiEvent({ scope: "*", event: "composer.attachmentAddError" }, (payload) => {
    onError(attachmentErrorText(payload));
  });
  return null;
}

function ZalavaRuntime({ children }) {
  const [messages, setMessages] = useState([]);
  const [memoryReviews, setMemoryReviews] = useState([]);
  const [memoryDecision, setMemoryDecision] = useState(null);
  const [memoryFeedback, setMemoryFeedback] = useState("");
  const [documentProcessing, setDocumentProcessing] = useState(null);
  const [conversationId, setConversationId] = useState(null);
  const [conversationIds, setConversationIds] = useState([]);
  const [conversationSummaries, setConversationSummaries] = useState([]);
  const ownedConversationIds = useRef([]);
  const [jobs, setJobs] = useState({});
  const [approvals, setApprovals] = useState({});
  const [imports, setImports] = useState([]);
  const [uploadedFiles, setUploadedFiles] = useState([]);
  const [pendingImports, setPendingImports] = useState([]);
  const [uploadError, setUploadError] = useState(null);
  const [status, setStatus] = useState("Connecting");
  const [connectionAttempt, setConnectionAttempt] = useState(0);
  const [conversationAction, setConversationAction] = useState("");
  const [conversationNotice, setConversationNotice] = useState("");
  const conversationActionRef = useRef("");
  const [origin, setOrigin] = useState("web");
  const [canSend, setCanSend] = useState(false);
  const [canContinue, setCanContinue] = useState(false);
  const [continuing, setContinuing] = useState(false);
  const [commandError, setCommandError] = useState(null);
  const selectedConversation = useRef(null);
  const socket = useRef(null);
  const pendingAttachments = useRef(new Map());
  const sendCommandRef = useRef(() => false);

  useEffect(() => {
    let active = true;
    const finishAction = (event) => {
      const action = conversationActionRef.current;
      if (!action || (action === "rename" ? event !== "list" : event !== "snapshot")) return;
      conversationActionRef.current = "";
      setConversationAction("");
      setConversationNotice(action === "rename" ? "Conversation name saved." : action === "create" ? "Conversation created." : "Conversation loaded.");
    };
    const protocol = window.location.protocol === "https:" ? "wss" : "ws";
    const connection = new WebSocket(`${protocol}://${window.location.host}/ws/ui/chat`);
    socket.current = connection;
    connection.onopen = () => { if (active) setStatus("Connected"); };
    connection.onclose = () => {
      if (!active) return;
      setStatus("Disconnected");
      setConversationAction(""); conversationActionRef.current = "";
      setContinuing(false);
      setPendingImports([]);
      pendingAttachments.current.forEach(pending => pending.reject(new Error("The connection was interrupted. Reconnect and try again.")));
      pendingAttachments.current.clear();
      setMessages(current => current.map(message => message.streaming ? { ...message, streaming: false, failure: true, content: message.content || "The connection was interrupted. Reconnect before trying again." } : message));
    };
    connection.onerror = () => { if (active) setStatus("Connection error"); };
    connection.onmessage = ({ data }) => {
      if (!active) return;
      const event = JSON.parse(data);
      if (event.type === "conversation.list") {
        setConversationIds(event.conversationIds);
        ownedConversationIds.current = event.conversationIds;
        setConversationSummaries(event.conversations ?? []);
        finishAction("list");
      } else if (event.type === "conversation.snapshot") {
        const remembered = rememberedConversation();
        if (!selectedConversation.current && remembered && remembered !== event.conversationId && ownedConversationIds.current.includes(remembered)) {
          selectedConversation.current = remembered;
          connection.send(JSON.stringify({ protocol: "zalava.ui/v1", type: "chat.select", conversationId: remembered }));
          return;
        }
        selectedConversation.current = event.conversationId;
        rememberConversation(event.conversationId);
        setConversationId(event.conversationId);
        finishAction("snapshot");
        setOrigin(event.channelId ?? "web");
        setCanSend(event.canSend === true);
        setCanContinue(event.canContinue === true);
        setContinuing(false);
        setCommandError(null);
        setMessages(event.messages.map((message) => ({ role: message.role, content: message.text, attachmentNames: message.attachmentNames ?? [] })));
      } else if (event.type === "document.processing") {
        setDocumentProcessing(event.available);
      } else if (event.type === "memory.reviews") {
        setMemoryReviews(event.reviews);
      } else if (event.type === "chat.delta") {
        if (event.conversationId !== selectedConversation.current) return;
        setMessages((current) => {
          const last = current.at(-1);
          if (last?.role === "assistant" && last.streaming) {
            return [...current.slice(0, -1), { ...last, content: last.content + event.text }];
          }
          return [...current, { role: "assistant", content: event.text, streaming: true }];
        });
      } else if (event.type === "chat.completed") {
        if (event.conversationId !== selectedConversation.current) return;
        setMessages((current) => [
          ...current.filter((message) => !message.streaming),
          { role: "assistant", content: event.text, jobIds: event.jobIds },
        ]);
      } else if (event.type === "job.updated") {
        setApprovals((current) => Object.fromEntries(Object.entries(current).map(([id, entry]) =>
          [id, entry.jobId === event.jobId && entry.submitting ? { ...entry, pending: false, submitting: false } : entry])));
        setJobs((current) => ({ ...current, [event.jobId]: { status: event.status, summary: event.summary } }));
      } else if (event.type === "permission.requested") {
        setApprovals((current) => ({
          ...current,
          [event.requestId]: { jobId: event.jobId, summary: event.summary, pending: true },
        }));
      } else if (event.type === "attachment.available") {
        if (event.attachmentId) {
          setUploadedFiles((current) => [...current.filter((entry) => entry.attachmentId !== event.attachmentId), event]);
          setPendingImports((current) => current.filter((id) => id !== event.attachmentId));
        }
        setUploadError(null);
        if (event.intent === "knowledge-import") {
          setImports((current) =>
            current.some((entry) => entry.sourceId === event.sourceId) ? current : [...current, event],
          );
        } else {
          const pending = pendingAttachments.current.get(event.name);
          if (pending) {
            pendingAttachments.current.delete(event.name);
            pending.resolve({
              id: event.attachmentId,
              type: "document",
              name: event.name,
              contentType: event.contentType,
              file: pending.file,
              status: { type: "requires-action", reason: "composer-send" },
              content: [],
            });
          }
        }
      } else if (event.type === "attachment.removed") {
        setUploadedFiles((current) => current.filter((entry) => entry.attachmentId !== event.attachmentId));
      } else if (event.type === "failure") {
        setPendingImports([]);
        setConversationAction(""); conversationActionRef.current = "";
        setContinuing(false);
        if (event.operation !== "chat.send") setCommandError(event.message);
        setApprovals((current) => Object.fromEntries(Object.entries(current).map(([id, entry]) =>
          [id, { ...entry, submitting: false }])));
        pendingAttachments.current.forEach((pending) => pending.reject(new Error(event.message)));
        pendingAttachments.current.clear();
        if (event.operation === "chat.send") setMessages((current) => [
          ...current.filter((message) => !message.streaming),
          { role: "assistant", content: event.message, failure: true },
        ]);
      }
    };
    return () => { active = false; connection.close(); };
  }, [connectionAttempt]);

  useEffect(() => {
    if (!conversationAction) return;
    const timeout = setTimeout(() => {
      conversationActionRef.current = "";
      setConversationAction("");
      setCommandError("Zalava did not confirm the conversation change. Reconnect to check its result before trying again.");
    }, 15000);
    return () => clearTimeout(timeout);
  }, [conversationAction]);

  const sendCommand = (command) => {
    if (socket.current?.readyState === WebSocket.OPEN) {
      socket.current.send(JSON.stringify({ protocol: "zalava.ui/v1", ...command }));
      return true;
    }
    return false;
  };
  sendCommandRef.current = sendCommand;

  const changeConversation = (action, command, label) => {
    if (conversationActionRef.current) return;
    setCommandError(null); setConversationNotice("");
    conversationActionRef.current = action;
    setConversationAction(label);
    if (!sendCommand(command)) {
      conversationActionRef.current = ""; setConversationAction("");
      setCommandError("Zalava is disconnected. Reconnect before changing the conversation.");
    }
  };

  const attachmentAdapter = useMemo(
    () => ({
      accept: ACCEPT,
      add: async ({ file }) => {
        if (!providerConfigured) throw new Error("Configure a provider before attaching files to Chat.");
        setUploadError(null);
        const contentType = contentTypeFor(file);
        if (!contentType) throw new Error(`Unsupported attachment type: ${file.name}`);
        if (file.size > MAX_ATTACHMENT_BYTES) {
          throw new Error(`${file.name} exceeds the 5 MiB attachment limit`);
        }
        const content = toBase64(await file.arrayBuffer());
        return await new Promise((resolve, reject) => {
          pendingAttachments.current.set(file.name, { resolve, reject, file });
          if (
            !sendCommandRef.current({
              type: "attachment.put",
              intent: "task-only",
              name: file.name,
              contentType,
              content,
            })
          ) {
            pendingAttachments.current.delete(file.name);
        reject(new Error("Zalava is not connected"));
          }
        });
      },
      send: async (attachment) => ({
        id: attachment.id,
        type: attachment.type,
        name: attachment.name,
        contentType: attachment.contentType,
        file: attachment.file,
        status: { type: "complete" },
        content: [
          {
            type: "file",
            filename: attachment.name,
            data: attachment.id,
            mimeType: attachment.contentType ?? "application/octet-stream",
            sourceType: "id",
          },
        ],
      }),
      remove: async (attachment) => {
        sendCommandRef.current({ type: "attachment.delete", attachmentId: attachment.id });
      },
    }),
    [],
  );

  const saveUploadedFile = (attachmentId) => {
    if (pendingImports.includes(attachmentId)) return;
    setUploadError(null);
    setPendingImports((current) => [...current, attachmentId]);
    if (!sendCommand({ type: "attachment.import", attachmentId })) {
      setPendingImports((current) => current.filter((id) => id !== attachmentId));
      setUploadError("Zalava is not connected");
    }
  };

  const onNew = async (message) => {
    const text = messageText(message);
    if (!providerConfigured || !text.trim() || socket.current?.readyState !== WebSocket.OPEN || !conversationId || !canSend || continuing || conversationAction) return;
    const attachments = message.attachments ?? [];
    const attachmentIds = attachments
      .map((attachment) => attachment.content?.find((part) => part.type === "file")?.data ?? attachment.id)
      .filter(Boolean);
    const attachmentNames = attachments.map((attachment) => attachment.name);
    setMessages((current) => [
      ...current,
      { role: "user", content: text.trim(), attachmentNames },
      { role: "assistant", content: "", streaming: true },
    ]);
    sendCommand({ type: "chat.send", conversationId, message: text.trim(), attachmentIds });
  };

  const runtime = useExternalStoreRuntime({
    messages,
    isRunning: messages.at(-1)?.streaming === true,
    isSendDisabled: !providerConfigured || status !== "Connected" || !conversationId || !canSend || continuing || Boolean(conversationAction),
    convertMessage: (message) => ({
      role: message.role,
      content: [{ type: "text", text: message.content }],
    }),
    adapters: { attachments: attachmentAdapter },
    onNew,
  });

  const value = useMemo(
    () => ({
      messages,
      memoryReviews, memoryDecision, memoryFeedback,
      decideMemory: async (proposalId, decision) => {
        if (memoryDecision) return;
        setMemoryDecision(proposalId); setMemoryFeedback("");
        try {
          const token = document.cookie.split('; ').find(value => value.startsWith('XSRF-TOKEN='))?.slice('XSRF-TOKEN='.length);
          const response = await fetch(`/api/memory-proposals/${encodeURIComponent(proposalId)}/${decision}`, {
            method: 'POST', headers: token ? { 'X-XSRF-TOKEN': decodeURIComponent(token) } : {},
          });
          if (!response.ok) throw new Error('Memory decision could not be saved. Refresh proposals and try again.');
          setMemoryReviews(reviews => reviews.filter(review => review.proposalId !== proposalId));
          setMemoryFeedback(decision === 'approve' ? 'Memory approved and stored.' : 'Proposal rejected.');
        } catch (error) { setMemoryFeedback(error.message); }
        finally { setMemoryDecision(null); }
      },
      documentProcessing,
      origin, canSend, canContinue, continuing, commandError,
      status,
      conversationAction, conversationNotice,
      reconnect: () => {
        if (status === "Connecting") return;
        selectedConversation.current = null;
        setConversationId(null);
        setStatus("Connecting"); setCommandError(null);
        setConversationNotice("");
        setConnectionAttempt(attempt => attempt + 1);
      },
      conversationId,
      conversationIds,
      conversationSummaries,
      jobs,
      approvals: Object.entries(approvals).filter(([, approval]) => approval.pending),
      imports,
      uploadError,
      saveUploadedFile,
      uploadedFiles,
      pendingImports,
      decideApproval: (jobId, requestId, decision) => {
        if (sendCommand({ type: "approval.decide", jobId, requestId, decision })) {
          setApprovals((current) => ({ ...current, [requestId]: { ...current[requestId], submitting: true } }));
        } else setCommandError("Zalava is not connected");
      },
      continueConversation: () => {
        setCommandError(null);
        if (sendCommand({ type: "chat.continue", conversationId, destination: "web" })) setContinuing(true);
        else setCommandError("Zalava is not connected");
      },
      selectConversation: (id) => changeConversation("select", { type: "chat.select", conversationId: id }, "Loading conversation…"),
      createConversation: () => changeConversation("create", { type: "chat.create" }, "Creating conversation…"),
      renameConversation: (title) => changeConversation("rename", { type: "chat.rename", conversationId, title }, "Saving conversation name…"),
    }),
    [messages, memoryReviews, memoryDecision, memoryFeedback, documentProcessing, status, conversationId, conversationIds, conversationSummaries, jobs, approvals, imports, uploadedFiles, pendingImports, uploadError, origin, canSend, canContinue, continuing, commandError, conversationAction, conversationNotice],
  );

  return (
    <AssistantRuntimeProvider runtime={runtime}>
      <ZalavaChatContext.Provider value={value}>
        <AttachmentErrorReporter onError={setUploadError} />
        {children}
      </ZalavaChatContext.Provider>
    </AssistantRuntimeProvider>
  );
}

function Chat() {
  const [fileOptionsOpen, setFileOptionsOpen] = useState(false);
  const fileOptionsRef = useRef(null);
  useEffect(() => {
    if (!fileOptionsOpen) return;
    const closeOutside = (event) => {
      if (!fileOptionsRef.current?.contains(event.target)) setFileOptionsOpen(false);
    };
    const closeOnEscape = (event) => {
      if (event.key === "Escape") {
        setFileOptionsOpen(false);
        fileOptionsRef.current?.querySelector(".composer-options-toggle")?.focus();
      }
    };
    document.addEventListener("pointerdown", closeOutside);
    document.addEventListener("keydown", closeOnEscape);
    return () => {
      document.removeEventListener("pointerdown", closeOutside);
      document.removeEventListener("keydown", closeOnEscape);
    };
  }, [fileOptionsOpen]);
  const [inspectorOpen, setInspectorOpen] = useState(false);
  const [inspectorTab, setInspectorTab] = useState("Run");
  const {
    origin, canSend, canContinue, continuing, commandError, continueConversation,
    conversationAction, conversationNotice, reconnect,
    messages,
    memoryReviews, memoryDecision, memoryFeedback, decideMemory,
    documentProcessing,
    status,
    conversationId,
    conversationIds,
    conversationSummaries,
    jobs,
    approvals,
    imports,
    uploadError,
    saveUploadedFile,
    uploadedFiles,
    pendingImports,
    decideApproval,
    selectConversation,
    createConversation,
    renameConversation,
  } = useContext(ZalavaChatContext);
  const [evidence, setEvidence] = useState({ runs: [], tools: [] });
  const [evidenceError, setEvidenceError] = useState("");
  const [evidenceAttempt, setEvidenceAttempt] = useState(0);
  const selectedSummary = conversationSummaries.find((entry) => entry.id === conversationId);
  const [conversationName, setConversationName] = useState("");
  useEffect(() => setConversationName(selectedSummary?.title ?? "New conversation"), [conversationId, selectedSummary?.title]);
  const aui = useAui();
  const historyIndex = useRef(-1);
  const preservedDraft = useRef("");
  const promptHistory = messages
    .filter((message) => message.role === "user")
    .map((message) => message.content);
  const recallPrompt = (event) => {
    if (event.key !== "ArrowUp" && event.key !== "ArrowDown") return;
    if (!event.altKey || event.ctrlKey || event.metaKey || event.shiftKey || promptHistory.length === 0) return;
    event.preventDefault();
    if (event.key === "ArrowUp") {
      const next = Math.min(historyIndex.current + 1, promptHistory.length - 1);
      if (historyIndex.current === -1) preservedDraft.current = aui.composer.getState().text;
      historyIndex.current = next;
      aui.composer.setText(promptHistory[promptHistory.length - 1 - next]);
    } else if (historyIndex.current > 0) {
      const next = historyIndex.current - 1;
      historyIndex.current = next;
      aui.composer.setText(promptHistory[promptHistory.length - 1 - next]);
    } else if (historyIndex.current === 0) {
      historyIndex.current = -1;
      aui.composer.setText(preservedDraft.current);
    }
  };

  const messagesRef = useRef(null);
  const followLatest = useRef(true);
  useEffect(() => {
    followLatest.current = true;
    const panel = messagesRef.current;
    if (panel) panel.scrollTop = panel.scrollHeight;
  }, [conversationId]);
  useEffect(() => {
    const panel = messagesRef.current;
    if (!panel) return;
    // A new authored question resumes following; scrolling up while streaming pauses it.
    if (messages.at(-2)?.role === 'user' && messages.at(-1)?.streaming && !messages.at(-1)?.content) followLatest.current = true;
    if (followLatest.current) panel.scrollTop = panel.scrollHeight;
  }, [messages]);
  useEffect(() => {
    if (inspectorOpen && window.matchMedia('(max-width: 1023px)').matches) {
      document.getElementById('chat-inspector')?.scrollIntoView({ block: 'start' });
      document.getElementById(`inspector-tab-${inspectorTab}`)?.focus({ preventScroll: true });
    }
  }, [inspectorOpen]);
  const pending = messages.some((message) => message.streaming);
  const failure = messages.findLast((message) => message.failure);
  useEffect(() => {
    if (!conversationId || pending) return;
    const controller = new AbortController();
    setEvidence({ runs: [], tools: [] }); setEvidenceError("");
    fetch(`/chat/${encodeURIComponent(conversationId)}/evidence`, { signal: controller.signal })
      .then(response => { if (!response.ok) throw new Error(); return response.json(); })
      .then(setEvidence).catch(error => { if (error.name !== 'AbortError') setEvidenceError('Execution details could not be loaded.'); });
    return () => controller.abort();
  }, [conversationId, pending, evidenceAttempt]);


  return (
    <main className="zalava-chat" aria-label="Zalava conversation">
      <header>
        <div>
          <h1>Chat</h1>
        </div>
        <output className="zalava-status" data-state={status === "Connected" ? "success" : status === "Connecting" ? "connecting" : "error"} aria-label="Chat connection" aria-live="polite">{status}</output>
      </header>
      {(status === "Disconnected" || status === "Connection error") && <section className="connection-recovery" role="alert">
        <p>Chat is disconnected. Your unsent message is kept below. Reconnect to load the latest conversation before sending.</p>
        <button className="zalava-button" type="button" onClick={reconnect}>Reconnect</button>
      </section>}
      {conversationAction && <p className="chat-action-status" role="status">{conversationAction}</p>}
      {!conversationAction && conversationNotice && <p className="chat-action-status" role="status">{conversationNotice}</p>}
      {!providerConfigured && <section id="model-setup" className="provider-notice" aria-label="Model setup" role="status">
        <strong>Model not configured</strong>
        <p>Chat is disabled until a model provider is configured. The connection alone does not mean a model is ready.</p>
        {providerSetupAllowed ? <a className="zalava-button" href="/settings?section=provider">Set up a provider</a> : <p>Ask your workspace administrator to configure a provider.</p>}
      </section>}
      {providerConfigured && !conversationId && status === "Connected" && <p role="status">Loading your conversations…</p>}
      <div className="conversation-toolbar">
      <nav className="conversations" aria-label="Conversations">
        <select className="zalava-field" value={conversationId ?? ""} onChange={(event) => selectConversation(event.target.value)} aria-label="Select conversation" disabled={!providerConfigured || !conversationId || pending || continuing || Boolean(conversationAction) || status !== "Connected"}>
          {!conversationId && <option value="">Start a conversation</option>}
          {conversationIds.map((id) => {
            const summary = conversationSummaries.find((entry) => entry.id === id);
            return <option key={id} value={id}>{summary?.title ?? "Saved conversation"} · {conversationDate(summary?.updatedAt ?? summary?.createdAt)}</option>;
          })}
        </select>
        <button className="zalava-button" type="button" aria-label="New conversation" onClick={createConversation} disabled={!providerConfigured || !conversationId || status !== "Connected" || pending || continuing || Boolean(conversationAction)}>New chat</button>
      </nav>
      {conversationId && <details className="conversation-options">
        <summary aria-label="Conversation options">Options</summary>
        <form onSubmit={(event) => { event.preventDefault(); if (conversationName.trim()) renameConversation(conversationName.trim()); }}>
          <label htmlFor="conversation-name">Conversation name</label>
          <input id="conversation-name" className="zalava-field" value={conversationName} maxLength={120} onChange={(event) => setConversationName(event.target.value)} disabled={pending || Boolean(conversationAction) || status !== "Connected"} />
          <button className="zalava-button zalava-button--secondary" type="submit" disabled={!conversationName.trim() || pending || Boolean(conversationAction) || status !== "Connected"}>Save name</button>
        </form>
      </details>}
      <button className="zalava-button zalava-button--secondary inspector-toggle" type="button" aria-label="Workspace details" aria-expanded={inspectorOpen} aria-controls="chat-inspector" onClick={() => setInspectorOpen((open) => !open)}>{inspectorOpen ? "Hide details" : "Details"}</button>
      </div>
      {memoryReviews.length > 0 && <p className="memory-review-shortcut"><button className="zalava-button zalava-button--secondary" type="button" onClick={() => { setInspectorOpen(true); setInspectorTab("Context"); }}>Review {memoryReviews.length} memory proposal{memoryReviews.length === 1 ? "" : "s"}</button></p>}
      {approvals.length > 0 && <div className="permission-shortcut" role="status">
        <span>{approvals.length} pending permission{approvals.length === 1 ? "" : "s"}</span>
        <button className="zalava-button" type="button" onClick={() => { setInspectorOpen(true); setInspectorTab("Tools"); }}>Review permissions</button>
      </div>}
      <div className={`chat-workspace ${inspectorOpen ? "inspector-open" : ""}`}>
      <div className="chat-primary">
      {origin !== "web" && <section className="channel-history" aria-label="Channel conversation">
        <h2>{origin} history</h2>
        <p>This history is read-only. Continue in a separate private web conversation to send a message.</p>
        {canContinue ? <>
          <label htmlFor="continuation-destination">Continue in</label>
          <select id="continuation-destination" className="zalava-field" disabled={!providerConfigured || continuing}><option value="web">Private web chat</option></select>
          <button className="zalava-button" type="button" onClick={continueConversation} disabled={!providerConfigured || continuing || status !== "Connected"}>{continuing ? "Continuing…" : "Continue conversation"}</button>
        </> : <p>Continuation is unavailable for this channel identity or destination.</p>}
      </section>}
      {commandError && commandError !== failure?.content && <section className="connection-recovery" role="alert">
        <p>{commandError}</p>
        <button className="zalava-button" type="button" onClick={reconnect} disabled={status === "Connecting"}>Reconnect</button>
      </section>}
      <section ref={messagesRef} onScroll={(event) => { const el = event.currentTarget; followLatest.current = el.scrollHeight - el.scrollTop - el.clientHeight < 80; }} className="messages" aria-live="polite" aria-label="Conversation messages" aria-busy={pending}>
        {messages.length === 0 && <div className="chat-empty"><h2>What would you like to work on?</h2><p>{providerConfigured ? "Ask a question, explore your knowledge or start a task. Work and permissions will appear in workspace details." : "Set up a model to start your first conversation. Your workspace is ready for you to explore."}</p></div>}
        {messages.map((message, index) => (
          <article className={`message ${message.role} ${message.failure ? "failure" : ""}`} key={index}>
            <strong>{message.role === "user" ? "You" : "Zalava"}</strong>
            {message.streaming && !message.content && <p className="assistant-pending" role="status"><span className="pending-dots" aria-hidden="true"><i></i><i></i><i></i></span>Zalava is responding…</p>}
            <Markdown
              allowedElements={["p", "strong", "em", "code", "pre", "ul", "ol", "li", "blockquote", "a", "h1", "h2", "h3", "h4", "h5", "h6"]}
              components={{ a: ({ href, children }) => <a href={href} rel="noopener noreferrer">{children}</a> }}
            >
              {message.content}
            </Markdown>
            {message.attachmentNames?.map((name) => (
              <span className="attachment-chip" key={name}>{name}</span>
            ))}
            {message.jobIds?.map((jobId) => <a href={`/jobs/${jobId}`} key={jobId}>View task</a>)}
          </article>
        ))}
      </section>

      {failure && <p role="alert">{failure.content}</p>}
      {canSend && <ComposerPrimitive.Root data-testid="composer" className="composer">
        <fieldset className="composer-fields" disabled={!providerConfigured} aria-describedby={!providerConfigured ? "model-setup" : undefined}>
        <label htmlFor="message">Message Zalava</label>
        <p className="composer-help" id="composer-help">Alt+↑/↓ recalls sent messages.</p>
        <ComposerPrimitive.AttachmentDropzone data-testid="attachment-dropzone" className="composer-dropzone">
          <ComposerPrimitive.Attachments>
            {({ attachment }) => (
              <AttachmentPrimitive.Root className="attachment-chip">
                <span className="attachment-name" title={attachment.name}><AttachmentPrimitive.Name /></span>
                {uploadedFiles.find((file) => file.attachmentId === attachment.id)?.sourceId ? <a href={`/knowledge/${uploadedFiles.find((file) => file.attachmentId === attachment.id).sourceId}`}>Open in Knowledge</a> : <button type="button" className="zalava-button zalava-button--secondary" disabled={attachment.status.type === "running" || pendingImports.includes(attachment.id)} onClick={() => saveUploadedFile(attachment.id)}>{pendingImports.includes(attachment.id) ? "Saving…" : "Save to knowledge"}</button>}
                <AttachmentPrimitive.Remove className="zalava-button zalava-button--secondary" aria-label="Remove">Remove</AttachmentPrimitive.Remove>
              </AttachmentPrimitive.Root>
            )}
          </ComposerPrimitive.Attachments>
          <div className="attachments" aria-label="Attachments">
            <ComposerPrimitive.Input
              id="message"
              aria-describedby="composer-help"
              className="zalava-field"
              placeholder={providerConfigured ? "Message Zalava" : "Configure a provider to start chatting"}
              onKeyDown={recallPrompt}
              onChange={() => {
                historyIndex.current = -1;
              }}
            />
          </div>
        </ComposerPrimitive.AttachmentDropzone>
        <div className="composer-actions">
          <div className="composer-options" ref={fileOptionsRef}>
            <button className="zalava-button zalava-button--secondary composer-options-toggle" type="button" aria-label="File options" title="Attach files" aria-expanded={fileOptionsOpen} aria-controls="composer-file-options" onClick={() => setFileOptionsOpen((open) => !open)}>
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" aria-hidden="true"><path d="m8 12 7-7a3 3 0 0 1 4 4L9 19a5 5 0 0 1-7-7L13 1" /></svg>
            </button>
            {fileOptionsOpen && <div id="composer-file-options" className="composer-options-menu" role="group" aria-label="Attach files">
              <div className="document-processing-notice" role="status">
                {documentProcessing === null ? <p>Checking document-processing support…</p> : documentProcessing ? <p>An active document-processing module can read attached files. Supported formats depend on that module; unreadable files show an error. Up to 5 files, 5 MB each; large extracted documents may need smaller parts.</p> : <><strong>A document-processing module is needed to read files.</strong><p>Files can be stored now. Reading attachments and processing Knowledge imports need an installed, configured and active module.</p>{providerSetupAllowed ? <a href="/modules">Find a document-processing module</a> : <p>Ask your workspace administrator to install and start a document-processing module.</p>}</>}
              </div>
              <ComposerPrimitive.AddAttachment multiple aria-label="Attach files" className="zalava-button zalava-button--secondary composer-attach" onClick={() => setFileOptionsOpen(false)}>Attach files</ComposerPrimitive.AddAttachment>

            </div>}
          </div>
          <ComposerPrimitive.Send className="zalava-button composer-send">Send</ComposerPrimitive.Send>
        </div>
        {uploadedFiles.length > 0 && <details className="uploaded-file-options">
          <summary>Uploaded files</summary>
          <p>Save a file to your private Knowledge library without uploading it again. This keeps a separate copy; removing a Chat attachment does not delete the Knowledge source.</p>
          <ul>{uploadedFiles.map((file) => <li key={file.attachmentId}>
            <span>{file.name}</span>
            {file.sourceId ? <a href={`/knowledge/${file.sourceId}`}>Open in Knowledge</a> : <button className="zalava-button zalava-button--secondary" type="button" disabled={pendingImports.includes(file.attachmentId)} onClick={() => saveUploadedFile(file.attachmentId)}>{pendingImports.includes(file.attachmentId) ? "Saving…" : "Save to knowledge"}</button>}
          </li>)}</ul>
          {!documentProcessing && <p>Reading saved sources needs an active document-processing module. Files can be stored now.</p>}
        </details>}
        {imports.length > 0 && (
          <ul className="attachment-list" aria-label="Imported sources">
            {imports.map((entry) => (
              <li key={entry.sourceId ?? entry.attachmentId}>
                Saved to Knowledge: <a href={`/knowledge/${entry.sourceId}`}>{entry.name}</a>. {documentProcessing ? "Processing pending; open the source for its result." : "A document-processing module is needed to read it."}
              </li>
            ))}
          </ul>
        )}
        {uploadError && <p role="alert">{uploadError}</p>}
      </fieldset>
      </ComposerPrimitive.Root>}
      </div>
      <aside id="chat-inspector" className="chat-inspector" aria-label="Workspace inspector">
        <button className="zalava-button zalava-button--secondary inspector-close" type="button" onClick={() => { setInspectorOpen(false); document.querySelector(".inspector-toggle")?.focus(); }}>Close details</button>
        <div role="tablist" aria-label="Workspace details">
          {["Run", "Tools", "Context"].map((tab) => <button className="zalava-button zalava-button--secondary" type="button" key={tab} id={`inspector-tab-${tab}`} role="tab" tabIndex={inspectorTab === tab ? 0 : -1} aria-selected={inspectorTab === tab} onKeyDown={(event) => {
            const tabs = ["Run", "Tools", "Context"];
            let next;
            if (event.key === "ArrowRight") next = tabs[(tabs.indexOf(tab) + 1) % tabs.length];
            else if (event.key === "ArrowLeft") next = tabs[(tabs.indexOf(tab) + tabs.length - 1) % tabs.length];
            else if (event.key === "Home") next = tabs[0];
            else if (event.key === "End") next = tabs.at(-1);
            if (next) { event.preventDefault(); setInspectorTab(next); document.getElementById(`inspector-tab-${next}`).focus(); }
          }} aria-controls="inspector-panel" onClick={() => setInspectorTab(tab)}>{tab}</button>)}
        </div>
        <div id="inspector-panel" role="tabpanel" aria-labelledby={`inspector-tab-${inspectorTab}`}>
          {inspectorTab === "Run" && <>
            <h2>This conversation</h2>
            {evidenceError && <p role="alert">{evidenceError} <button className="zalava-button zalava-button--secondary" type="button" onClick={() => setEvidenceAttempt(value => value + 1)}>Retry details</button></p>}
            {evidence.runs.length === 0 && !evidenceError && <p>No recorded executions for this conversation.</p>}
            {evidence.runs.map(run => <details className="conversation-execution" key={run.id}>
              <summary>{run.status === 'SUCCEEDED' ? 'Completed' : 'Failed'} · {run.durationMillis} ms · {conversationDate(run.at)}</summary>
              <p>{run.selectedToolCount} tools available · {run.contextCharactersUsed} / {run.contextCharacterBudget} context characters</p>
              <p><strong>Question:</strong> {run.prompt}</p><p><strong>Result:</strong> {run.result}</p>
              {run.error && <p role="alert">{run.error}</p>}
              {evidence.tools.filter(tool => new Date(tool.at) >= new Date(run.at) && new Date(tool.at) <= new Date(run.completedAt)).map((tool, index) => <details key={index}><summary>{tool.name} · {tool.success ? 'Completed' : 'Failed'} · {tool.durationMillis} ms</summary><p>Provider: {tool.provider}</p>{tool.input && <><strong>Input</strong><pre>{tool.input}</pre></>}<strong>Result</strong><pre>{tool.result}</pre>{tool.error && <p>{tool.error}</p>}</details>)}
            </details>)}
            <h2>Recent work for your account</h2>
            <p>Tasks may belong to other conversations.</p>
            {Object.keys(jobs).length === 0 && <p>No recent tasks.</p>}
            {Object.entries(jobs).map(([jobId, job]) => <section className="execution" key={jobId} aria-label={`Execution ${jobId}`}>
              <strong>Execution {job.status.replaceAll("_", " ")}</strong><p>{job.summary}</p><a href={`/jobs/${jobId}`}>View task evidence</a>
            </section>)}
          </>}
          {inspectorTab === "Tools" && <>
            <h2>Tool calls</h2>
            {evidenceError && <p role="alert">{evidenceError} <button type="button" onClick={() => setEvidenceAttempt(value => value + 1)}>Retry details</button></p>}
            {evidence.tools.length === 0 && !evidenceError && <p>No recorded tool calls for this conversation.</p>}
            {evidence.tools.map((tool,index) => <details className="conversation-execution" key={index}><summary>{tool.name} · {tool.success ? 'Completed' : 'Failed'}</summary><p>{tool.provider} · {conversationDate(tool.at)} · {tool.durationMillis} ms</p>{tool.input && <><strong>Input</strong><pre>{tool.input}</pre></>}<strong>Result</strong><pre>{tool.result}</pre>{tool.error && <p role="alert">{tool.error}</p>}</details>)}
            <h2>Pending permissions</h2>
            {approvals.length === 0 && <p>No pending permissions.</p>}
            {approvals.map(([requestId, approval]) => <section className="approval" key={requestId} aria-label="Pending permission">
              <strong>Permission needed</strong><p>Zalava requests approval for {approval.summary}.</p>
              <button className="zalava-button" type="button" disabled={approval.submitting || status !== "Connected"} onClick={() => decideApproval(approval.jobId, requestId, "allow-once")}>Allow once</button>
              <button className="zalava-button zalava-button--danger" type="button" disabled={approval.submitting || status !== "Connected"} onClick={() => decideApproval(approval.jobId, requestId, "deny")}>Deny</button>
              {approval.submitting && <p role="status">Submitting decision…</p>}
            </section>)}
          </>}
          {inspectorTab === "Context" && <>
            <section className="memory-review-notice" aria-label="Memory review">
              <h2>Memory proposals</h2><p>Only approved suggestions are saved for recall.</p>
              {memoryReviews.length === 0 && <p>No pending proposals.</p>}
              {memoryReviews.map(review => <section key={review.proposalId} className="memory-review-entry">
                <p>{review.summary}</p><div className="memory-review-actions">
                  <button className="zalava-button" type="button" disabled={Boolean(memoryDecision)} onClick={() => decideMemory(review.proposalId, 'approve')}>Approve memory</button>
                  <button className="zalava-button zalava-button--secondary" type="button" disabled={Boolean(memoryDecision)} onClick={() => decideMemory(review.proposalId, 'reject')}>Reject</button>
                  <a href={review.reviewUrl}>Full proposal</a>
                </div>{memoryDecision === review.proposalId && <p role="status">Saving decision…</p>}
              </section>)}
              {memoryFeedback && <p role="status">{memoryFeedback}</p>}
            </section>

            <h2>Available knowledge sources</h2><p>These sources are available to your account. This list does not show what the model used.</p>
            {imports.length === 0 ? <p>No sources imported in this session.</p> : <ul>{imports.map((entry) => <li key={entry.sourceId}><a href={`/knowledge/${entry.sourceId}`}>{entry.name}</a></li>)}</ul>}
            <p>Composer attachments apply to the next message. Knowledge imports remain available separately.</p>
          </>}
        </div>
      </aside>
      </div>
    </main>
  );
}

createRoot(document.getElementById("root")).render(<ZalavaRuntime><Chat /></ZalavaRuntime>);

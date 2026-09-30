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

const SeaChatContext = createContext(null);
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

function SeaRuntime({ children }) {
  const [messages, setMessages] = useState([]);
  const [conversationId, setConversationId] = useState(null);
  const [conversationIds, setConversationIds] = useState([]);
  const [jobs, setJobs] = useState({});
  const [approvals, setApprovals] = useState({});
  const [imports, setImports] = useState([]);
  const [uploadError, setUploadError] = useState(null);
  const [status, setStatus] = useState("Connecting");
  const socket = useRef(null);
  const pendingAttachments = useRef(new Map());
  const sendCommandRef = useRef(() => false);

  useEffect(() => {
    const protocol = window.location.protocol === "https:" ? "wss" : "ws";
    const connection = new WebSocket(`${protocol}://${window.location.host}/ws/ui/chat`);
    socket.current = connection;
    connection.onopen = () => setStatus("Connected");
    connection.onclose = () => setStatus("Disconnected");
    connection.onerror = () => setStatus("Connection error");
    connection.onmessage = ({ data }) => {
      const event = JSON.parse(data);
      if (event.type === "conversation.list") {
        setConversationIds(event.conversationIds);
      } else if (event.type === "conversation.snapshot") {
        setConversationId(event.conversationId);
        setMessages(event.messages.map((message) => ({ role: message.role, content: message.text })));
      } else if (event.type === "chat.delta") {
        setMessages((current) => {
          const last = current.at(-1);
          if (last?.role === "assistant" && last.streaming) {
            return [...current.slice(0, -1), { ...last, content: last.content + event.text }];
          }
          return [...current, { role: "assistant", content: event.text, streaming: true }];
        });
      } else if (event.type === "chat.completed") {
        setMessages((current) => [
          ...current.filter((message) => !message.streaming),
          { role: "assistant", content: event.text, jobIds: event.jobIds },
        ]);
      } else if (event.type === "job.updated") {
        setJobs((current) => ({ ...current, [event.jobId]: { status: event.status, summary: event.summary } }));
      } else if (event.type === "permission.requested") {
        setApprovals((current) => ({
          ...current,
          [event.requestId]: { jobId: event.jobId, summary: event.summary, pending: true },
        }));
      } else if (event.type === "attachment.available") {
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
        setImports((current) => current.filter((entry) => entry.attachmentId !== event.attachmentId));
      } else if (event.type === "failure") {
        pendingAttachments.current.forEach((pending) => pending.reject(new Error(event.message)));
        pendingAttachments.current.clear();
        setMessages((current) => [
          ...current.filter((message) => !message.streaming),
          { role: "assistant", content: event.message, failure: true },
        ]);
      }
    };
    return () => connection.close();
  }, []);

  const sendCommand = (command) => {
    if (socket.current?.readyState === WebSocket.OPEN) {
      socket.current.send(JSON.stringify({ protocol: "sea.ui/v1", ...command }));
      return true;
    }
    return false;
  };
  sendCommandRef.current = sendCommand;

  const attachmentAdapter = useMemo(
    () => ({
      accept: ACCEPT,
      add: async ({ file }) => {
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

  const importFiles = (fileList) => {
    setUploadError(null);
    Array.from(fileList ?? []).forEach((file) => {
      const contentType = contentTypeFor(file);
      if (!contentType) {
        setUploadError(`Unsupported attachment type: ${file.name}`);
        return;
      }
      if (file.size > MAX_ATTACHMENT_BYTES) {
        setUploadError(`${file.name} exceeds the 5 MiB attachment limit`);
        return;
      }
      file.arrayBuffer().then((buffer) => {
        if (
          !sendCommandRef.current({
            type: "attachment.put",
            intent: "knowledge-import",
            name: file.name,
            contentType,
            content: toBase64(buffer),
          })
        ) {
      setUploadError("Zalava is not connected");
        }
      });
    });
  };

  const onNew = async (message) => {
    const text = messageText(message);
    if (!text.trim() || socket.current?.readyState !== WebSocket.OPEN || !conversationId) return;
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
    isSendDisabled: status !== "Connected" || !conversationId,
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
      status,
      conversationId,
      conversationIds,
      jobs,
      approvals: Object.entries(approvals).filter(([, approval]) => approval.pending),
      imports,
      uploadError,
      importFiles,
      decideApproval: (jobId, requestId, decision) => {
        setApprovals((current) => ({
          ...current,
          [requestId]: { ...current[requestId], pending: false },
        }));
        sendCommand({ type: "approval.decide", jobId, requestId, decision });
      },
      selectConversation: (id) => sendCommand({ type: "chat.select", conversationId: id }),
      createConversation: () => sendCommand({ type: "chat.create" }),
    }),
    [messages, status, conversationId, conversationIds, jobs, approvals, imports, uploadError],
  );

  return (
    <AssistantRuntimeProvider runtime={runtime}>
      <SeaChatContext.Provider value={value}>
        <AttachmentErrorReporter onError={setUploadError} />
        {children}
      </SeaChatContext.Provider>
    </AssistantRuntimeProvider>
  );
}

function Chat() {
  const {
    messages,
    status,
    conversationId,
    conversationIds,
    jobs,
    approvals,
    imports,
    uploadError,
    importFiles,
    decideApproval,
    selectConversation,
    createConversation,
  } = useContext(SeaChatContext);
  const aui = useAui();
  const historyIndex = useRef(-1);
  const preservedDraft = useRef("");
  const promptHistory = messages
    .filter((message) => message.role === "user")
    .map((message) => message.content);
  const recallPrompt = (event) => {
    if (event.key !== "ArrowUp" && event.key !== "ArrowDown") return;
    if (event.shiftKey || promptHistory.length === 0) return;
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

  const pending = messages.some((message) => message.streaming);
  const failure = messages.findLast((message) => message.failure);

  return (
    <main className="sea-chat" aria-label="Zalava conversation">
      <header>
        <div>
          <p className="eyebrow">Default workspace</p>
          <h1>Chat</h1>
          <p>Ask Zalava a question or start work that will be tracked as a job.</p>
        </div>
        <output className="sea-status" aria-live="polite">{status}</output>
      </header>
      <nav className="conversations" aria-label="Conversations">
        <select className="sea-field" value={conversationId ?? ""} onChange={(event) => selectConversation(event.target.value)} aria-label="Select conversation">
          {conversationIds.map((id) => <option key={id} value={id}>Conversation {id.slice(0, 8)}</option>)}
        </select>
        <button className="sea-button" type="button" onClick={createConversation} disabled={status !== "Connected"}>New conversation</button>
      </nav>
      <section className="messages" aria-live="polite" aria-label="Conversation messages" aria-busy={pending}>
        {messages.map((message, index) => (
          <article className={`message ${message.role} ${message.failure ? "failure" : ""}`} key={index}>
            <strong>{message.role === "user" ? "You" : "Zalava"}</strong>
            <Markdown
              allowedElements={["p", "strong", "em", "code", "pre", "ul", "ol", "li", "blockquote", "a"]}
              components={{ a: ({ href, children }) => <a href={href} rel="noopener noreferrer">{children}</a> }}
            >
              {message.content}
            </Markdown>
            {message.attachmentNames?.map((name) => (
              <span className="attachment-chip" key={name}>{name}</span>
            ))}
            {message.jobIds?.map((jobId) => <a href={`/jobs/${jobId}`} key={jobId}>View job</a>)}
          </article>
        ))}
      </section>

      {Object.entries(jobs).map(([jobId, job]) => (
        <section className="execution" key={jobId} aria-label={`Execution ${jobId}`}>
          <strong>Execution {job.status.replaceAll("_", " ")}</strong>
          <p>{job.summary}</p>
          <a href={`/jobs/${jobId}`}>View job evidence</a>
        </section>
      ))}

      {approvals.map(([requestId, approval]) => (
        <section className="approval" key={requestId} aria-label="Pending permission">
          <strong>Permission needed</strong>
          <p>Zalava requests approval for {approval.summary}.</p>
          <button className="sea-button" type="button" onClick={() => decideApproval(approval.jobId, requestId, "allow-once")}>Allow once</button>
          <button className="sea-button sea-button--danger" type="button" onClick={() => decideApproval(approval.jobId, requestId, "deny")}>Deny</button>
        </section>
      ))}
      {pending && <p role="status">Zalava is responding…</p>}
      {failure && <p role="alert">{failure.content}</p>}
      <ComposerPrimitive.Root data-testid="composer" className="composer">
        <label htmlFor="message">Message Zalava</label>
        <ComposerPrimitive.AttachmentDropzone data-testid="attachment-dropzone" className="composer-dropzone">
          <ComposerPrimitive.Attachments>
            {({ attachment }) => (
              <AttachmentPrimitive.Root className="attachment-chip">
                <AttachmentPrimitive.Name />
                <AttachmentPrimitive.Remove className="sea-button sea-button--secondary" aria-label="Remove">Remove</AttachmentPrimitive.Remove>
              </AttachmentPrimitive.Root>
            )}
          </ComposerPrimitive.Attachments>
          <div className="attachments" aria-label="Attachments">
            <ComposerPrimitive.Input
              id="message"
              className="sea-field"
              placeholder="Message Zalava"
              onKeyDown={recallPrompt}
              onChange={() => {
                historyIndex.current = -1;
              }}
            />
            <ComposerPrimitive.AddAttachment multiple aria-label="Attach files" className="sea-button sea-button--secondary composer-attach">
              Attach files
            </ComposerPrimitive.AddAttachment>
            <ComposerPrimitive.Send className="sea-button composer-send">Send</ComposerPrimitive.Send>
          </div>
        </ComposerPrimitive.AttachmentDropzone>
        <div className="attachments" aria-label="Knowledge import">
          <label htmlFor="knowledge-import-file">Import to knowledge</label>
          <input
            id="knowledge-import-file"
            type="file"
            multiple
            aria-label="Import files to knowledge"
            accept={ACCEPT}
            onChange={(event) => {
              importFiles(event.target.files);
              event.target.value = "";
            }}
          />
        </div>
        {imports.length > 0 && (
          <ul className="attachment-list" aria-label="Imported sources">
            {imports.map((entry) => (
              <li key={entry.sourceId ?? entry.attachmentId}>
                Imported <a href={`/knowledge/${entry.sourceId}`}>{entry.name}</a>
              </li>
            ))}
          </ul>
        )}
        {uploadError && <p role="alert">{uploadError}</p>}
      </ComposerPrimitive.Root>
    </main>
  );
}

createRoot(document.getElementById("root")).render(<SeaRuntime><Chat /></SeaRuntime>);

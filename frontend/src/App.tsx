import { useEffect, useRef, useState, type FormEvent } from 'react'
import Select, { createFilter, type GroupBase } from 'react-select'
import {
  Accessibility,
  ArrowDownUp,
  Baby,
  BetweenVerticalStart,
  Footprints,
  Flag,
  MapPin,
  MessageCircle,
  MoveDown,
  MoveUp,
  Navigation,
  Send,
  User,
  type LucideIcon,
} from 'lucide-react'
import './App.css'
import type {
  AiMessageRequest,
  AiMessageResponse,
  ErrorResponse,
  NodeDTO,
  NodesResponse,
  PassengerProfile,
  ProfilesResponse,
  RouteDTO,
  RouteInputStep,
  RouteInstruction,
} from './types'

type NodeSelectOption = {
  value: string
  label: string
  group: string
  hint?: string
  keywords: string
}

type ChatMessage = {
  id: string
  role: 'user' | 'assistant'
  text: string
}

const DEFAULT_PROFILE: PassengerProfile = 'PASSENGER'
const DEFAULT_PROFILE_DESCRIPTIONS: Record<PassengerProfile, string> = {
  PASSENGER: '',
  ELDERLY: '',
  PARENT_WITH_STROLLER: '',
  WHEELCHAIR_USER: '',
}

const PROFILE_META: Record<
  PassengerProfile,
  {
    label: string
    icon: LucideIcon
    hint: string
  }
> = {
  PASSENGER: {
    label: 'Passenger',
    icon: User,
    hint: 'Fastest available route',
  },
  ELDERLY: {
    label: 'Elderly',
    icon: User,
    hint: 'Prefer fewer stairs',
  },
  PARENT_WITH_STROLLER: {
    label: 'Stroller',
    icon: Baby,
    hint: 'Avoid stairs where possible',
  },
  WHEELCHAIR_USER: {
    label: 'Wheelchair',
    icon: Accessibility,
    hint: 'Step-free route only',
  },
}

function readApiError(payload: unknown, fallback: string): string {
  if (
    typeof payload === 'object' &&
    payload !== null &&
    'error' in payload &&
    typeof (payload as { error?: unknown }).error === 'string'
  ) {
    return (payload as { error: string }).error
  }
  return fallback
}

function formatNodeLabel(nodeId: string): string {
  const levelMatch = nodeId.match(/^([FB])(\d+)_/)
  const body = nodeId.replace(/^([FB])(\d+)_/, '')
  const words = body
    .split('_')
    .filter(Boolean)
    .map((word) => {
      if (/^\d+$/.test(word)) {
        return word
      }
      return word.charAt(0) + word.slice(1).toLowerCase()
    })

  if (!levelMatch) {
    return words.join(' ')
  }

  const [, zone, level] = levelMatch
  const levelPrefix = zone === 'F' ? `Level ${level}` : `Level -${level}`
  return `${levelPrefix} · ${words.join(' ')}`
}

function normalizeNodeCandidate(value: string): string {
  return value
    .trim()
    .toUpperCase()
    .replace(/[^A-Z0-9_]/g, '_')
    .replace(/_+/g, '_')
    .replace(/^_+|_+$/g, '')
}

function getFromNodeFromUrl(nodes: string[]): string | null {
  const params = new URLSearchParams(globalThis.location.search)
  const fromCandidate =
    params.get('from') ??
    params.get('node') ??
    params.get('location') ??
    params.get('id')

  if (!fromCandidate) {
    return null
  }

  const normalized = normalizeNodeCandidate(fromCandidate)
  return (
    nodes.find((node) => normalizeNodeCandidate(node) === normalized) ?? null
  )
}

function routeNoticeTone(message: string): 'error' | 'warning' {
  if (message.toLowerCase().includes('no route')) {
    return 'warning'
  }
  return 'error'
}

async function readApiResponse<T>(response: Response, fallback: string): Promise<T> {
  const payload = (await response.json()) as T | ErrorResponse

  if (!response.ok) {
    throw new Error(readApiError(payload, fallback))
  }

  return payload as T
}

function profileDescriptionMap(
  profiles: ProfilesResponse['profiles'],
): Record<PassengerProfile, string> {
  const descriptions = { ...DEFAULT_PROFILE_DESCRIPTIONS }
  for (const item of profiles) {
    descriptions[item.name] = item.description
  }
  return descriptions
}

function makeNodeOption(node: NodeDTO): NodeSelectOption {
  return {
    value: node.id,
    label: node.label || formatNodeLabel(node.id),
    group: node.category || 'Other',
    hint: node.hint,
    keywords: `${node.id} ${node.label} ${node.category} ${node.hint ?? ''}`.toLowerCase(),
  }
}

function groupedNodeOptions(nodes: NodeSelectOption[]): Array<GroupBase<NodeSelectOption>> {
  const groups = new Map<string, NodeSelectOption[]>()
  for (const option of nodes) {
    const list = groups.get(option.group) ?? []
    list.push(option)
    groups.set(option.group, list)
  }

  return [...groups.entries()]
    .sort((a, b) => a[0].localeCompare(b[0]))
    .map(([label, options]) => ({
      label,
      options: options.sort((a, b) => a.label.localeCompare(b.label)),
    }))
}

function edgeIconForInstruction(
  routeSteps: RouteInputStep[],
  instruction: RouteInstruction,
): LucideIcon {
  const segment = routeSteps.slice(
    instruction.fromStepIndex,
    instruction.toStepIndex + 1,
  )
  const emphasized =
    segment.find((step) => step.edgeType !== 'CORRIDOR') ??
    routeSteps[instruction.fromStepIndex]

  if (!emphasized) {
    return Navigation
  }

  if (emphasized.edgeType === 'ELEVATOR') {
    return BetweenVerticalStart
  }
  if (emphasized.edgeType === 'ESCALATOR') {
    return emphasized.floorTo < emphasized.floorFrom ? MoveDown : MoveUp
  }
  if (emphasized.edgeType === 'STAIRS') {
    return ArrowDownUp
  }
  return Footprints
}

async function fetchRouteInstructions(
  from: string,
  to: string,
  profile: PassengerProfile,
): Promise<RouteDTO> {
  const params = new URLSearchParams({ from, to, profile })
  const response = await fetch(`/api/route-instructions?${params.toString()}`)
  return readApiResponse<RouteDTO>(response, 'Unable to find route')
}

async function sendAiMessage(
  message: string,
  messages: ChatMessage[],
  routeContext: RouteDTO | null,
): Promise<AiMessageResponse> {
  const payload: AiMessageRequest = {
    message,
    messages: messages.map((item) => ({
      role: item.role,
      text: item.text,
    })),
    routeContext,
  }

  const response = await fetch('/api/ai-message', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(payload),
  })
  return readApiResponse<AiMessageResponse>(response, 'Unable to get AI answer')
}

function App() {
  const chatBodyRef = useRef<HTMLDivElement | null>(null)
  const [nodes, setNodes] = useState<NodeDTO[]>([])
  const [profiles, setProfiles] = useState<PassengerProfile[]>([])
  const [profileDescriptions, setProfileDescriptions] = useState<
    Record<PassengerProfile, string>
  >(DEFAULT_PROFILE_DESCRIPTIONS)
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const [profile, setProfile] = useState<PassengerProfile>(DEFAULT_PROFILE)
  const [route, setRoute] = useState<RouteDTO | null>(null)
  const [error, setError] = useState('')
  const [aiMessage, setAiMessage] = useState('')
  const [aiMessages, setAiMessages] = useState<ChatMessage[]>([])
  const [aiError, setAiError] = useState('')
  const [setupLoading, setSetupLoading] = useState(true)
  const [loading, setLoading] = useState(false)
  const [aiLoading, setAiLoading] = useState(false)

  const fromNodes = nodes.filter((node) => node.selectableFrom)
  const toNodes = nodes.filter((node) => node.selectableTo)
  const fromSelectList = fromNodes.map(makeNodeOption)
  if (from && !fromNodes.some((node) => node.id === from)) {
    fromSelectList.unshift({
      value: from,
      label: formatNodeLabel(from),
      group: 'Current exact point',
      keywords: `${from} ${formatNodeLabel(from)}`.toLowerCase(),
    })
  }
  const fromSelectGroups = groupedNodeOptions(fromSelectList)

  const toSelectList = toNodes.map(makeNodeOption)
  const toSelectGroups = groupedNodeOptions(toSelectList)
  const fromSelected = fromSelectList.find((item) => item.value === from) ?? null
  const toSelected = toSelectList.find((item) => item.value === to) ?? null
  const canSubmit =
    !setupLoading &&
    !loading &&
    Boolean(from && to && profile) &&
    from !== to
  const canSendAiMessage = !aiLoading && aiMessage.trim().length > 0

  useEffect(() => {
    let cancelled = false

    async function loadFormData() {
      setSetupLoading(true)
      setError('')

      try {
        const [nodesRes, profilesRes] = await Promise.all([
          fetch('/api/nodes'),
          fetch('/api/profiles'),
        ])

        const nodesPayload = await readApiResponse<NodesResponse>(
          nodesRes,
          'Failed to load nodes',
        )
        const profilesPayload = await readApiResponse<ProfilesResponse>(
          profilesRes,
          'Failed to load profiles',
        )

        const loadedNodes = [...nodesPayload.nodes].sort((a, b) =>
          a.label.localeCompare(b.label),
        )
        const loadedNodeIds = loadedNodes.map((node) => node.id)
        const loadedProfiles = profilesPayload.profiles
        const loadedProfileNames = loadedProfiles.map((item) => item.name)

        if (cancelled) {
          return
        }

        setNodes(loadedNodes)
        setProfiles(loadedProfileNames)
        setProfileDescriptions(profileDescriptionMap(loadedProfiles))

        const firstNode = loadedNodes.find((node) => node.selectableFrom)?.id ?? ''
        const secondNode =
          loadedNodes.find((node) => node.selectableTo && node.id !== firstNode)?.id ??
          loadedNodes.find((node) => node.selectableTo)?.id ??
          firstNode
        const fromNodeFromUrl = getFromNodeFromUrl(loadedNodeIds)

        setFrom(fromNodeFromUrl ?? firstNode)
        setTo(secondNode)
        setProfile(loadedProfileNames[0] ?? DEFAULT_PROFILE)
      } catch (err) {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : 'Failed to load form data')
        }
      } finally {
        if (!cancelled) {
          setSetupLoading(false)
        }
      }
    }

    void loadFormData()

    return () => {
      cancelled = true
    }
  }, [])

  useEffect(() => {
    const chatBody = chatBodyRef.current
    if (chatBody) {
      chatBody.scrollTop = chatBody.scrollHeight
    }
  }, [aiMessages, aiLoading])

  async function findRoute(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!from || !to || !profile) {
      return
    }

    setLoading(true)
    setError('')
    setRoute(null)

    try {
      const narrated = await fetchRouteInstructions(from, to, profile)
      setRoute(narrated)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Unable to find route')
    } finally {
      setLoading(false)
    }
  }

  async function askAi(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const message = aiMessage.trim()
    if (!message) {
      return
    }

    setAiLoading(true)
    setAiError('')
    setAiMessage('')
    const previousMessages = aiMessages
    setAiMessages((messages) => [
      ...messages,
      {
        id: `user-${Date.now()}`,
        role: 'user',
        text: message,
      },
    ])

    try {
      const response = await sendAiMessage(message, previousMessages, route)
      setAiMessages((messages) => [
        ...messages,
        {
          id: `assistant-${Date.now()}`,
          role: 'assistant',
          text: response.answer,
        },
      ])
    } catch (err) {
      setAiError(err instanceof Error ? err.message : 'Unable to get AI answer')
    } finally {
      setAiLoading(false)
    }
  }

  const routeReady = Boolean(route)
  const errorTone = routeNoticeTone(error)
  const routeSteps = route?.inputSteps ?? []
  const routeInstructions = route?.instructions ?? []
  const routeFromLabel = routeSteps[0]?.fromLabel ?? ''
  const lastRouteStep = routeSteps[routeSteps.length - 1]
  const routeToLabel = lastRouteStep?.toLabel ?? ''

  return (
    <main className="wf-page">
      <header className="wf-header">
        <div className="wf-mark" aria-hidden="true">
          <svg viewBox="0 0 32 32">
            <path d="M16 4 L26 14 L20 14 L20 27 L12 27 L12 14 L6 14 Z" />
          </svg>
        </div>
        <span className="wf-brand">Wayfinder</span>
        <span className="wf-live">Route Service Online</span>
      </header>

      <h1 className="wf-title">Find your way</h1>

      <section className="wf-layout">
        <form className="wf-card" onSubmit={findRoute}>
          <h2 className="wf-card-title">Route Details</h2>

          <div className="wf-field">
            <label htmlFor="from">From</label>
            <div className={`wf-input-wrap${setupLoading ? ' is-disabled' : ''}`}>
              <MapPin className="wf-input-icon" size={16} />
              <Select<NodeSelectOption, false>
                inputId="from"
                classNamePrefix="wf-combo"
                options={fromSelectGroups}
                value={fromSelected}
                onChange={(option) => setFrom(option?.value ?? '')}
                isDisabled={setupLoading}
                isSearchable
                placeholder="Choose starting point..."
                filterOption={createFilter({ stringify: (option) => option.data.keywords })}
                menuPortalTarget={globalThis.document?.body}
                styles={{ menuPortal: (base) => ({ ...base, zIndex: 30 }) }}
                formatOptionLabel={(option) => (
                  <div className="wf-combo-option">
                    <span>{option.label}</span>
                    {option.hint ? <small>{option.hint}</small> : null}
                  </div>
                )}
              />
            </div>
          </div>

          <div className="wf-field">
            <label htmlFor="to">To</label>
            <div className={`wf-input-wrap${setupLoading ? ' is-disabled' : ''}`}>
              <Flag className="wf-input-icon" size={16} />
              <Select<NodeSelectOption, false>
                inputId="to"
                classNamePrefix="wf-combo"
                options={toSelectGroups}
                value={toSelected}
                onChange={(option) => setTo(option?.value ?? '')}
                isDisabled={setupLoading}
                isSearchable
                placeholder="Choose destination..."
                filterOption={createFilter({ stringify: (option) => option.data.keywords })}
                menuPortalTarget={globalThis.document?.body}
                styles={{ menuPortal: (base) => ({ ...base, zIndex: 30 }) }}
                formatOptionLabel={(option) => (
                  <div className="wf-combo-option">
                    <span>{option.label}</span>
                    {option.hint ? <small>{option.hint}</small> : null}
                  </div>
                )}
              />
            </div>
            <small className="wf-destination-help">
              Technical points like escalators and corridors are hidden from
              destinations.
            </small>
          </div>

          <div className="wf-field">
            <label htmlFor="profile">Route type</label>
            <div className="wf-profile-grid">
              {profiles.map((item) => {
                const selected = item === profile
                const Icon = PROFILE_META[item].icon
                return (
                  <button
                    key={item}
                    type="button"
                    className={`wf-profile-btn${selected ? ' is-selected' : ''}`}
                    onClick={() => setProfile(item)}
                    aria-pressed={selected}
                    disabled={setupLoading}
                  >
                    <span className="wf-profile-icon">
                      <Icon size={15} />
                    </span>
                    <span className="wf-profile-copy">
                      <span>{PROFILE_META[item].label}</span>
                      <small>{PROFILE_META[item].hint}</small>
                    </span>
                  </button>
                )
              })}
            </div>
            {profileDescriptions[profile] ? (
              <small className="wf-profile-help">{profileDescriptions[profile]}</small>
            ) : null}
          </div>

          <button type="submit" className="wf-btn wf-btn-primary" disabled={!canSubmit}>
            {setupLoading
              ? 'Loading terminal data...'
              : loading
                ? 'Finding route...'
                : routeReady
                  ? 'Recalculate Route'
                  : 'Find Route'}
          </button>
        </form>

        <section className="wf-card wf-card-elev" aria-live="polite">
          <h2 className="wf-card-title">Route Instructions</h2>

          {error ? (
            <div className={`wf-notice ${errorTone}`}>{error}</div>
          ) : routeReady && route ? (
            <div className="wf-result">
              {route.summary ? <p className="wf-route-summary">{route.summary}</p> : null}
              <div className="wf-summary">
                <div className="wf-metric">
                  <span>
                    <MapPin size={12} />
                    From
                  </span>
                  <strong>{routeFromLabel}</strong>
                </div>
                <div className="wf-metric">
                  <span>
                    <Flag size={12} />
                    To
                  </span>
                  <strong>{routeToLabel}</strong>
                </div>
              </div>

              <ol className="wf-path">
                {routeInstructions.map((instruction, index) => {
                  const Icon =
                    routeSteps.length > 0
                      ? edgeIconForInstruction(routeSteps, instruction)
                      : Navigation

                  return (
                    <li
                      key={`instruction-${instruction.fromStepIndex}-${instruction.toStepIndex}`}
                    >
                      <span className="wf-step-index wf-step-icon">
                        <Icon size={14} />
                      </span>
                      <div className="wf-step-info">
                        <strong>{instruction.text}</strong>
                        {instruction.warnings.length > 0 ? (
                          <div className="wf-step-warnings">
                            {instruction.warnings.map((warning) => (
                              <span
                                key={`${index}-${warning}`}
                                className="wf-warning-chip"
                              >
                                {warning}
                              </span>
                            ))}
                          </div>
                        ) : null}
                      </div>
                    </li>
                  )
                })}
              </ol>
            </div>
          ) : (
            <div className="wf-empty">
              <Navigation size={18} />
              <strong>Ready to guide you</strong>
              <p>
                Choose your starting point and destination. Your terminal route
                instructions will appear here.
              </p>
            </div>
          )}
        </section>

        <form className="wf-card wf-ai-card" onSubmit={askAi}>
          <div className="wf-ai-heading">
            <span className="wf-ai-icon">
              <MessageCircle size={16} />
            </span>
            <div>
              <h2 className="wf-card-title">Ask AI</h2>
              <p>Send a free-form question to the AI assistant.</p>
            </div>
          </div>

          <div className="wf-chat-window">
            <div className="wf-chat-body" ref={chatBodyRef} aria-live="polite">
              {aiMessages.length === 0 ? (
                <div className="wf-chat-empty">
                  Ask a question about documents, airport process, or how to use the route form.
                </div>
              ) : (
                aiMessages.map((message) => (
                  <div
                    key={message.id}
                    className={`wf-chat-row ${message.role === 'user' ? 'is-user' : 'is-ai'}`}
                  >
                    <div className="wf-chat-bubble">{message.text}</div>
                  </div>
                ))
              )}
              {aiLoading ? (
                <div className="wf-chat-row is-ai">
                  <div className="wf-chat-bubble wf-chat-typing">AI is typing...</div>
                </div>
              ) : null}
            </div>
          </div>

          <label className="wf-ai-label" htmlFor="ai-message">
            Message
          </label>
          <textarea
            id="ai-message"
            className="wf-ai-textarea"
            value={aiMessage}
            maxLength={2000}
            onChange={(event) => setAiMessage(event.target.value)}
            placeholder="Example: What should I prepare before passport control?"
          />

          <button type="submit" className="wf-btn wf-btn-primary" disabled={!canSendAiMessage}>
            <Send size={15} />
            {aiLoading ? 'Sending...' : 'Send Message'}
          </button>

          {aiError ? <div className="wf-notice error">{aiError}</div> : null}
        </form>
      </section>
    </main>
  )
}

export default App

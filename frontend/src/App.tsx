import { useEffect, useRef, useState, type FormEvent } from 'react'
import {
  Accessibility,
  Baby,
  Clock3,
  Flag,
  Layers,
  MapPin,
  Navigation,
  QrCode,
  Route,
  User,
  type LucideIcon,
} from 'lucide-react'
import './App.css'
import type { PassengerProfile, RouteDTO } from './types'

type NodesResponse = {
  nodes: string[]
}

type ProfilesResponse = {
  profiles: Array<{
    name: PassengerProfile
    description: string
  }>
}

type ErrorResponse = {
  error?: string
}

type BarcodeDetectorLike = {
  detect: (source: ImageBitmapSource) => Promise<Array<{ rawValue?: string }>>
}

type BarcodeDetectorCtor = new (options?: {
  formats?: string[]
}) => BarcodeDetectorLike

const DEFAULT_PROFILE: PassengerProfile = 'PASSENGER'

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

function extractNodeIdFromQr(rawValue: string, nodes: string[]): string | null {
  const normalizedNodes = new Set(nodes.map((node) => normalizeNodeCandidate(node)))
  const candidates: string[] = []
  const trimmed = rawValue.trim()

  if (!trimmed) {
    return null
  }

  candidates.push(trimmed)

  try {
    const parsedJson = JSON.parse(trimmed) as {
      node?: string
      from?: string
      location?: string
      id?: string
    }
    if (typeof parsedJson.node === 'string') candidates.push(parsedJson.node)
    if (typeof parsedJson.from === 'string') candidates.push(parsedJson.from)
    if (typeof parsedJson.location === 'string') candidates.push(parsedJson.location)
    if (typeof parsedJson.id === 'string') candidates.push(parsedJson.id)
  } catch {
    // Not JSON, continue with URL/plain parsing.
  }

  try {
    const url = new URL(trimmed)
    const keys = ['node', 'from', 'location', 'id']
    for (const key of keys) {
      const value = url.searchParams.get(key)
      if (value) {
        candidates.push(value)
      }
    }
    const pathParts = url.pathname.split('/').filter(Boolean)
    if (pathParts.length > 0) {
      candidates.push(pathParts[pathParts.length - 1])
    }
  } catch {
    // Not a URL, plain QR payload.
  }

  for (const candidate of candidates) {
    const normalized = normalizeNodeCandidate(candidate)
    if (normalizedNodes.has(normalized)) {
      const direct = nodes.find(
        (node) => normalizeNodeCandidate(node) === normalized,
      )
      if (direct) {
        return direct
      }
    }
  }

  return null
}

function getBarcodeDetectorCtor(): BarcodeDetectorCtor | null {
  const detector = (globalThis as unknown as { BarcodeDetector?: BarcodeDetectorCtor })
    .BarcodeDetector
  return typeof detector === 'function' ? detector : null
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

function App() {
  const [nodes, setNodes] = useState<string[]>([])
  const [profiles, setProfiles] = useState<PassengerProfile[]>([])
  const [profileDescriptions, setProfileDescriptions] = useState<
    Record<PassengerProfile, string>
  >({
    PASSENGER: '',
    ELDERLY: '',
    PARENT_WITH_STROLLER: '',
    WHEELCHAIR_USER: '',
  })
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const [profile, setProfile] = useState<PassengerProfile>(DEFAULT_PROFILE)
  const [route, setRoute] = useState<RouteDTO | null>(null)
  const [error, setError] = useState('')
  const [setupLoading, setSetupLoading] = useState(true)
  const [loading, setLoading] = useState(false)
  const [scanActive, setScanActive] = useState(false)
  const [scanStatus, setScanStatus] = useState('')

  const videoRef = useRef<HTMLVideoElement | null>(null)
  const streamRef = useRef<MediaStream | null>(null)
  const detectorRef = useRef<BarcodeDetectorLike | null>(null)
  const frameRef = useRef<number | null>(null)
  const scanningRef = useRef(false)

  function stopScanner() {
    scanningRef.current = false
    if (frameRef.current !== null) {
      cancelAnimationFrame(frameRef.current)
      frameRef.current = null
    }
    if (streamRef.current) {
      for (const track of streamRef.current.getTracks()) {
        track.stop()
      }
      streamRef.current = null
    }
    if (videoRef.current) {
      videoRef.current.srcObject = null
    }
    setScanActive(false)
  }

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

        const nodesPayload = (await nodesRes.json()) as NodesResponse | ErrorResponse
        const profilesPayload = (await profilesRes.json()) as
          | ProfilesResponse
          | ErrorResponse

        if (!nodesRes.ok) {
          throw new Error(readApiError(nodesPayload, 'Failed to load nodes'))
        }
        if (!profilesRes.ok) {
          throw new Error(readApiError(profilesPayload, 'Failed to load profiles'))
        }

        const loadedNodes = [...(nodesPayload as NodesResponse).nodes].sort()
        const loadedProfiles = (profilesPayload as ProfilesResponse).profiles
        const loadedProfileNames = loadedProfiles.map((item) => item.name)

        if (cancelled) {
          return
        }

        setNodes(loadedNodes)
        setProfiles(loadedProfileNames)
        setProfileDescriptions({
          PASSENGER:
            loadedProfiles.find((item) => item.name === 'PASSENGER')
              ?.description ?? '',
          ELDERLY:
            loadedProfiles.find((item) => item.name === 'ELDERLY')
              ?.description ?? '',
          PARENT_WITH_STROLLER:
            loadedProfiles.find((item) => item.name === 'PARENT_WITH_STROLLER')
              ?.description ?? '',
          WHEELCHAIR_USER:
            loadedProfiles.find((item) => item.name === 'WHEELCHAIR_USER')
              ?.description ?? '',
        })

        const firstNode = loadedNodes[0] ?? ''
        const secondNode = loadedNodes[1] ?? firstNode
        const fromFromUrl = getFromNodeFromUrl(loadedNodes)

        setFrom(fromFromUrl ?? firstNode)
        setTo(secondNode)
        setProfile(loadedProfileNames[0] ?? DEFAULT_PROFILE)
        if (fromFromUrl) {
          setScanStatus(`Starting point set from QR link: ${formatNodeLabel(fromFromUrl)}.`)
        }
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
      stopScanner()
    }
  }, [])

  async function startScanner() {
    if (scanActive || setupLoading || nodes.length === 0) {
      return
    }

    setScanStatus('')

    const DetectorCtor = getBarcodeDetectorCtor()
    if (!DetectorCtor) {
      setScanStatus(
        'This browser does not support in-app QR scan. Use your phone camera to open a QR link with a starting point.',
      )
      return
    }

    if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
      setScanStatus('Camera access is unavailable in this browser context.')
      return
    }

    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        video: { facingMode: { ideal: 'environment' } },
        audio: false,
      })

      streamRef.current = stream
      if (!videoRef.current) {
        throw new Error('Scanner video element is not ready.')
      }
      videoRef.current.srcObject = stream
      await videoRef.current.play()

      try {
        detectorRef.current = new DetectorCtor({ formats: ['qr_code'] })
      } catch {
        detectorRef.current = new DetectorCtor()
      }

      scanningRef.current = true
      setScanActive(true)
      setScanStatus('Scanning QR. Point camera at location code.')

      const tick = () => {
        const video = videoRef.current
        const detector = detectorRef.current
        if (!scanningRef.current || !video || !detector) {
          return
        }

        detector
          .detect(video)
          .then((results) => {
            if (!scanningRef.current) {
              return
            }

            const rawValue = results[0]?.rawValue
            if (rawValue) {
              const node = extractNodeIdFromQr(rawValue, nodes)
              if (node) {
                setFrom(node)
                setScanStatus(`Scanned start location: ${formatNodeLabel(node)}.`)
                stopScanner()
                return
              }
              setScanStatus('QR detected, but it does not map to a known node.')
            }

            frameRef.current = requestAnimationFrame(tick)
          })
          .catch(() => {
            if (scanningRef.current) {
              frameRef.current = requestAnimationFrame(tick)
            }
          })
      }

      frameRef.current = requestAnimationFrame(tick)
    } catch (err) {
      stopScanner()
      setScanStatus(
        err instanceof Error
          ? `Cannot open camera: ${err.message}`
          : 'Cannot open camera for QR scan.',
      )
    }
  }

  async function findRoute(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!from || !to || !profile) {
      return
    }

    setLoading(true)
    setError('')
    setRoute(null)

    try {
      const params = new URLSearchParams({ from, to, profile })
      const response = await fetch(`/api/route?${params.toString()}`)
      const payload = (await response.json()) as RouteDTO | ErrorResponse

      if (!response.ok) {
        throw new Error(readApiError(payload, 'Unable to find route'))
      }
      setRoute(payload as RouteDTO)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Unable to find route')
    } finally {
      setLoading(false)
    }
  }

  const canSubmit =
    !setupLoading && !loading && Boolean(from && to && profile) && from !== to
  const routeReady = Boolean(route)
  const errorTone = routeNoticeTone(error)

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
            <div className="wf-input-wrap">
              <MapPin className="wf-input-icon" size={16} />
              <select
                id="from"
                value={from}
                onChange={(event) => setFrom(event.target.value)}
                disabled={setupLoading || scanActive}
              >
                {nodes.map((node) => (
                  <option key={node} value={node}>
                    {formatNodeLabel(node)}
                  </option>
                ))}
              </select>
            </div>
          </div>

          <div className="wf-scan-row">
            <button
              type="button"
              className="wf-btn wf-btn-quiet"
              onClick={scanActive ? stopScanner : startScanner}
              disabled={setupLoading}
            >
              <QrCode size={16} />
              {scanActive ? 'Stop Scan' : 'Scan QR'}
            </button>
            <p className="wf-scan-help">
              {scanStatus || 'Scan a nearby QR to set your starting point.'}
            </p>
          </div>

          {scanActive ? (
            <div className="wf-scanner-box">
              <video ref={videoRef} className="wf-scanner-video" muted playsInline />
            </div>
          ) : null}

          <div className="wf-field">
            <label htmlFor="to">To</label>
            <div className="wf-input-wrap">
              <Flag className="wf-input-icon" size={16} />
              <select
                id="to"
                value={to}
                onChange={(event) => setTo(event.target.value)}
                disabled={setupLoading}
              >
                {nodes.map((node) => (
                  <option key={node} value={node}>
                    {formatNodeLabel(node)}
                  </option>
                ))}
              </select>
            </div>
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
              <div className="wf-summary">
                <div className="wf-metric">
                  <span>
                    <MapPin size={12} />
                    From
                  </span>
                  <strong>{formatNodeLabel(route.from)}</strong>
                  <code>{route.from}</code>
                </div>
                <div className="wf-metric">
                  <span>
                    <Flag size={12} />
                    To
                  </span>
                  <strong>{formatNodeLabel(route.to)}</strong>
                  <code>{route.to}</code>
                </div>
                <div className="wf-metric">
                  <span>
                    <Clock3 size={12} />
                    Estimated time
                  </span>
                  <strong>{route.cost} min</strong>
                  <code>
                    <Route size={12} />
                    {profile.replaceAll('_', ' ')}
                    <Layers size={12} />
                  </code>
                </div>
              </div>

              <ol className="wf-path">
                {route.path.map((node, index) => (
                  <li key={`${node}-${index}`}>
                    <span className="wf-step-index">{index + 1}</span>
                    <div className="wf-step-info">
                      <strong>{formatNodeLabel(node)}</strong>
                      <code>{node}</code>
                    </div>
                  </li>
                ))}
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
      </section>
    </main>
  )
}

export default App

export type PassengerProfile =
  | 'PASSENGER'
  | 'ELDERLY'
  | 'PARENT_WITH_STROLLER'
  | 'WHEELCHAIR_USER'

export type NodeDTO = {
  id: string
  label: string
  floor: number
  category: string
  selectableFrom: boolean
  selectableTo: boolean
  hint?: string
}

export type NodesResponse = {
  nodes: NodeDTO[]
}

export type ProfilesResponse = {
  profiles: Array<{
    name: PassengerProfile
    description: string
  }>
}

export type ErrorResponse = {
  error?: string
}

export type AiMessageResponse = {
  answer: string
}

export type AiMessageRequest = {
  message: string
  messages: Array<{
    role: 'user' | 'assistant'
    text: string
  }>
  routeContext: RouteDTO | null
}

export type RouteDTO = {
  summary: string
  inputSteps?: Array<{
    fromId: string
    toId: string
    fromLabel: string
    toLabel: string
    edgeType: 'CORRIDOR' | 'ELEVATOR' | 'ESCALATOR' | 'STAIRS'
    floorFrom: number
    floorTo: number
  }>
  instructions?: Array<{
    fromStepIndex: number
    toStepIndex: number
    text: string
    warnings: string[]
  }>
}

export type RouteInputStep = NonNullable<RouteDTO['inputSteps']>[number]

export type RouteInstruction = NonNullable<RouteDTO['instructions']>[number]

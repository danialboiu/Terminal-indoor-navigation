export type NodeDTO = {
  id: string
  label: string
  description: string
  floor: number
  enabled: boolean
}

export type PassengerProfile =
  | 'PASSENGER'
  | 'ELDERLY'
  | 'PARENT_WITH_STROLLER'
  | 'WHEELCHAIR_USER'

export type RouteDTO = {
  from: string
  to: string
  path: string[]
  cost: number
  profile: PassengerProfile
}


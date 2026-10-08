export interface DroneBattery {
  capacity_percent?: number
  remain_flight_time?: number
  return_home_power?: number
  landing_power?: number
}

export interface PositionState {
  gps_number?: number
  is_fixed?: boolean
  quality?: number
  rtk_number?: number
}

export interface OsdDrone {
  mode_code?: number
  gear?: number
  latitude?: number
  longitude?: number
  height?: number
  elevation?: number
  horizontal_speed?: number
  vertical_speed?: number
  attitude_head?: number
  attitude_pitch?: number
  attitude_roll?: number
  home_distance?: number
  wind_speed?: number
  wind_direction?: number
  total_flight_time?: number
  total_flight_distance?: number
  firmware_version?: string
  battery?: DroneBattery
  position_state?: PositionState
}

export interface OsdDock {
  mode_code?: number
  latitude?: number
  longitude?: number
  height?: number
  cover_state?: number
  putter_state?: number
  drone_in_dock?: boolean
  drone_charge_state?: number
  humidity?: number
  temperature?: number
  environment_temperature?: number
  rainfall?: number
  wind_speed?: number
  job_number?: number
  alarm_state?: boolean
  drc_state?: number
}

export interface OsdMessage {
  sn: string
  role: string
  data: OsdDrone & OsdDock
}

export interface StatusMessage {
  sn: string
  online: boolean
  children: { sn: string; online: boolean }[]
}

export type UserRole = 'USER' | 'ADMIN'

export type OAuthProviderCode = 'GOOGLE' | 'KAKAO' | 'NAVER'

export type PropertyType = 'APARTMENT' | 'OFFICETEL' | 'VILLA' | 'HOUSE'

export type TransactionType = 'SALE' | 'JEONSE' | 'MONTHLY_RENT'

export type ShapDirection = 'UP' | 'DOWN' | 'NEUTRAL'

export interface ApiErrorDetail {
  field?: string
  reason: string
}

export interface ApiErrorResponse {
  code: string
  message: string
  details?: ApiErrorDetail[]
  path: string
  timestamp: string
}

export interface PageMetadata {
  number: number
  size: number
  totalElements: number
  totalPages: number
}

export interface PagedResponse<T> {
  items: T[]
  page: PageMetadata
}

export interface AuthUser {
  userId: number
  email: string
  displayName: string
  roles: UserRole[]
}

export interface AuthMeResponse {
  authenticated: boolean
  user: AuthUser | null
}

export interface AuthRefreshResponse {
  accessToken: string
  tokenType: 'Bearer'
  expiresIn: number
  user: AuthUser
}

export interface AuthLoginRequest {
  email: string
  password: string
}

export interface AuthSignupRequest extends AuthLoginRequest {
  displayName: string
}

export type AuthSessionResponse = AuthRefreshResponse

export interface AuthLogoutResponse {
  message: string
}

export interface PendingSocialSignupResponse {
  provider: OAuthProviderCode
  email?: string | null
  displayName?: string | null
  matchingEmailAccountExists: boolean
}

export interface SocialSignupRequest extends AuthSignupRequest {}

export interface SocialLinkRequest extends AuthLoginRequest {}

export interface LatestTransaction {
  transactionType: TransactionType
  dealAmount: number
  dealDate: string
}

export interface PropertyMapItem {
  propertyId: number
  propertyType: PropertyType
  name: string
  address: string
  lat: number
  lng: number
  latestTransaction?: LatestTransaction | null
  dealCount: number
  aiAvailable: boolean
}

export interface Bounds {
  swLat: number
  swLng: number
  neLat: number
  neLng: number
}

export interface MapSearchParams extends Bounds {
  zoomLevel: number
  propertyTypes?: PropertyType[]
  transactionTypes?: TransactionType[]
  minDealAmount?: number
  maxDealAmount?: number
  minAreaM2?: number
  maxAreaM2?: number
  dealYearFrom?: number
  dealYearTo?: number
}

export interface PropertyMapResponse {
  items: PropertyMapItem[]
  bounds: Bounds
  filters: {
    propertyTypes: PropertyType[]
    transactionTypes: TransactionType[]
    zoomLevel: number
  }
}

export interface PropertySearchParams {
  sido?: string
  sigungu?: string
  legalDong?: string
  complexName?: string
  keyword?: string
  centerLat?: number
  centerLng?: number
  swLat?: number
  swLng?: number
  neLat?: number
  neLng?: number
  propertyTypes?: PropertyType[]
  transactionTypes?: TransactionType[]
  minDealAmount?: number
  maxDealAmount?: number
  minAreaM2?: number
  maxAreaM2?: number
  dealYearFrom?: number
  dealYearTo?: number
  page?: number
  size?: number
  sort?: string
}

export interface PropertySearchItem {
  propertyId: number
  propertyType: PropertyType
  name: string
  address: string
  legalDong: string
  lat: number
  lng: number
  distanceM?: number
  latestTransaction?: LatestTransaction | null
  aiAvailable: boolean
}

export interface PropertyDetail {
  propertyId: number
  propertyType: PropertyType
  name: string
  address: {
    sido: string
    sigungu: string
    legalDong: string
    roadAddress?: string | null
  }
  location: {
    lat: number
    lng: number
  }
  summary: {
    builtYear?: number | null
    householdCount?: number | null
    latestDealAmount?: number | null
    latestDealDate?: string | null
  }
  transactions: PropertyTransaction[]
  favorite?: {
    apartmentFavorited: boolean
    areaFavorited: boolean
  }
  ai: {
    valuationAvailable: boolean
    shapAvailable: boolean
    unsupportedReason?: string | null
  }
}

export interface PropertyTransaction {
  transactionId?: number
  transactionType: TransactionType
  dealAmount: number
  dealDate: string
  exclusiveAreaM2?: number | null
  floor?: number | null
}

export interface ValuationRequest {
  exclusiveAreaM2: number
  floor: number
  asOfDate: string
}

export interface ValuationResponse {
  propertyId: number
  supported: boolean
  estimatedPrice?: number
  currency?: 'KRW'
  predictionInterval?: {
    lower: number
    upper: number
  }
  modelVersion?: string
  baselineDate?: string
  featureSetVersion?: string
  message: string
}

export interface ShapRequest extends ValuationRequest {}

export interface ShapValue {
  feature: string
  labelKo: string
  value: string | number
  shapValue: number
  direction: ShapDirection
}

export interface ShapResponse {
  propertyId: number
  supported: boolean
  baseValue?: number
  prediction?: number
  currency?: 'KRW'
  values: ShapValue[]
  modelVersion?: string
  baselineDate?: string
  featureSetVersion?: string
  message: string
}

export interface FavoriteApartmentItem {
  favoriteId: number
  propertyId: number
  propertyType: PropertyType
  name: string
  address: string
  lat: number
  lng: number
  latestTransaction?: LatestTransaction | null
  createdAt: string
}

export interface FavoriteAreaItem {
  favoriteAreaId: number
  label: string
  sido?: string
  sigungu?: string
  legalDong?: string
  centerLat?: number
  centerLng?: number
  zoomLevel?: number
  createdAt: string
}

export interface FavoriteApartmentCreateResponse {
  favoriteId: number
  propertyId: number
  createdAt: string
  message: string
}

export interface FavoriteApartmentDeleteResponse {
  propertyId: number
  message: string
}

export interface FavoriteAreaCreateRequest {
  label: string
  sido?: string
  sigungu?: string
  legalDong?: string
  centerLat?: number
  centerLng?: number
  zoomLevel?: number
}

export interface FavoriteAreaCreateResponse {
  favoriteAreaId: number
  label: string
  createdAt: string
  message: string
}

export interface FavoriteAreaDeleteResponse {
  favoriteAreaId: number
  message: string
}

export interface NoticeSummary {
  noticeId: number
  title: string
  summary: string
  pinned: boolean
  publishedAt: string
  createdAt: string
}

export interface NoticeDetail extends NoticeSummary {
  content: string
  updatedAt?: string | null
}

export interface NoticeListParams {
  page?: number
  size?: number
  sort?: 'publishedAt,desc' | 'createdAt,desc'
  keyword?: string
  pinnedOnly?: boolean
}

export interface NoticeUpsertRequest {
  title: string
  content: string
  pinned: boolean
  publishedAt: string
}

export interface NoticeMutationResponse {
  noticeId: number
  message: string
}

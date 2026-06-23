import { defineStore } from 'pinia'

import type { PropertyDetail, ShapValue, ValuationResponse } from '@/api/types'

export interface PropertyChatRuntimeContext {
  source: 'property-detail'
  property: {
    propertyId: number
    name: string
    propertyType: string
    address: string
    builtYear?: number | null
    householdCount?: number | null
    latestDealAmount?: number | null
    latestDealDate?: string | null
  }
  valuation?: {
    estimatedPrice?: number
    currency?: string
    predictionInterval?: {
      lower: number
      upper: number
    }
    modelVersion?: string
    baselineDate?: string
    featureSetVersion?: string
    message?: string
  }
  shap?: {
    values: ShapValue[]
  }
}

interface ChatContextState {
  runtimeContext: PropertyChatRuntimeContext | null
}

export const useChatContextStore = defineStore('chatContext', {
  state: (): ChatContextState => ({
    runtimeContext: null
  }),

  getters: {
    hasRuntimeContext: (state) => Boolean(state.runtimeContext),
    contextTitle: (state) => state.runtimeContext?.property.name ?? ''
  },

  actions: {
    setPropertyAnalysisContext(
      property: PropertyDetail,
      valuation: ValuationResponse,
      shapValues: ShapValue[]
    ) {
      const address = [
        property.address.sido,
        property.address.sigungu,
        property.address.legalDong,
        property.address.roadAddress
      ]
        .filter(Boolean)
        .join(' ')

      this.runtimeContext = {
        source: 'property-detail',
        property: {
          propertyId: property.propertyId,
          name: property.name,
          propertyType: property.propertyType,
          address,
          builtYear: property.summary.builtYear,
          householdCount: property.summary.householdCount,
          latestDealAmount: property.summary.latestDealAmount,
          latestDealDate: property.summary.latestDealDate
        },
        valuation: {
          estimatedPrice: valuation.estimatedPrice,
          currency: valuation.currency,
          predictionInterval: valuation.predictionInterval,
          modelVersion: valuation.modelVersion,
          baselineDate: valuation.baselineDate,
          featureSetVersion: valuation.featureSetVersion,
          message: valuation.message
        },
        shap: {
          values: shapValues
        }
      }
    },

    clearRuntimeContext() {
      this.runtimeContext = null
    }
  }
})

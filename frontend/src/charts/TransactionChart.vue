<script setup lang="ts">
import * as echarts from 'echarts/core'
import { BarChart } from 'echarts/charts'
import { GridComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'

import type { PropertyTransaction } from '@/api/types'
import EmptyState from '@/components/EmptyState.vue'
import { formatKrw } from '@/utils/format'

echarts.use([BarChart, GridComponent, TooltipComponent, CanvasRenderer])

const props = defineProps<{
  transactions: PropertyTransaction[]
}>()

const chartEl = ref<HTMLDivElement | null>(null)
let chart: echarts.ECharts | null = null

const hasData = computed(() => props.transactions.length > 0)

function renderChart() {
  if (!chartEl.value || !hasData.value) {
    return
  }

  chart ??= echarts.init(chartEl.value)
  chart.setOption({
    color: ['#2563eb'],
    grid: { top: 24, right: 16, bottom: 36, left: 72 },
    tooltip: {
      trigger: 'axis',
      valueFormatter: (value: unknown) => formatKrw(Number(value))
    },
    xAxis: {
      type: 'category',
      data: props.transactions.map((item) => item.dealDate)
    },
    yAxis: {
      type: 'value',
      axisLabel: {
        formatter: (value: number) => `${Math.round(value / 100000000)}억`
      }
    },
    series: [
      {
        type: 'bar',
        data: props.transactions.map((item) => item.dealAmount)
      }
    ]
  })
}

onMounted(renderChart)
watch(() => props.transactions, renderChart, { deep: true })

onBeforeUnmount(() => {
  chart?.dispose()
  chart = null
})
</script>

<template>
  <div v-if="hasData" ref="chartEl" class="chart-box" role="img" aria-label="거래 금액 차트"></div>
  <EmptyState
    v-else
    title="아직 표시할 거래 차트가 없습니다."
    description="실거래 데이터가 연결되면 거래 흐름을 차트로 보여드립니다."
  />
</template>

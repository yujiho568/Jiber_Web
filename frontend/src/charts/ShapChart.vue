<script setup lang="ts">
import * as echarts from 'echarts/core'
import { BarChart } from 'echarts/charts'
import { GridComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'

import type { ShapValue } from '@/api/types'
import EmptyState from '@/components/EmptyState.vue'
import { formatKrw } from '@/utils/format'

echarts.use([BarChart, GridComponent, TooltipComponent, CanvasRenderer])

const props = defineProps<{
  values: ShapValue[]
}>()

const chartEl = ref<HTMLDivElement | null>(null)
let chart: echarts.ECharts | null = null

const hasData = computed(() => props.values.length > 0)

async function renderChart() {
  await nextTick()

  if (!chartEl.value || !hasData.value) {
    return
  }

  chart ??= echarts.init(chartEl.value)
  chart.setOption({
    color: ['#0f766e'],
    grid: { top: 24, right: 24, bottom: 36, left: 96 },
    tooltip: {
      trigger: 'axis',
      valueFormatter: (value: unknown) => formatKrw(Number(value))
    },
    xAxis: {
      type: 'value',
      axisLabel: {
        formatter: (value: number) => `${Math.round(value / 100000000)}억`
      }
    },
    yAxis: {
      type: 'category',
      data: props.values.map((item) => item.labelKo)
    },
    series: [
      {
        type: 'bar',
        data: props.values.map((item) => item.shapValue)
      }
    ]
  })
}

onMounted(renderChart)
watch(() => props.values, renderChart, { deep: true, flush: 'post' })

onBeforeUnmount(() => {
  chart?.dispose()
  chart = null
})
</script>

<template>
  <div v-if="hasData" ref="chartEl" class="chart-box" role="img" aria-label="SHAP 요인 차트"></div>
  <EmptyState
    v-else
    title="아직 표시할 SHAP 요인이 없습니다."
    description="아파트 단지 추정가를 요청하면 주요 영향 요인을 차트로 보여드립니다."
  />
</template>

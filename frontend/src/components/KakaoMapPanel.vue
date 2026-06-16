<script setup lang="ts">
import { onMounted, ref } from 'vue'

import { getKakaoMapFallbackMessage, hasKakaoMapKey, loadKakaoMaps } from '@/map/kakaoLoader'

const loading = ref(false)
const ready = ref(false)
const message = ref(getKakaoMapFallbackMessage())

onMounted(async () => {
  if (!hasKakaoMapKey()) {
    return
  }

  loading.value = true
  message.value = '카카오 지도를 불러오고 있습니다.'

  try {
    await loadKakaoMaps()
    ready.value = true
    message.value = '지도를 표시할 준비가 되었습니다.'
  } catch (error) {
    message.value = error instanceof Error ? error.message : '카카오 지도를 불러오지 못했습니다.'
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <section class="map-panel" :class="{ 'is-ready': ready }" aria-label="지도 영역">
    <div class="map-grid" aria-hidden="true">
      <span class="map-pin pin-one"></span>
      <span class="map-pin pin-two"></span>
      <span class="map-pin pin-three"></span>
    </div>
    <div class="map-message">
      <strong>{{ ready ? '카카오 지도 준비 완료' : '지도 준비 중' }}</strong>
      <p>{{ loading ? '지도 리소스를 확인하고 있습니다.' : message }}</p>
    </div>
  </section>
</template>

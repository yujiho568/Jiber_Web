<script setup lang="ts">
import { RouterLink } from 'vue-router'
</script>

<template>
  <section class="landing-grid">
    <div class="landing-copy">
      <p class="eyebrow">실거래 기반 부동산 정보 서비스</p>
      <h1>지도에서 거래 흐름을 확인하고, 아파트 추정가 설명을 살펴보세요.</h1>
      <p>
        Jiber는 실거래 데이터, 지역별 검색, 즐겨찾기, 공지사항, 아파트 단지 추정가와 SHAP 요인 설명을
        한곳에서 확인할 수 있도록 준비 중입니다.
      </p>
      <div class="button-row">
        <RouterLink class="primary-button" to="/map">지도 검색 시작</RouterLink>
        <RouterLink class="secondary-button" to="/notices">공지사항 보기</RouterLink>
      </div>
    </div>

    <section class="overview-panel" aria-label="서비스 구성">
      <div>
        <span class="metric">01</span>
        <strong>지도 검색</strong>
        <p>지도 범위, 부동산 유형, 거래 유형을 기준으로 실거래 위치를 탐색합니다.</p>
      </div>
      <div>
        <span class="metric">02</span>
        <strong>상세 정보</strong>
        <p>최근 거래, 단지 요약, 관심 등록 상태, AI 제공 가능 여부를 분리해서 확인합니다.</p>
      </div>
      <div>
        <span class="metric">03</span>
        <strong>설명 가능한 추정</strong>
        <p>아파트 단지에 한해 추정가와 주요 영향 요인을 차트로 보여줄 예정입니다.</p>
      </div>
    </section>
  </section>
</template>

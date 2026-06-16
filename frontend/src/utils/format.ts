export function formatKrw(value?: number | null): string {
  if (value === undefined || value === null) {
    return '정보 없음'
  }

  if (value >= 100000000) {
    const eok = value / 100000000
    return `${eok.toLocaleString('ko-KR', { maximumFractionDigits: 1 })}억 원`
  }

  return `${value.toLocaleString('ko-KR')}원`
}

export function formatDate(value?: string | null): string {
  if (!value) {
    return '날짜 없음'
  }

  return new Intl.DateTimeFormat('ko-KR', {
    year: 'numeric',
    month: 'long',
    day: 'numeric'
  }).format(new Date(value))
}

export function transactionTypeLabel(value: string): string {
  const labels: Record<string, string> = {
    SALE: '매매',
    JEONSE: '전세',
    MONTHLY_RENT: '월세'
  }

  return labels[value] ?? value
}

export function propertyTypeLabel(value: string): string {
  const labels: Record<string, string> = {
    APARTMENT: '아파트',
    OFFICETEL: '오피스텔',
    VILLA: '빌라',
    HOUSE: '단독·다가구'
  }

  return labels[value] ?? value
}

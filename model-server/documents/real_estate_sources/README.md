# Real Estate RAG Sources

Downloaded on 2026-06-18.

These files are intended as explanatory/reference documents for the LLM. Raw structured data in `/Users/jihoyu/Downloads/data` should stay outside RAG and be passed to the LLM at question time as prediction/XAI context.

## Official Statistics And Reports

- `r_one_main.html` / `r_one_main.txt`
  - Source: https://www.reb.or.kr/r-one/portal/main/indexPage.do
  - Purpose: R-ONE service/menu context.
- `r_one_stat_meta.html` / `r_one_stat_meta.txt`
  - Source: https://www.reb.or.kr/r-one/portal/compose/statMetaExpPage.do
  - Purpose: statistical metadata page.
- `r_one_statistics_dictionary.html` / `r_one_statistics_dictionary.txt`
  - Source: https://www.reb.or.kr/r-one/portal/bbs/dic/searchBulletinPage.do
  - Purpose: statistical term dictionary page.
- `r_one_reports.html` / `r_one_reports.txt`
  - Source: https://www.reb.or.kr/r-one/portal/bbs/rpt/searchBulletinPage.do
  - Purpose: public report list page.
- `r_one_2026_05_housing_price_trend_report.pdf`
  - Source: R-ONE report seq `3928`, file `4804`
  - Title: 2026년 5월 전국주택가격동향조사 보고서
- `r_one_2026_04_apartment_actual_transaction_price_index_report.pdf`
  - Source: R-ONE report seq `3929`, file `4811`
  - Title: 2026년 4월 공동주택 실거래가격지수 보고서
- `r_one_2026_05_officetel_price_trend_report.pdf`
  - Source: R-ONE report seq `3926`, file `4802`
  - Title: 2026년 5월 오피스텔가격동향조사 보고서
- `r_one_2026_06_15_weekly_apartment_price_trend_table.xlsx`
  - Source: R-ONE report seq `3930`, file `4814`
  - Title: 2026년 6월 15일 기준 주간아파트가격 동향 통계표
  - Note: structured spreadsheet; do not ingest unless spreadsheet parsing is added intentionally.

## Actual Transaction And Price Service Context

- `rtms_main.html` / `rtms_main.txt`
  - Source: https://rtms.molit.go.kr/
  - Purpose: Ministry of Land, Infrastructure and Transport actual transaction price system page.
- `rtech_main.html` / `rtech_main.txt`
  - Source: https://www.rtech.or.kr/portal/main/indexPage.do
  - Purpose: REB Real Estate Tech service context.
- `rtech_faq.html` / `rtech_faq.txt`
  - Source: https://www.rtech.or.kr/portal/intro/bodFaq.do
  - Purpose: definitions for REB price, transaction price reflection, reference price, small-complex model price.
- `rtech_library.html` / `rtech_library.txt`
  - Source: https://www.rtech.or.kr/portal/info/bodLbry.do
  - Purpose: library page.
- `safe_jeonse_contract_checklist.pdf`
  - Source: https://www.rtech.or.kr/file/Safe_Jeonse_Contract_Checklist.pdf
  - Purpose: jeonse contract safety checklist.

## Laws

- `law_real_estate_transaction_reporting_detail.html` / `law_real_estate_transaction_reporting_detail.txt`
  - Source: https://www.law.go.kr/법령/부동산거래신고등에관한법률
  - Title: 부동산 거래신고 등에 관한 법률
  - Effective date captured: 2024-05-17.
- `law_housing_lease_protection_detail.html` / `law_housing_lease_protection_detail.txt`
  - Source: https://www.law.go.kr/법령/주택임대차보호법
  - Title: 주택임대차보호법
  - Effective date captured: 2026-01-02.
- `law_licensed_real_estate_agent_detail.html` / `law_licensed_real_estate_agent_detail.txt`
  - Source: https://www.law.go.kr/법령/공인중개사법
  - Title: 공인중개사법
  - Effective date captured: 2026-02-15.
- `law_apartment_management_detail.html` / `law_apartment_management_detail.txt`
  - Source: https://www.law.go.kr/법령/공동주택관리법
  - Title: 공동주택관리법
  - Effective date captured: 2026-06-03.

## Ingestion Note

The current `ingest.py` reads `.txt`, `.md`, and `.pdf`. The `.txt` copies and PDFs in this folder are ready for ingestion. The `.html` files are kept as source snapshots.


package dpm18th.team3bot.notion;

/**
 * 제목 검색·수동 요약용 최소 정보 (날짜 없어도 됨)
 */
public record NotionPageHit(String pageId, String title, String pageUrl) {}

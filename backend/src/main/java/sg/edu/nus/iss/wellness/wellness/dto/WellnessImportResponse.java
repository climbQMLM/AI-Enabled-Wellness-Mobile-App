package sg.edu.nus.iss.wellness.wellness.dto;

import java.time.LocalDate;

/**
 *
 * POST /api/wellness/import 的响应体，字段和形状严格对应
 * PROJECT_SPEC.md §3.4：
 * {"importedDates": 178, "updated": 12, "inserted": 166,
 *  "dateRange": ["2026-01-01","2026-06-29"], "skippedRows": 0}
 */
public record WellnessImportResponse(
        int importedDates,
        int updated,
        int inserted,
        LocalDate[] dateRange,
        int skippedRows
) {
}

package sg.edu.nus.iss.wellness.wellness.csv;

import java.time.LocalDate;
import java.util.Map;

/**
 *
 * 一份 CSV 文件解析后的结果：按日期分组的数据 + 解析过程中跳过的坏行数。
 * skippedRows 对应 PROJECT_SPEC.md §3.4 返回体里的 skippedRows 字段——
 * 单行解析失败（比如日期格式不对）不应该让整份文件解析失败，
 * 跳过该行、计数、继续解析下一行（见 §3.2 第4条容错要求）。
 */
public record CsvParseResult<T>(Map<LocalDate, T> rowsByDate, int skippedRows) {
}

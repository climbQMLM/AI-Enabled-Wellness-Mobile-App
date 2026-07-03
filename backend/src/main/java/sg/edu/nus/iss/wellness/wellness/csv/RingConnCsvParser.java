package sg.edu.nus.iss.wellness.wellness.csv;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 *
 * 解析 RingConn 导出的三种 CSV。实现要点对应 PROJECT_SPEC.md §3：
 *   - 用 Apache Commons CSV 而不是手写 split，天然处理 CRLF 换行
 *     和带引号字段（§3.2 第1条）
 *   - 按表头识别文件类型/列，不依赖文件名/列序号（§3）
 *   - 百分比字段 strip "%" 再转数字（§3.2 第2条）
 *   - Sleep 按 wake-date 聚合多段（§3.2 第3条，实际累加逻辑在 SleepAggRow 里）
 *   - 单行解析失败不让整份文件失败，跳过计数（§3.2 第4条）
 *   - 空值入库为 null，不填 0（§3.2 第5条）——本类里"解析失败/空白"统一返回 null，
 *     由调用方决定怎么用
 */
public final class RingConnCsvParser {

    private RingConnCsvParser() {
        // 工具类，不允许实例化
    }

    private static final CSVFormat FORMAT = CSVFormat.DEFAULT.builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .setIgnoreSurroundingSpaces(true)
            .setTrim(true)
            .get();

    /**
     * 看表头里有没有这几个文件特有的列名，来判断这是哪种 CSV。
     * 用"列名集合包含"而不是"表头完全相等"，是为了容忍未来 RingConn
     * 导出格式新增/调整列顺序、或多出几列我们不关心的数据。
     */
    public static RingConnCsvType detectType(Set<String> headers) {
        if (headers.contains("Steps") && headers.contains("Calories(kcal)")) {
            return RingConnCsvType.ACTIVITY;
        }
        if (headers.contains("Avg. Heart Rate(bpm)")) {
            return RingConnCsvType.VITAL_SIGNS;
        }
        if (headers.contains("Wake-up time") && headers.contains("Time Asleep(min)")) {
            return RingConnCsvType.SLEEP;
        }
        return RingConnCsvType.UNKNOWN;
    }

    public static CsvParseResult<ActivityRow> parseActivity(Reader reader) throws IOException {
        Map<LocalDate, ActivityRow> rows = new HashMap<>();
        int skipped = 0;

        try (CSVParser parser = FORMAT.parse(reader)) {
            for (CSVRecord record : parser) {
                try {
                    LocalDate date = LocalDate.parse(get(record, "Date"));
                    Integer steps = parseIntOrNull(get(record, "Steps"));
                    Integer calories = parseIntOrNull(get(record, "Calories(kcal)"));
                    rows.put(date, new ActivityRow(steps, calories));
                } catch (Exception rowError) {
                    skipped++; // 单行格式错误（比如日期解析失败），跳过这一行继续
                }
            }
        }
        return new CsvParseResult<>(rows, skipped);
    }

    public static CsvParseResult<VitalSignsRow> parseVitalSigns(Reader reader) throws IOException {
        Map<LocalDate, VitalSignsRow> rows = new HashMap<>();
        int skipped = 0;

        try (CSVParser parser = FORMAT.parse(reader)) {
            for (CSVRecord record : parser) {
                try {
                    LocalDate date = LocalDate.parse(get(record, "Date"));
                    VitalSignsRow row = new VitalSignsRow(
                            parseIntOrNull(get(record, "Avg. Heart Rate(bpm)")),
                            parseIntOrNull(get(record, "Min. Heart Rate(bpm)")),
                            parseIntOrNull(get(record, "Max. Heart Rate(bpm)")),
                            parseIntOrNull(get(record, "Avg. Spo2(%)")),   // 自带 % strip，见 parseIntOrNull
                            parseIntOrNull(get(record, "Min. Spo2(%)")),
                            parseIntOrNull(get(record, "Max. Spo2(%)")),
                            parseIntOrNull(get(record, "Avg. HRV(ms)")),
                            parseIntOrNull(get(record, "Min. HRV(ms)")),
                            parseIntOrNull(get(record, "Max. HRV(ms)"))
                    );
                    rows.put(date, row);
                } catch (Exception rowError) {
                    skipped++;
                }
            }
        }
        return new CsvParseResult<>(rows, skipped);
    }

    public static CsvParseResult<SleepAggRow> parseSleep(Reader reader) throws IOException {
        Map<LocalDate, SleepAggRow> rows = new HashMap<>();
        int skipped = 0;

        try (CSVParser parser = FORMAT.parse(reader)) {
            for (CSVRecord record : parser) {
                try {
                    // 归属日期 = Wake-up time 的日期部分，取前 10 个字符
                    // "2026-01-01 15:25:33" -> "2026-01-01"（PROJECT_SPEC.md §3.2 第3条）
                    String wakeUpRaw = get(record, "Wake-up time");
                    LocalDate wakeDate = LocalDate.parse(wakeUpRaw.substring(0, 10));

                    int timeAsleep = parseIntOrZero(get(record, "Time Asleep(min)"));
                    int deep = parseIntOrZero(get(record, "Sleep Stages - Deep Sleep(min)"));
                    int rem = parseIntOrZero(get(record, "Sleep Stages - REM(min)"));
                    int light = parseIntOrZero(get(record, "Sleep Stages - Light Sleep(min)"));
                    int awake = parseIntOrZero(get(record, "Sleep Stages - Awake(min)"));
                    BigDecimal ratio = parsePercentOrNull(get(record, "Sleep Time Ratio(%)"));

                    rows.computeIfAbsent(wakeDate, d -> new SleepAggRow())
                            .accumulate(timeAsleep, deep, rem, light, awake, ratio);
                } catch (Exception rowError) {
                    skipped++;
                }
            }
        }
        return new CsvParseResult<>(rows, skipped);
    }

    /** 列不存在（认不出的/缺失的列）或单元格为空，统一返回 null，调用方按容错处理 */
    private static String get(CSVRecord record, String column) {
        if (!record.isMapped(column)) {
            return null;
        }
        String value = record.get(column);
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    /** 去掉可能存在的 "%" 后缀，解析成整数；解析不了或值为空都返回 null（不是 0） */
    private static Integer parseIntOrNull(String raw) {
        if (raw == null) {
            return null;
        }
        String cleaned = raw.endsWith("%") ? raw.substring(0, raw.length() - 1) : raw;
        return Integer.parseInt(cleaned.trim());
    }

    /** Sleep 累加用：缺失值在"累加"语境下当 0 处理（不影响总和），跟"整行落库 NULL"是两回事 */
    private static int parseIntOrZero(String raw) {
        Integer value = parseIntOrNull(raw);
        return value == null ? 0 : value;
    }

    private static BigDecimal parsePercentOrNull(String raw) {
        if (raw == null) {
            return null;
        }
        String cleaned = raw.endsWith("%") ? raw.substring(0, raw.length() - 1) : raw;
        return new BigDecimal(cleaned.trim());
    }
}

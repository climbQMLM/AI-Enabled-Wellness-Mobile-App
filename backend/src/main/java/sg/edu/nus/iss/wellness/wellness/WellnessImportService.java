package sg.edu.nus.iss.wellness.wellness;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import sg.edu.nus.iss.wellness.common.ApiException;
import sg.edu.nus.iss.wellness.wellness.csv.ActivityRow;
import sg.edu.nus.iss.wellness.wellness.csv.CsvParseResult;
import sg.edu.nus.iss.wellness.wellness.csv.RingConnCsvParser;
import sg.edu.nus.iss.wellness.wellness.csv.RingConnCsvType;
import sg.edu.nus.iss.wellness.wellness.csv.SleepAggRow;
import sg.edu.nus.iss.wellness.wellness.csv.VitalSignsRow;
import sg.edu.nus.iss.wellness.wellness.dto.WellnessImportResponse;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;

/**
 *
 * RingConn CSV 导入的整体编排逻辑，对应 PROJECT_SPEC.md §3：
 *   1. 接收 zip 或多个 csv（§3.4 接口说明）
 *   2. 解压/分类/解析（具体解析细节在 wellness.csv 包里）
 *   3. 按日期合并三个来源（§3.3 合并逻辑）
 *   4. 字段级合并 upsert 进 wellness_logs（手动字段不被覆盖，见 PROJECT_SPEC.md §4）
 *   5. 重新计算 readiness_score（§6/§6.1）
 *   6. 返回导入摘要（§3.4）
 */
@Service
public class WellnessImportService {

    /** 一份"虚拟文件"——可能是用户直接上传的 csv，也可能是从 zip 里解出来的一个 entry */
    private record NamedBytes(String name, byte[] content) {
    }

    private final WellnessLogRepository repository;
    private final EntityManager entityManager;

    public WellnessImportService(WellnessLogRepository repository, EntityManager entityManager) {
        this.repository = repository;
        this.entityManager = entityManager;
    }

    @Transactional
    public WellnessImportResponse importFiles(Long userId, MultipartFile[] files) {
        if (files == null || files.length == 0) {
            throw ApiException.badRequest("no files uploaded");
        }

        List<NamedBytes> csvFiles = expandToCsvFiles(files);
        if (csvFiles.isEmpty()) {
            throw ApiException.badRequest("no .csv content found in upload (expected a zip or csv files)");
        }

        Map<LocalDate, ActivityRow> activityByDate = new HashMap<>();
        Map<LocalDate, VitalSignsRow> vitalByDate = new HashMap<>();
        Map<LocalDate, SleepAggRow> sleepByDate = new HashMap<>();
        int totalSkippedRows = 0;

        for (NamedBytes csv : csvFiles) {
            RingConnCsvType type = detectType(csv.content());

            try {
                switch (type) {
                    case ACTIVITY -> {
                        CsvParseResult<ActivityRow> result = RingConnCsvParser.parseActivity(readerOf(csv.content()));
                        activityByDate.putAll(result.rowsByDate());
                        totalSkippedRows += result.skippedRows();
                    }
                    case VITAL_SIGNS -> {
                        CsvParseResult<VitalSignsRow> result = RingConnCsvParser.parseVitalSigns(readerOf(csv.content()));
                        vitalByDate.putAll(result.rowsByDate());
                        totalSkippedRows += result.skippedRows();
                    }
                    case SLEEP -> {
                        CsvParseResult<SleepAggRow> result = RingConnCsvParser.parseSleep(readerOf(csv.content()));
                        // 同一个 wake-date 可能在多个 Sleep 文件里都出现（理论上不该发生，
                        // 但导入要容错），用 accumulate 累加而不是直接覆盖
                        result.rowsByDate().forEach((date, row) ->
                                sleepByDate.merge(date, row, (existing, incoming) -> {
                                    existing.accumulate(incoming.getTimeAsleepMin(), incoming.getDeepSleepMin(),
                                            incoming.getRemSleepMin(), incoming.getLightSleepMin(),
                                            incoming.getAwakeMin(), incoming.getSleepRatio());
                                    return existing;
                                }));
                        totalSkippedRows += result.skippedRows();
                    }
                    case UNKNOWN -> {
                        // 认不出的文件整份跳过，不算入 skippedRows（那个是"行级"计数），
                        // 也不让整个导入失败——见 PROJECT_SPEC.md §3.2 第4条的精神
                    }
                }
            } catch (IOException e) {
                throw ApiException.badRequest("failed to parse file " + csv.name() + ": " + e.getMessage());
            }
        }

        // 合并逻辑（§3.3）：日期集合是三个来源的并集；用 TreeSet 保证有序，
        // 方便后面直接取 first()/last() 算 dateRange
        TreeSet<LocalDate> allDates = new TreeSet<>();
        allDates.addAll(activityByDate.keySet());
        allDates.addAll(vitalByDate.keySet());
        allDates.addAll(sleepByDate.keySet());

        int inserted = 0;
        int updated = 0;

        for (LocalDate date : allDates) {
            var existing = repository.findByUserIdAndLogDate(userId, date);
            boolean isNew = existing.isEmpty();
            WellnessLog log = existing.orElseGet(() -> new WellnessLog(userId, date, WellnessLog.Source.ringconn));

            mergeActivity(log, activityByDate.get(date));
            mergeVitalSigns(log, vitalByDate.get(date));
            mergeSleep(log, sleepByDate.get(date));

            // 整行的 source 标记为 ringconn，表示"这一天包含导入数据"，
            // 不代表每个字段都来自导入——字段级来源已经在合并时尊重了手动数据
            // （见下面 mergeXxx 方法和 PROJECT_SPEC.md §4 导入接口说明）
            log.setSource(WellnessLog.Source.ringconn);
            log.setReadinessScore(
                    ReadinessCalculator.compute(log.getHrvAvg(), log.getHrMin(), log.getTimeAsleepMin())
            );

            repository.save(log);
            if (isNew) {
                inserted++;
            } else {
                updated++;
            }
        }

        entityManager.flush();

        LocalDate[] dateRange = allDates.isEmpty()
                ? new LocalDate[0]
                : new LocalDate[]{ allDates.first(), allDates.last() };

        return new WellnessImportResponse(allDates.size(), updated, inserted, dateRange, totalSkippedRows);
    }

    /**
     * 字段级合并：只填充当前为 NULL 的字段，已有非空值（通常是手动录入）不覆盖。
     * 见 PROJECT_SPEC.md §4 "POST /api/wellness/import" 的合并规则说明。
     */
    private void mergeActivity(WellnessLog log, ActivityRow row) {
        if (row == null) return;
        if (log.getSteps() == null) log.setSteps(row.steps());
        if (log.getCaloriesKcal() == null) log.setCaloriesKcal(row.caloriesKcal());
    }

    private void mergeVitalSigns(WellnessLog log, VitalSignsRow row) {
        if (row == null) return;
        if (log.getHrAvg() == null) log.setHrAvg(row.hrAvg());
        if (log.getHrMin() == null) log.setHrMin(row.hrMin());
        if (log.getHrMax() == null) log.setHrMax(row.hrMax());
        if (log.getSpo2Avg() == null) log.setSpo2Avg(row.spo2Avg());
        if (log.getSpo2Min() == null) log.setSpo2Min(row.spo2Min());
        if (log.getSpo2Max() == null) log.setSpo2Max(row.spo2Max());
        if (log.getHrvAvg() == null) log.setHrvAvg(row.hrvAvg());
        if (log.getHrvMin() == null) log.setHrvMin(row.hrvMin());
        if (log.getHrvMax() == null) log.setHrvMax(row.hrvMax());
    }

    private void mergeSleep(WellnessLog log, SleepAggRow row) {
        if (row == null) return;
        if (log.getTimeAsleepMin() == null) log.setTimeAsleepMin(row.getTimeAsleepMin());
        if (log.getDeepSleepMin() == null) log.setDeepSleepMin(row.getDeepSleepMin());
        if (log.getRemSleepMin() == null) log.setRemSleepMin(row.getRemSleepMin());
        if (log.getLightSleepMin() == null) log.setLightSleepMin(row.getLightSleepMin());
        if (log.getAwakeMin() == null) log.setAwakeMin(row.getAwakeMin());
        if (log.getSleepRatio() == null) log.setSleepRatio(row.getSleepRatio());
    }

    /**
     * 把上传的文件展开成一串"裸 csv 内容"：
     *   - 文件名以 .zip 结尾 → 解压，里面每个 .csv entry 都算一份
     *   - 文件名以 .csv 结尾 → 原样算一份
     *   - 其他一律忽略（比如 zip 里夹带的 macOS 元数据文件）
     */
    private List<NamedBytes> expandToCsvFiles(MultipartFile[] files) {
        List<NamedBytes> result = new ArrayList<>();

        for (MultipartFile file : files) {
            String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
            try {
                if (name.toLowerCase().endsWith(".zip")) {
                    result.addAll(extractCsvEntriesFromZip(file));
                } else if (name.toLowerCase().endsWith(".csv")) {
                    result.add(new NamedBytes(name, file.getBytes()));
                }
                // 其它扩展名（比如误传的 .xlsx）直接忽略，不报错中断整个导入
            } catch (IOException e) {
                throw ApiException.badRequest("failed to read uploaded file " + name + ": " + e.getMessage());
            }
        }
        return result;
    }

    private List<NamedBytes> extractCsvEntriesFromZip(MultipartFile zipFile) throws IOException {
        List<NamedBytes> entries = new ArrayList<>();

        try (ZipInputStream zis = new ZipInputStream(zipFile.getInputStream())) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String entryName = entry.getName();
                boolean looksLikeJunk = entryName.contains("__MACOSX") || entryName.startsWith(".");

                if (!entry.isDirectory() && entryName.toLowerCase().endsWith(".csv") && !looksLikeJunk) {
                    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                    zis.transferTo(buffer);
                    entries.add(new NamedBytes(entryName, buffer.toByteArray()));
                }
                zis.closeEntry();
            }
        }
        return entries;
    }

    /** 只读一遍表头来判断文件类型，不消耗掉给真正解析用的那份 Reader */
    private RingConnCsvType detectType(byte[] content) {
        CSVFormat headerOnlyFormat = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .get();

        try (CSVParser parser = headerOnlyFormat.parse(readerOf(content))) {
            Set<String> headers = new HashSet<>(parser.getHeaderNames());
            return RingConnCsvParser.detectType(headers);
        } catch (IOException e) {
            return RingConnCsvType.UNKNOWN;
        }
    }

    private Reader readerOf(byte[] content) {
        return new InputStreamReader(new ByteArrayInputStream(content), StandardCharsets.UTF_8);
    }
}

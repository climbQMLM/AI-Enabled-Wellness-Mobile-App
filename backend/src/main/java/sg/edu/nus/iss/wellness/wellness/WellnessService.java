package sg.edu.nus.iss.wellness.wellness;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sg.edu.nus.iss.wellness.common.ApiException;
import sg.edu.nus.iss.wellness.wellness.dto.WellnessLogCreateRequest;
import sg.edu.nus.iss.wellness.wellness.dto.WellnessLogPatchRequest;
import sg.edu.nus.iss.wellness.wellness.dto.WellnessLogResponse;

import java.time.LocalDate;
import java.util.List;

/**
 *
 * Wellness CRUD 的业务逻辑，对应 PROJECT_SPEC.md §4。
 * 所有方法都要求 userId——数据隔离的边界在这一层，不在 Controller。
 */
@Service
public class WellnessService {

    /** GET 不传 from/to 时默认查最近 30 天，见 PROJECT_SPEC.md §4 */
    private static final long DEFAULT_RANGE_DAYS = 30;

    private final WellnessLogRepository repository;
    private final EntityManager entityManager;

    public WellnessService(WellnessLogRepository repository, EntityManager entityManager) {
        this.repository = repository;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public List<WellnessLogResponse> list(Long userId, LocalDate from, LocalDate to) {
        LocalDate effectiveTo = (to != null) ? to : LocalDate.now();
        LocalDate effectiveFrom = (from != null) ? from : effectiveTo.minusDays(DEFAULT_RANGE_DAYS - 1);

        if (effectiveFrom.isAfter(effectiveTo)) {
            throw ApiException.badRequest("'from' must not be after 'to'");
        }

        return repository.findByUserIdAndLogDateBetweenOrderByLogDateAsc(userId, effectiveFrom, effectiveTo)
                .stream()
                .map(WellnessLogResponse::from)
                .toList();
    }

    @Transactional
    public WellnessLogResponse create(Long userId, WellnessLogCreateRequest request) {
        repository.findByUserIdAndLogDate(userId, request.logDate()).ifPresent(existing -> {
            throw ApiException.conflict("wellness log already exists for this date, use PUT to update");
        });

        WellnessLog log = new WellnessLog(userId, request.logDate(), WellnessLog.Source.manual);
        log.setSteps(request.steps());
        log.setCaloriesKcal(request.caloriesKcal());
        log.setHrAvg(request.hrAvg());
        log.setHrMin(request.hrMin());
        log.setHrMax(request.hrMax());
        log.setSpo2Avg(request.spo2Avg());
        log.setSpo2Min(request.spo2Min());
        log.setSpo2Max(request.spo2Max());
        log.setHrvAvg(request.hrvAvg());
        log.setHrvMin(request.hrvMin());
        log.setHrvMax(request.hrvMax());
        log.setTimeAsleepMin(request.timeAsleepMin());
        log.setDeepSleepMin(request.deepSleepMin());
        log.setRemSleepMin(request.remSleepMin());
        log.setLightSleepMin(request.lightSleepMin());
        log.setAwakeMin(request.awakeMin());
        log.setSleepRatio(request.sleepRatio());
        recomputeReadiness(log);

        WellnessLog saved = repository.save(log);
        // created_at/updated_at 是数据库 DEFAULT CURRENT_TIMESTAMP 自动生成的，
        // insert 后 Hibernate 不会自动把这两个字段的真实值回填到 Java 对象里
        // （entity 里它们是 insertable=false），所以这里手动 flush+refresh 一次，
        // 确保 POST 的响应体里 createdAt/updatedAt 不是 null。
        entityManager.flush();
        entityManager.refresh(saved);

        return WellnessLogResponse.from(saved);
    }

    @Transactional
    public WellnessLogResponse patch(Long userId, Long id, WellnessLogPatchRequest request) {
        WellnessLog log = repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> ApiException.notFound("wellness log not found"));

        // PATCH 语义：只有非 null 的字段才覆盖，见 dto/WellnessLogPatchRequest 注释
        if (request.steps() != null) log.setSteps(request.steps());
        if (request.caloriesKcal() != null) log.setCaloriesKcal(request.caloriesKcal());
        if (request.hrAvg() != null) log.setHrAvg(request.hrAvg());
        if (request.hrMin() != null) log.setHrMin(request.hrMin());
        if (request.hrMax() != null) log.setHrMax(request.hrMax());
        if (request.spo2Avg() != null) log.setSpo2Avg(request.spo2Avg());
        if (request.spo2Min() != null) log.setSpo2Min(request.spo2Min());
        if (request.spo2Max() != null) log.setSpo2Max(request.spo2Max());
        if (request.hrvAvg() != null) log.setHrvAvg(request.hrvAvg());
        if (request.hrvMin() != null) log.setHrvMin(request.hrvMin());
        if (request.hrvMax() != null) log.setHrvMax(request.hrvMax());
        if (request.timeAsleepMin() != null) log.setTimeAsleepMin(request.timeAsleepMin());
        if (request.deepSleepMin() != null) log.setDeepSleepMin(request.deepSleepMin());
        if (request.remSleepMin() != null) log.setRemSleepMin(request.remSleepMin());
        if (request.lightSleepMin() != null) log.setLightSleepMin(request.lightSleepMin());
        if (request.awakeMin() != null) log.setAwakeMin(request.awakeMin());
        if (request.sleepRatio() != null) log.setSleepRatio(request.sleepRatio());
        recomputeReadiness(log);

        // 不用显式调用 save()：log 是这个事务内从 repository 查出来的受管实体(managed entity)，
        // 事务提交时 Hibernate 的 dirty checking 会自动把字段变化 flush 成 UPDATE 语句。
        return WellnessLogResponse.from(log);
    }

    @Transactional
    public void delete(Long userId, Long id) {
        WellnessLog log = repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> ApiException.notFound("wellness log not found"));
        repository.delete(log);
    }

    /** 每次写入(create/patch)后都要重新算 readiness，公式见 PROJECT_SPEC.md §6/§6.1 */
    private void recomputeReadiness(WellnessLog log) {
        log.setReadinessScore(
                ReadinessCalculator.compute(log.getHrvAvg(), log.getHrMin(), log.getTimeAsleepMin())
        );
    }
}

package sg.edu.nus.iss.wellness.wellness;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 *
 * 所有查询都带 userId 条件——这是多租户数据隔离的关键，绝对不能漏写，
 * 否则用户 A 可能查到/改到/删到用户 B 的数据。
 */
public interface WellnessLogRepository extends JpaRepository<WellnessLog, Long> {

    Optional<WellnessLog> findByUserIdAndLogDate(Long userId, LocalDate logDate);

    List<WellnessLog> findByUserIdAndLogDateBetweenOrderByLogDateAsc(Long userId, LocalDate from, LocalDate to);

    /** PUT/DELETE 前用这个查，同时校验"这条记录存在"和"属于当前用户"两件事 */
    Optional<WellnessLog> findByIdAndUserId(Long id, Long userId);
}

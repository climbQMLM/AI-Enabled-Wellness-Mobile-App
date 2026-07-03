package sg.edu.nus.iss.wellness.agent;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

/**
 *
 * recommendations 表的数据访问层。
 *
 * findByUserIdAndRecDateBetween：用于 GET /api/recommendations?from&to
 * findAllUserIds：Scheduler 定时任务需要遍历所有用户，用这个拿 ID 列表
 */
public interface RecommendationRepository extends JpaRepository<Recommendation, Long> {

    List<Recommendation> findByUserIdAndRecDateBetweenOrderByRecDateDesc(
            Long userId, LocalDate from, LocalDate to);
}

package sg.edu.nus.iss.wellness.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 *
 * Spring Data JPA 会根据方法名自动生成 SQL，不用手写实现。
 * findByEmail 是登录/注册查重的核心查询，依赖 users.email 上的 UNIQUE 索引。
 */
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}

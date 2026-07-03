package sg.edu.nus.iss.wellness.wellness;

import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import sg.edu.nus.iss.wellness.user.User;
import sg.edu.nus.iss.wellness.wellness.dto.WellnessImportResponse;
import sg.edu.nus.iss.wellness.wellness.dto.WellnessLogCreateRequest;
import sg.edu.nus.iss.wellness.wellness.dto.WellnessLogPatchRequest;
import sg.edu.nus.iss.wellness.wellness.dto.WellnessLogResponse;

import java.time.LocalDate;
import java.util.List;

/**
 *
 * 对应 PROJECT_SPEC.md §4 Wellness CRUD 和 §3 RingConn CSV 导入。
 * @AuthenticationPrincipal User 能直接拿到当前登录用户——见
 * JwtAuthFilter，鉴权通过后写进 SecurityContext 的 principal 就是
 * sg.edu.nus.iss.wellness.user.User 实体本身。
 */
@RestController
@RequestMapping("/api/wellness")
public class WellnessController {

    private final WellnessService wellnessService;
    private final WellnessImportService wellnessImportService;

    public WellnessController(WellnessService wellnessService,
                              WellnessImportService wellnessImportService) {
        this.wellnessService = wellnessService;
        this.wellnessImportService = wellnessImportService;
    }

    @GetMapping
    public List<WellnessLogResponse> list(
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return wellnessService.list(user.getId(), from, to);
    }

    @PostMapping
    public ResponseEntity<WellnessLogResponse> create(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody WellnessLogCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(wellnessService.create(user.getId(), request));
    }

    @PutMapping("/{id}")
    public WellnessLogResponse patch(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @RequestBody WellnessLogPatchRequest request) {
        return wellnessService.patch(user.getId(), id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal User user, @PathVariable Long id) {
        wellnessService.delete(user.getId(), id);
        return ResponseEntity.noContent().build();
    }

    /**
     * POST /api/wellness/import
     *
     * 接收 multipart/form-data，参数名 "files"，支持：
     *   - 单个 .zip（里面包含多个 csv，常见于 RingConn App 的"导出全部"操作）
     *   - 多个 .csv 直接上传（Activity / Vital Signs / Sleep 三种任意组合）
     *   - 混合上传（zip + csv 同时传）
     *
     * 内部由 WellnessImportService 负责解压、分类、解析、字段级合并 upsert。
     * 返回导入摘要（PROJECT_SPEC.md §3.4），HTTP 200。
     */
    @PostMapping("/import")
    public WellnessImportResponse importCsv(
            @AuthenticationPrincipal User user,
            @RequestParam("files") MultipartFile[] files) {
        return wellnessImportService.importFiles(user.getId(), files);
    }
}

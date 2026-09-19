package cn.sfj.jiaowutong.web;

import cn.sfj.jiaowutong.common.ApiResult;
import cn.sfj.jiaowutong.domain.ReleaseAssessmentStatus;
import cn.sfj.jiaowutong.security.CurrentUserHolder;
import cn.sfj.jiaowutong.security.LoginUser;
import cn.sfj.jiaowutong.service.ReleaseAssessmentService;
import cn.sfj.jiaowutong.web.dto.ReleaseAssessmentRequest;
import cn.sfj.jiaowutong.web.dto.ReleaseDecisionRequest;
import cn.sfj.jiaowutong.web.vo.ReleaseAssessmentView;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 解除与评估（干警/监管员）：
 * - GET  /release/due                     到期/临期对象名单（按对象司法所时区判定）
 * - POST /release/objects/{id}/assessment 生成评估报告（客观数据固化）
 * - GET  /release/assessments             评估报告列表
 * - GET  /release/assessments/{id}        报告详情（每一步留痕）
 * - POST /release/assessments/{id}/draft  修改鉴定意见/结论（待完善/驳回态）
 * - POST /release/assessments/{id}/submit 提交评估
 * - POST /release/assessments/{id}/approve 审批通过
 * - POST /release/assessments/{id}/reject  审批驳回（必填理由）
 * - POST /release/assessments/{id}/execute 执行解除（永久标记 + 位置冻结 + 红点核销）
 */
@RestController
@RequestMapping("/api/release")
public class ReleaseController {

    private final ReleaseAssessmentService releaseService;

    public ReleaseController(ReleaseAssessmentService releaseService) {
        this.releaseService = releaseService;
    }

    @GetMapping("/due")
    public ApiResult<List<ReleaseAssessmentView.DueItem>> due() {
        LoginUser user = CurrentUserHolder.require();
        return ApiResult.success(releaseService.dueList(user));
    }

    @PostMapping("/objects/{id}/assessment")
    public ApiResult<ReleaseAssessmentView> generate(@PathVariable Long id) {
        LoginUser user = CurrentUserHolder.require();
        return ApiResult.success(releaseService.generate(id, user));
    }

    @GetMapping("/assessments")
    public ApiResult<List<ReleaseAssessmentView>> list(@RequestParam(required = false) ReleaseAssessmentStatus status,
                                                       @RequestParam(required = false) Long officeId) {
        LoginUser user = CurrentUserHolder.require();
        return ApiResult.success(releaseService.list(status, officeId, user));
    }

    @GetMapping("/assessments/{id}")
    public ApiResult<ReleaseAssessmentView.DetailView> detail(@PathVariable Long id) {
        LoginUser user = CurrentUserHolder.require();
        return ApiResult.success(releaseService.detail(id, user));
    }

    @PostMapping("/assessments/{id}/draft")
    public ApiResult<ReleaseAssessmentView> draft(@PathVariable Long id,
                                                  @Valid @RequestBody ReleaseAssessmentRequest request) {
        LoginUser user = CurrentUserHolder.require();
        return ApiResult.success(releaseService.update(id, request, user));
    }

    @PostMapping("/assessments/{id}/submit")
    public ApiResult<ReleaseAssessmentView> submit(@PathVariable Long id,
                                                   @Valid @RequestBody(required = false) ReleaseDecisionRequest request) {
        LoginUser user = CurrentUserHolder.require();
        return ApiResult.success(releaseService.submit(id,
                request == null ? new ReleaseDecisionRequest(null) : request, user));
    }

    @PostMapping("/assessments/{id}/approve")
    public ApiResult<ReleaseAssessmentView> approve(@PathVariable Long id,
                                                    @Valid @RequestBody(required = false) ReleaseDecisionRequest request) {
        LoginUser user = CurrentUserHolder.require();
        return ApiResult.success(releaseService.approve(id,
                request == null ? new ReleaseDecisionRequest(null) : request, user));
    }

    @PostMapping("/assessments/{id}/reject")
    public ApiResult<ReleaseAssessmentView> reject(@PathVariable Long id,
                                                   @Valid @RequestBody ReleaseDecisionRequest request) {
        LoginUser user = CurrentUserHolder.require();
        return ApiResult.success(releaseService.reject(id, request, user));
    }

    @PostMapping("/assessments/{id}/execute")
    public ApiResult<ReleaseAssessmentView> execute(@PathVariable Long id,
                                                    @Valid @RequestBody(required = false) ReleaseDecisionRequest request) {
        LoginUser user = CurrentUserHolder.require();
        return ApiResult.success(releaseService.execute(id,
                request == null ? new ReleaseDecisionRequest(null) : request, user));
    }

    /** 按矫正编号检索已归档档案（解除后档案仍可查） */
    @GetMapping("/archive/lookup")
    public ApiResult<Map<String, Object>> archiveLookup(@RequestParam String correctionNo) {
        LoginUser user = CurrentUserHolder.require();
        return ApiResult.success(releaseService.archiveLookup(correctionNo, user));
    }
}

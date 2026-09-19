package cn.sfj.jiaowutong.web;

import cn.sfj.jiaowutong.common.ApiResult;
import cn.sfj.jiaowutong.security.CurrentUserHolder;
import cn.sfj.jiaowutong.security.LoginUser;
import cn.sfj.jiaowutong.service.ReleaseService;
import cn.sfj.jiaowutong.web.dto.AssessmentFormRequest;
import cn.sfj.jiaowutong.web.dto.GenerateAssessmentRequest;
import cn.sfj.jiaowutong.web.dto.ReleaseReasonRequest;
import cn.sfj.jiaowutong.web.vo.ReleaseAssessmentView;
import cn.sfj.jiaowutong.web.vo.ReleaseDueItem;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 解除与评估（干警/监管员）：
 * - GET  /release/due                  到期清单（含是否已在评估流程）
 * - POST /release/assessments          为到期对象生成评估报告
 * - GET  /release/assessments/{id}     评估报告详情 + 考核双口径 + 留痕
 * - PUT  /release/assessments/{id}     保存/补正报告内容
 * - POST /release/assessments/{id}/submit   提交审批
 * - POST /release/assessments/{id}/approve  审批通过（须意见）
 * - POST /release/assessments/{id}/return   退回补正（须意见）
 * - POST /release/assessments/{id}/declare  宣告解除（打永久标记、冻结定位、关红点）
 */
@RestController
@RequestMapping("/api/release")
public class ReleaseController {

    private final ReleaseService releaseService;

    public ReleaseController(ReleaseService releaseService) {
        this.releaseService = releaseService;
    }

    @GetMapping("/due")
    public ApiResult<List<ReleaseDueItem>> due() {
        LoginUser user = CurrentUserHolder.require();
        return ApiResult.success(releaseService.dueList(user));
    }

    @PostMapping("/assessments")
    public ApiResult<ReleaseAssessmentView> generate(@Valid @RequestBody GenerateAssessmentRequest req) {
        LoginUser user = CurrentUserHolder.require();
        return ApiResult.success(releaseService.generate(req.objectId(), user));
    }

    @GetMapping("/assessments/{id}")
    public ApiResult<ReleaseAssessmentView> detail(@PathVariable Long id) {
        LoginUser user = CurrentUserHolder.require();
        return ApiResult.success(releaseService.detail(id, user));
    }

    @PutMapping("/assessments/{id}")
    public ApiResult<ReleaseAssessmentView> saveDraft(@PathVariable Long id,
                                                      @Valid @RequestBody AssessmentFormRequest form) {
        LoginUser user = CurrentUserHolder.require();
        return ApiResult.success(releaseService.saveDraft(id, form, user));
    }

    @PostMapping("/assessments/{id}/submit")
    public ApiResult<ReleaseAssessmentView> submit(@PathVariable Long id,
                                                   @RequestBody(required = false) ReleaseReasonRequest req) {
        LoginUser user = CurrentUserHolder.require();
        return ApiResult.success(releaseService.submit(id, req == null ? null : req.reason(), user));
    }

    @PostMapping("/assessments/{id}/approve")
    public ApiResult<ReleaseAssessmentView> approve(@PathVariable Long id,
                                                    @Valid @RequestBody ReleaseReasonRequest req) {
        LoginUser user = CurrentUserHolder.require();
        return ApiResult.success(releaseService.decide(id, true, req.reason(), user));
    }

    @PostMapping("/assessments/{id}/return")
    public ApiResult<ReleaseAssessmentView> returnBack(@PathVariable Long id,
                                                       @Valid @RequestBody ReleaseReasonRequest req) {
        LoginUser user = CurrentUserHolder.require();
        return ApiResult.success(releaseService.decide(id, false, req.reason(), user));
    }

    @PostMapping("/assessments/{id}/declare")
    public ApiResult<ReleaseAssessmentView> declare(@PathVariable Long id,
                                                    @Valid @RequestBody ReleaseReasonRequest req) {
        LoginUser user = CurrentUserHolder.require();
        return ApiResult.success(releaseService.declare(id, req.reason(), user));
    }

    /** 便捷：按对象取其最新一份解除评估（档案页入口用；无则 data 为 null）。 */
    @GetMapping("/objects/{objectId}/latest")
    public ApiResult<ReleaseAssessmentView> latestForObject(@PathVariable Long objectId) {
        LoginUser user = CurrentUserHolder.require();
        return ApiResult.success(releaseService.latestForObject(objectId, user));
    }
}

package cn.sfj.jiaowutong.web;

import cn.sfj.jiaowutong.common.ApiResult;
import cn.sfj.jiaowutong.domain.ViolationActionType;
import cn.sfj.jiaowutong.domain.ViolationCaseStatus;
import cn.sfj.jiaowutong.security.CurrentUserHolder;
import cn.sfj.jiaowutong.security.LoginUser;
import cn.sfj.jiaowutong.service.ViolationCaseService;
import cn.sfj.jiaowutong.web.dto.CaseActionRequest;
import cn.sfj.jiaowutong.web.dto.RegisterCaseRequest;
import cn.sfj.jiaowutong.web.vo.ViolationCaseView;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 违规处置（干警/监管员）：
 * - GET  /violations/cases                 案件列表（status / officeId 过滤，按数据范围裁剪）
 * - POST /violations/cases                 登记受理（原始红点或人工事由；时间窗合并）
 * - GET  /violations/cases/{id}            案件详情（每一步留痕 + 所附预警）
 * - POST /violations/cases/{id}/admonish   予以训诫（驱动矫正状态机 → 训诫）
 * - POST /violations/cases/{id}/reimprison 提请收监（驱动矫正状态机 → 收监）
 * - POST /violations/cases/{id}/reject     驳回（必填理由，红点核销）
 * - POST /violations/cases/{id}/revoke     撤销（必填理由，红点核销）
 */
@RestController
@RequestMapping("/api/violations")
public class ViolationCaseController {

    private final ViolationCaseService caseService;

    public ViolationCaseController(ViolationCaseService caseService) {
        this.caseService = caseService;
    }

    @GetMapping("/cases")
    public ApiResult<List<ViolationCaseView>> list(@RequestParam(required = false) ViolationCaseStatus status,
                                                   @RequestParam(required = false) Long officeId) {
        LoginUser user = CurrentUserHolder.require();
        return ApiResult.success(caseService.list(status, officeId, user));
    }

    @PostMapping("/cases")
    public ApiResult<ViolationCaseView> register(@Valid @RequestBody RegisterCaseRequest request) {
        LoginUser user = CurrentUserHolder.require();
        return ApiResult.success(caseService.register(request, user));
    }

    @GetMapping("/cases/{id}")
    public ApiResult<ViolationCaseView.DetailView> detail(@PathVariable Long id) {
        LoginUser user = CurrentUserHolder.require();
        return ApiResult.success(caseService.detail(id, user));
    }

    @PostMapping("/cases/{id}/admonish")
    public ApiResult<ViolationCaseView> admonish(@PathVariable Long id,
                                                 @Valid @RequestBody CaseActionRequest request) {
        return ApiResult.success(caseService.act(id, ViolationActionType.ADMONISH, request.reason(),
                CurrentUserHolder.require()));
    }

    @PostMapping("/cases/{id}/reimprison")
    public ApiResult<ViolationCaseView> reimprison(@PathVariable Long id,
                                                   @Valid @RequestBody CaseActionRequest request) {
        return ApiResult.success(caseService.act(id, ViolationActionType.REIMPRISON, request.reason(),
                CurrentUserHolder.require()));
    }

    @PostMapping("/cases/{id}/reject")
    public ApiResult<ViolationCaseView> reject(@PathVariable Long id,
                                               @Valid @RequestBody CaseActionRequest request) {
        LoginUser user = CurrentUserHolder.require();
        return ApiResult.success(caseService.act(id, ViolationActionType.REJECT, request.reason(), user));
    }

    @PostMapping("/cases/{id}/revoke")
    public ApiResult<ViolationCaseView> revoke(@PathVariable Long id,
                                               @Valid @RequestBody CaseActionRequest request) {
        LoginUser user = CurrentUserHolder.require();
        return ApiResult.success(caseService.act(id, ViolationActionType.REVOKE, request.reason(), user));
    }
}

package cn.sfj.jiaowutong.web;

import cn.sfj.jiaowutong.common.ApiResult;
import cn.sfj.jiaowutong.domain.DisposalStatus;
import cn.sfj.jiaowutong.security.CurrentUserHolder;
import cn.sfj.jiaowutong.security.LoginUser;
import cn.sfj.jiaowutong.service.DisposalService;
import cn.sfj.jiaowutong.web.dto.DisposalActionRequest;
import cn.sfj.jiaowutong.web.dto.RegisterDisposalRequest;
import cn.sfj.jiaowutong.web.dto.RegisterFromEventsRequest;
import cn.sfj.jiaowutong.web.vo.DisposalDetailView;
import cn.sfj.jiaowutong.web.vo.DisposalView;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 违规处置（干警/监管员）：
 * - GET  /disposals                    处置单列表（可按状态过滤，默认含全部）
 * - GET  /disposals/pending-count      待处置数量（作战台角标）
 * - GET  /disposals/{id}               处置单详情 + 合并事件 + 逐步留痕
 * - POST /disposals/from-events        把红点事件登记为处置单（时间窗内自动并入同一单）
 * - POST /disposals/manual             手工登记处置单
 * - POST /disposals/{id}/admonish      予以训诫（联动档案状态机）
 * - POST /disposals/{id}/reimprison    提请收监（联动档案状态机）
 * - POST /disposals/{id}/reject        驳回（不构成违规）
 * - POST /disposals/{id}/revoke        撤销（登记有误，红点退回）
 */
@RestController
@RequestMapping("/api/disposals")
public class DisposalController {

    private final DisposalService disposalService;

    public DisposalController(DisposalService disposalService) {
        this.disposalService = disposalService;
    }

    @GetMapping
    public ApiResult<List<DisposalView>> list(@RequestParam(required = false) DisposalStatus status,
                                              @RequestParam(required = false) Long objectId) {
        LoginUser user = CurrentUserHolder.require();
        return ApiResult.success(disposalService.list(status, objectId, user));
    }

    /** 待登记受理的红点（处置中心首栏）。 */
    @GetMapping("/open-events")
    public ApiResult<List<cn.sfj.jiaowutong.web.vo.DashboardView.RedDotItem>> openEvents() {
        LoginUser user = CurrentUserHolder.require();
        return ApiResult.success(disposalService.openEvents(user));
    }

    @GetMapping("/pending-count")
    public ApiResult<java.util.Map<String, Object>> pendingCount() {
        LoginUser user = CurrentUserHolder.require();
        return ApiResult.success(java.util.Map.of("count", disposalService.pendingCount(user)));
    }

    @GetMapping("/{id}")
    public ApiResult<DisposalDetailView> detail(@PathVariable Long id) {
        LoginUser user = CurrentUserHolder.require();
        return ApiResult.success(disposalService.detail(id, user));
    }

    @PostMapping("/from-events")
    public ApiResult<DisposalDetailView> fromEvents(@Valid @RequestBody RegisterFromEventsRequest req) {
        LoginUser user = CurrentUserHolder.require();
        return ApiResult.success(disposalService.registerFromEvents(
                req.objectId(), parseCategory(req.category()), req.eventIds(),
                req.title(), req.detail(), req.reason(), user));
    }

    @PostMapping("/manual")
    public ApiResult<DisposalDetailView> manual(@Valid @RequestBody RegisterDisposalRequest req) {
        LoginUser user = CurrentUserHolder.require();
        return ApiResult.success(disposalService.registerManual(req, user));
    }

    @PostMapping("/{id}/admonish")
    public ApiResult<DisposalDetailView> admonish(@PathVariable Long id,
                                                  @Valid @RequestBody DisposalActionRequest req) {
        return advance(id, "ADMONISH", req);
    }

    @PostMapping("/{id}/reimprison")
    public ApiResult<DisposalDetailView> reimprison(@PathVariable Long id,
                                                    @Valid @RequestBody DisposalActionRequest req) {
        return advance(id, "REIMPRISON", req);
    }

    @PostMapping("/{id}/reject")
    public ApiResult<DisposalDetailView> reject(@PathVariable Long id,
                                                @Valid @RequestBody DisposalActionRequest req) {
        return advance(id, "REJECT", req);
    }

    @PostMapping("/{id}/revoke")
    public ApiResult<DisposalDetailView> revoke(@PathVariable Long id,
                                                @Valid @RequestBody DisposalActionRequest req) {
        return advance(id, "REVOKE", req);
    }

    private ApiResult<DisposalDetailView> advance(Long id, String action, DisposalActionRequest req) {
        LoginUser user = CurrentUserHolder.require();
        return ApiResult.success(disposalService.advance(id, action, req.reason(), user));
    }

    private cn.sfj.jiaowutong.domain.DisposalCategory parseCategory(String raw) {
        try {
            return cn.sfj.jiaowutong.domain.DisposalCategory.valueOf(raw);
        } catch (Exception e) {
            throw new cn.sfj.jiaowutong.common.ApiException(
                    "VALIDATION_ERROR", "违规事由非法：" + raw);
        }
    }
}

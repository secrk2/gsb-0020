package cn.sfj.jiaowutong.service;

import cn.sfj.jiaowutong.common.ApiException;
import cn.sfj.jiaowutong.domain.ReleaseAssessmentStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReleaseAssessmentStateMachineTest {

    @Test
    void legalPathDraftToDone() {
        assertDoesNotThrow(() -> ReleaseAssessmentStateMachine.assertTransition(
                ReleaseAssessmentStatus.DRAFT, ReleaseAssessmentStatus.SUBMITTED));
        assertDoesNotThrow(() -> ReleaseAssessmentStateMachine.assertTransition(
                ReleaseAssessmentStatus.SUBMITTED, ReleaseAssessmentStatus.APPROVED));
        assertDoesNotThrow(() -> ReleaseAssessmentStateMachine.assertTransition(
                ReleaseAssessmentStatus.APPROVED, ReleaseAssessmentStatus.DONE));
    }

    @Test
    void rejectCanBeResubmitted() {
        assertDoesNotThrow(() -> ReleaseAssessmentStateMachine.assertTransition(
                ReleaseAssessmentStatus.SUBMITTED, ReleaseAssessmentStatus.REJECTED));
        assertDoesNotThrow(() -> ReleaseAssessmentStateMachine.assertTransition(
                ReleaseAssessmentStatus.REJECTED, ReleaseAssessmentStatus.SUBMITTED));
    }

    @Test
    void cannotApproveDraftOrExecuteBeforeApproval() {
        assertEquals("INVALID_ACTION", assertThrows(ApiException.class, () ->
                ReleaseAssessmentStateMachine.assertTransition(
                        ReleaseAssessmentStatus.DRAFT, ReleaseAssessmentStatus.APPROVED)).getCode());
        assertEquals("INVALID_ACTION", assertThrows(ApiException.class, () ->
                ReleaseAssessmentStateMachine.assertTransition(
                        ReleaseAssessmentStatus.SUBMITTED, ReleaseAssessmentStatus.DONE)).getCode());
    }

    @Test
    void doneIsTerminal() {
        assertEquals("INVALID_ACTION", assertThrows(ApiException.class, () ->
                ReleaseAssessmentStateMachine.assertTransition(
                        ReleaseAssessmentStatus.DONE, ReleaseAssessmentStatus.SUBMITTED)).getCode());
    }
}

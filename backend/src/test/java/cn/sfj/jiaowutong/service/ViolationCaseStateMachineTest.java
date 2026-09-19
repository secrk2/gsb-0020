package cn.sfj.jiaowutong.service;

import cn.sfj.jiaowutong.common.ApiException;
import cn.sfj.jiaowutong.domain.ViolationCaseStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ViolationCaseStateMachineTest {

    @Test
    void registeredCanTakeEveryDisposition() {
        for (ViolationCaseStatus to : new ViolationCaseStatus[]{
                ViolationCaseStatus.ADMONISHED, ViolationCaseStatus.REIMPRISONED,
                ViolationCaseStatus.REJECTED, ViolationCaseStatus.REVOKED}) {
            assertDoesNotThrow(() -> ViolationCaseStateMachine.assertAction(
                    ViolationCaseStatus.REGISTERED, to));
        }
    }

    @Test
    void terminalStatesRejectAnyFurtherAction() {
        for (ViolationCaseStatus closed : new ViolationCaseStatus[]{
                ViolationCaseStatus.ADMONISHED, ViolationCaseStatus.REIMPRISONED,
                ViolationCaseStatus.REJECTED, ViolationCaseStatus.REVOKED}) {
            ApiException ex = assertThrows(ApiException.class, () -> ViolationCaseStateMachine
                    .assertAction(closed, ViolationCaseStatus.REGISTERED));
            assertEquals("INVALID_ACTION", ex.getCode());
            assertTrue(ex.getMessage().contains("不允许再次处置"));
        }
    }
}

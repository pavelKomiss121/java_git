/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.transaction.model;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class IsolationTestReport {
    private int isolationLevel;
    private IsolationTestResult dirtyReadTest;
    private IsolationTestResult nonRepeatableReadTest;
    private IsolationTestResult phantomReadTest;
    private List<String> recommendations;
}

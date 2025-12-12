/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.batch.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FailedRecord {
    private int index;
    private Object data;
    private int errorCode;
    private String errorMessage;
}

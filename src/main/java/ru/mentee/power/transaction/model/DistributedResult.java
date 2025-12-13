/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.transaction.model;

import java.util.Map;
import javax.transaction.xa.Xid;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DistributedResult {
    private Xid transactionId;
    private boolean successful;
    private Map<String, Object> results;
    private String errorMessage;
}

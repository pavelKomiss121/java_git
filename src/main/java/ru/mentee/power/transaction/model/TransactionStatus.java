/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.transaction.model;

import javax.transaction.xa.Xid;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TransactionStatus {
    private Xid transactionId;
    private String status;
    private long timestamp;
}

/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.transaction.model;

import java.util.List;
import javax.transaction.xa.Xid;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RecoveryResult {
    private int recoveredTransactions;
    private List<Xid> xids;
}

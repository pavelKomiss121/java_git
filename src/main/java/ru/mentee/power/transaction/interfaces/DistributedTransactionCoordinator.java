/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.transaction.interfaces;

import java.sql.SQLException;
import javax.transaction.xa.Xid;
import ru.mentee.power.transaction.exception.DistributedTransactionException;
import ru.mentee.power.transaction.model.DistributedOperation;
import ru.mentee.power.transaction.model.DistributedResult;
import ru.mentee.power.transaction.model.RecoveryResult;
import ru.mentee.power.transaction.model.TransactionStatus;

public interface DistributedTransactionCoordinator {
    DistributedResult executeDistributed(DistributedOperation operation)
            throws DistributedTransactionException;

    RecoveryResult recoverPendingTransactions() throws SQLException;

    TransactionStatus getTransactionStatus(Xid transactionId);
}

/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.transaction.impl;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.sql.XAConnection;
import javax.sql.XADataSource;
import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;
import lombok.extern.slf4j.Slf4j;
import ru.mentee.power.transaction.exception.DistributedTransactionException;
import ru.mentee.power.transaction.interfaces.DistributedTransactionCoordinator;
import ru.mentee.power.transaction.model.DatabaseOperation;
import ru.mentee.power.transaction.model.DistributedOperation;
import ru.mentee.power.transaction.model.DistributedResult;
import ru.mentee.power.transaction.model.RecoveryResult;
import ru.mentee.power.transaction.model.TransactionStatus;

@Slf4j
public class DistributedTransactionManager implements DistributedTransactionCoordinator {
    private final Map<String, XADataSource> dataSources;

    public DistributedTransactionManager(Map<String, XADataSource> dataSources) {
        this.dataSources = dataSources;
    }

    @Override
    public DistributedResult executeDistributed(DistributedOperation operation)
            throws DistributedTransactionException {
        Xid xid = generateXid();
        Map<String, XAConnection> connections = new HashMap<>();
        Map<String, XAResource> resources = new HashMap<>();

        try {
            for (String dbName : operation.getDatabases()) {
                XAConnection xaConn = dataSources.get(dbName).getXAConnection();
                connections.put(dbName, xaConn);
                XAResource xaResource = xaConn.getXAResource();
                resources.put(dbName, xaResource);
                xaResource.start(xid, XAResource.TMNOFLAGS);
            }

            Map<String, Object> results = new HashMap<>();
            for (DatabaseOperation dbOp : operation.getOperations()) {
                Connection conn = connections.get(dbOp.getDatabase()).getConnection();
                Object result = dbOp.execute(conn);
                results.put(dbOp.getDatabase(), result);
            }

            for (XAResource resource : resources.values()) {
                resource.end(xid, XAResource.TMSUCCESS);
            }

            boolean allPrepared = true;
            List<String> readOnlyResources = new ArrayList<>();
            for (Map.Entry<String, XAResource> entry : resources.entrySet()) {
                try {
                    int vote = entry.getValue().prepare(xid);
                    if (vote == XAResource.XA_RDONLY) {
                        readOnlyResources.add(entry.getKey());
                    }
                } catch (Exception e) {
                    log.error("Ошибка prepare для {}", entry.getKey(), e);
                    allPrepared = false;
                    break;
                }
            }

            for (String roResource : readOnlyResources) {
                resources.remove(roResource);
            }

            if (allPrepared) {
                for (XAResource resource : resources.values()) {
                    resource.commit(xid, false);
                }
                return DistributedResult.builder()
                        .transactionId(xid)
                        .successful(true)
                        .results(results)
                        .build();
            } else {
                rollbackDistributed(resources, xid);
                return DistributedResult.builder()
                        .transactionId(xid)
                        .successful(false)
                        .errorMessage("Не все участники готовы к commit")
                        .build();
            }
        } catch (Exception e) {
            rollbackDistributed(resources, xid);
            throw new DistributedTransactionException("Ошибка распределенной транзакции", e);
        } finally {
            for (XAConnection conn : connections.values()) {
                try {
                    conn.close();
                } catch (Exception e) {
                    log.error("Ошибка закрытия XA соединения", e);
                }
            }
        }
    }

    @Override
    public RecoveryResult recoverPendingTransactions() throws SQLException {
        List<Xid> recoveredXids = new ArrayList<>();
        for (Map.Entry<String, XADataSource> entry : dataSources.entrySet()) {
            String dbName = entry.getKey();
            XAConnection xaConn = null;
            try {
                xaConn = entry.getValue().getXAConnection();
                XAResource xaResource = xaConn.getXAResource();
                Xid[] pendingXids;
                try {
                    pendingXids = xaResource.recover(XAResource.TMSTARTRSCAN);
                } catch (XAException e) {
                    log.error("Ошибка при получении списка транзакций для восстановления", e);
                    continue;
                }
                for (Xid xid : pendingXids) {
                    log.warn("Обнаружена подвисшая транзакция {} в {}", xid, dbName);
                    try {
                        if (shouldCommit(xid)) {
                            xaResource.commit(xid, false);
                            log.info("Транзакция {} зафиксирована", xid);
                        } else {
                            xaResource.rollback(xid);
                            log.info("Транзакция {} отменена", xid);
                        }
                        recoveredXids.add(xid);
                    } catch (XAException e) {
                        log.error("Ошибка при восстановлении транзакции {}", xid, e);
                    }
                }
            } finally {
                if (xaConn != null) {
                    try {
                        xaConn.close();
                    } catch (Exception e) {
                        log.error("Ошибка закрытия XA соединения", e);
                    }
                }
            }
        }
        return RecoveryResult.builder()
                .recoveredTransactions(recoveredXids.size())
                .xids(recoveredXids)
                .build();
    }

    @Override
    public TransactionStatus getTransactionStatus(Xid transactionId) {
        return TransactionStatus.builder()
                .transactionId(transactionId)
                .status("UNKNOWN")
                .timestamp(System.currentTimeMillis())
                .build();
    }

    private Xid generateXid() {
        byte[] globalId = new byte[16];
        byte[] branchId = new byte[16];
        java.util.Random random = new java.util.Random();
        random.nextBytes(globalId);
        random.nextBytes(branchId);
        return new SimpleXid(globalId, branchId);
    }

    private void rollbackDistributed(Map<String, XAResource> resources, Xid xid) {
        for (XAResource resource : resources.values()) {
            try {
                resource.rollback(xid);
            } catch (Exception e) {
                log.error("Ошибка отката ресурса", e);
            }
        }
    }

    private boolean shouldCommit(Xid xid) {
        return false;
    }

    private static class SimpleXid implements Xid {
        private final byte[] globalId;
        private final byte[] branchId;

        public SimpleXid(byte[] globalId, byte[] branchId) {
            this.globalId = globalId;
            this.branchId = branchId;
        }

        @Override
        public int getFormatId() {
            return 1;
        }

        @Override
        public byte[] getGlobalTransactionId() {
            return globalId;
        }

        @Override
        public byte[] getBranchQualifier() {
            return branchId;
        }
    }
}

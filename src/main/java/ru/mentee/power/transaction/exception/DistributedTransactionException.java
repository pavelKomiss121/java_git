/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.transaction.exception;

public class DistributedTransactionException extends Exception {
    public DistributedTransactionException(String message) {
        super(message);
    }

    public DistributedTransactionException(String message, Throwable cause) {
        super(message, cause);
    }
}

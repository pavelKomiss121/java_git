/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.exception;

public class InsufficientFundsException extends Throwable {
    InsufficientFundsException() {
        super();
    }

    public InsufficientFundsException(String message) {
        super(message);
    }

    public InsufficientFundsException(String message, Throwable cause) {
        super(message, cause);
    }

    public InsufficientFundsException(Throwable cause) {
        super(cause);
    }
}

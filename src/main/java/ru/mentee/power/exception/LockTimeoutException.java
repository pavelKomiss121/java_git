/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.exception;

public class LockTimeoutException extends Throwable {

    LockTimeoutException() {
        super();
    }

    public LockTimeoutException(String message) {
        super(message);
    }

    public LockTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }

    public LockTimeoutException(Throwable cause) {
        super(cause);
    }
}

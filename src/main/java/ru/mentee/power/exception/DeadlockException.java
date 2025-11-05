/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.exception;

public class DeadlockException extends Throwable {

    DeadlockException() {
        super();
    }

    public DeadlockException(String message) {
        super(message);
    }

    public DeadlockException(String message, Throwable cause) {
        super(message, cause);
    }

    public DeadlockException(Throwable cause) {
        super(cause);
    }
}

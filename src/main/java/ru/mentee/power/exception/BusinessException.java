/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.exception;

public class BusinessException extends Throwable {

    BusinessException() {
        super();
    }

    public BusinessException(String message) {
        super(message);
    }

    public BusinessException(String message, Throwable cause) {
        super(message, cause);
    }

    public BusinessException(Throwable cause) {
        super(cause);
    }
}

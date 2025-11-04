/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.exception;

public class ProductNotAvailableException extends Throwable {
    ProductNotAvailableException() {
        super();
    }

    public ProductNotAvailableException(String message) {
        super(message);
    }

    public ProductNotAvailableException(String message, Throwable cause) {
        super(message, cause);
    }

    public ProductNotAvailableException(Throwable cause) {
        super(cause);
    }
}

package com.vonhof.webi;

/**
 * Indicate to webi that an HTTP error occurred. Sets message and http status code
 * @author Henrik Hofmeister <@vonhofdk>
 */
public class HttpException extends RuntimeException {
    public static int BAD_REQUEST = 400;
    public static int NOT_FOUND = 404;
    public static int INTERNAL_ERROR = 500;
    public static int UNAUTHORIZED = 401;
    public static int FORBIDDEN = 403;
    
    private final int code;

    public HttpException(int code, Throwable thrwbl) {
        super(thrwbl);
        this.code = code;
    }

    public HttpException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}

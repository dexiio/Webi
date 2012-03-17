package com.vonhof.webi;

public class HttpException extends Exception {
    public static int CLIENT = 400;
    public static int NOT_FOUND = 404;
    public static int INTERNAL_ERROR = 500;
    
    private final int code;

    public HttpException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}

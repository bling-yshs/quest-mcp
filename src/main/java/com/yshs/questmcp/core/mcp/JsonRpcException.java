package com.yshs.questmcp.core.mcp;

/** A request that is answered with a JSON-RPC error object. */
final class JsonRpcException extends Exception {
    static final int PARSE_ERROR = -32700;
    static final int INVALID_REQUEST = -32600;
    static final int METHOD_NOT_FOUND = -32601;
    static final int INVALID_PARAMS = -32602;
    static final int INTERNAL_ERROR = -32603;

    private final int code;

    JsonRpcException(int code, String message) {
        super(message);
        this.code = code;
    }

    int code() {
        return code;
    }
}

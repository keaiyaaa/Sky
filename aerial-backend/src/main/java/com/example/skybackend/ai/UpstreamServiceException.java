package com.example.skybackend.ai;

public class UpstreamServiceException extends RuntimeException {

    public UpstreamServiceException() {
        super("上游模型请求失败");
    }
}

package com.ai.mall.identity.application.exception;
public class UseCaseException extends RuntimeException {private final Kind kind;public UseCaseException(Kind kind,String message){super(message);this.kind=kind;}public Kind kind(){return kind;}public enum Kind{INVALID,NOT_FOUND,CONFLICT,UNAUTHORIZED}}

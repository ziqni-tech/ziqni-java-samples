package com.ziqni.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GlobalExceptionHandler implements Thread.UncaughtExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    public void uncaughtException(Thread t, Throwable e) {
        logger.error("Unhandled exception caught!", e);
    }
}

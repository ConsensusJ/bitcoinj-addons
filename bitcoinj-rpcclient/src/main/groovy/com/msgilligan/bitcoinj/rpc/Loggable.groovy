package com.msgilligan.bitcoinj.rpc

import groovy.transform.CompileStatic
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Adds slf4j logger to a class
 *
 * Note: we can't use the @Slf4j annotation on traits
 */
trait Loggable {
    private final Logger logger = LoggerFactory.getLogger( this.getClass() )

    Logger getLog() {
        return logger
    }
}
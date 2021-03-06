package com.msgilligan.bitcoinj.test

import com.msgilligan.bitcoinj.rpc.BitcoinClientDelegate
import org.consensusj.jsonrpc.groovy.Loggable

/**
 * Test support functions intended to be mixed-in to Spock test specs
 */
trait BTCTestSupport implements BitcoinClientDelegate, FundingSourceDelegate {

    void serverReady() {
        Boolean ready = client.waitForServer(60)   // Wait up to 1 minute
        if (!ready) {
            throw new RuntimeException("Timeout waiting for server")
        }
    }

    void consolidateCoins() {
        fundingSource.fundingSourceMaintenance();
    }
}
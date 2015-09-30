package com.msgilligan.bitcoinj.rpc

import com.msgilligan.bitcoinj.BaseRegTestSpec
import org.bitcoinj.core.Coin
import org.bitcoinj.core.Sha256Hash
import org.bitcoinj.params.RegTestParams

class BitcoinSpec extends BaseRegTestSpec {
    static final Coin testAmount = 2.btc

    def "return basic info" () {
        when: "we request info"
        def info = getInfo()

        then: "we get back some basic information"
        info != null
        info.version >= 90100
        info.protocolversion >= 70002
    }

    def "Get a list of available commands"() {
        given:
        def commands = getCommands()

        expect:
        commands != null
        commands.contains('getinfo')
        commands.contains('help')
        commands.contains('stop')
    }

    def "Use RegTest mode to generate a block upon request"() {
        given: "a certain starting height"
        def startHeight = blockCount
        def version10 = getInfo().version > 100000

        when: "we generate 1 new block"
        def result = generateBlock()

        then: "the block height is 1 higher"
        blockCount == startHeight + 1

        and: "We have a txid if version > 10"
        !version10 || version10 && result.size() == 1 && result[0] instanceof Sha256Hash
    }

    def "When we send an amount to a newly created address, it arrives"() {
        given: "A new, empty Bitcoin address"
        def destinationAddress = getNewAddress()

        when: "we send it testAmount (from coins mined in RegTest mode)"
        sendToAddress(destinationAddress, testAmount, "comment", "comment-to")

        and: "we generate 1 new block"
        generateBlock()

        then: "the new address has a balance of testAmount"
        testAmount == getReceivedByAddress(destinationAddress)
        // TODO: check balance of source address/wallet
    }

    def "Get a list of unspent transaction outputs"() {
        when: "we request unspent transaction outputs"
        def unspent = listUnspent()

        then: "there is at least 1"
        unspent.size() >= 1
    }

    def "Get a filtered list of unconfirmed transaction outputs"() {
        when: "we create a new address and send #testAmount to it"
        def destinationAddress = getNewAddress()
        sendToAddress(destinationAddress, testAmount, "comment", "comment-to")

        and: "we request unconfirmed unspent outputs for #destinationAddress"
        def unspent = listUnspent(0, 0, [destinationAddress])

        then: "there is at least 1"
        unspent.size() >= 1

        and: "they have 0 confirmations"
        unspent.every { output -> output.confirmations == 0 }

        and: "they are associated with #destinationAddress"
        unspent.every { output -> output.address == destinationAddress }
    }

    def "We can get the correct private key for an address"() {
        when: "we create a new address and dump it's private key"
        def address = getNewAddress()
        def netParams = RegTestParams.get()
        def key = dumpPrivKey(address)

        then: "when we convert the dumped key to an address we get the same address"
        key.toAddress(netParams) == address
    }

    def "We can get information about chain tips"() {
        when:
        def tips = getChainTips()

        then:
        tips != null
        tips.size() >= 1
        tips[0].branchlen == 0
        tips[0].hash instanceof Sha256Hash
        tips[0].height > 0
        tips[0].status == "active"
    }
}

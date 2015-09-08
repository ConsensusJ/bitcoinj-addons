package com.msgilligan.bitcoinj.test

import com.msgilligan.bitcoinj.rpc.BitcoinClientDelegate
import com.msgilligan.bitcoinj.rpc.Outpoint
import com.msgilligan.bitcoinj.rpc.UnspentOutput
import org.bitcoinj.core.Address
import org.bitcoinj.core.Coin
import org.bitcoinj.core.ECKey
import org.bitcoinj.core.NetworkParameters
import org.bitcoinj.core.Sha256Hash
import org.bitcoinj.core.Transaction
import org.bitcoinj.core.TransactionOutPoint
import org.bitcoinj.core.TransactionOutput
import org.bitcoinj.params.RegTestParams

/**
 * Test support functions intended to be mixed-in to Spock test specs
 */
trait BTCTestSupport implements BitcoinClientDelegate {
    // TODO: set, or get and verify default values of the client
    private final NetworkParameters netParams = RegTestParams.get()
    private static final BigDecimal satoshisPerBTCDecimal = new BigDecimal(Coin.COIN.value);
    final BigDecimal stdTxFee = 0.00010000
    final BigDecimal stdRelayTxFee = 0.00001000
    final Integer defaultMaxConf = 9999999
    final long stdTxFeeSatoshis = btcToSatoshis(stdTxFee)

    @Deprecated
    Sha256Hash requestBitcoin(Address toAddress, BigDecimal requestedBTC) {
        return requestBitcoin(toAddress,btcToCoin(requestedBTC))
    }

    /**
     * Generate blocks and fund an address with requested amount of BTC
     *
     * TODO: Improve performance. Can we mine multiple blocks with a single RPC?
     *
     * @param toAddress Address to fund with BTC
     * @param requestedBTC Amount of BTC to "mine" and send
     * @return
     */
    Sha256Hash requestBitcoin(Address toAddress, Coin requestedAmount) {
        long requestedSatoshi = requestedAmount.longValue()
        long amountGatheredSoFar = 0
        def inputs = new ArrayList<Outpoint>()

        // Newly mined coins need to mature to be spendable
        def minCoinAge = 100

        if (blockCount < minCoinAge) {
            generateBlocks(minCoinAge - blockCount)
        }

        while (amountGatheredSoFar < requestedSatoshi) {
            generateBlock()
            def blockIndex = blockCount - minCoinAge
            def block = client.getBlock(blockIndex)
            def blockTxs = block.tx as List<String>
            def coinbaseTx = new Sha256Hash(blockTxs.get(0))
            def txout = client.getTxOut(coinbaseTx, 0)

            // txout is empty, if output was already spent
            if (txout && txout.containsKey("value")) {
                def amountBTCbd = BigDecimal.valueOf(txout.value as Double)
                long amountSatoshi = btcToSatoshis(amountBTCbd)
                amountGatheredSoFar += amountSatoshi
                inputs << new Outpoint(coinbaseTx, 0)
            }
        }

        // Don't care about change, we mine it anyway
        def outputs = new HashMap<Address, BigDecimal>()
        outputs.put(toAddress, BigDecimal.valueOf(requestedSatoshi * Coin.COIN.longValue()))

        def unsignedTxHex = client.createRawTransaction(inputs, outputs)
        def signingResult = client.signRawTransaction(unsignedTxHex)

        assert signingResult.complete == true

        def signedTxHex = signingResult.hex as String
        def txid = client.sendRawTransaction(signedTxHex, true)

        return txid
    }

    /**
     * Creates a raw transaction, spending from a single address, whereby no new change address is created, and
     * remaining amounts are returned to {@code fromAddress}.
     *
     * Note: the transaction inputs are not signed, and the transaction is not stored in the wallet or transmitted to
     * the network.
     *
     * @param fromAddress The source to spend from
     * @param outputs The destinations and amounts to transfer
     * @return The hex-encoded raw transaction
     */
    String createRawTransaction(Address fromAddress, Map<Address, BigDecimal> outputs) {
        // Get unspent outputs via RPC
        def unspentOutputs = listUnspent(0, defaultMaxConf, [fromAddress])

        // Gather inputs
        def inputs = unspentOutputs.collect { new Outpoint(it.txid, it.vout) }

        // Calculate change
        BigDecimal amountIn     = (BigDecimal) unspentOutputs.sum { it.amount }
        BigDecimal amountOut    = (BigDecimal) outputs.values().sum()
        BigDecimal amountChange = amountIn - amountOut - stdTxFee
        if (amountIn < (amountOut + stdTxFee)) {
            println "Insufficient funds: ${amountIn} < ${amountOut + stdTxFee}"
        }
        if (amountChange > 0) {
            outputs[fromAddress] = amountChange
        }

        return createRawTransaction(inputs, outputs)
    }

    /**
     * Creates a raw transaction, sending {@code amount} from a single address to a destination, whereby no new change
     * address is created, and remaining amounts are returned to {@code fromAddress}.
     *
     * Note: the transaction inputs are not signed, and the transaction is not stored in the wallet or transmitted to
     * the network.
     *
     * @param fromAddress The source to spent from
     * @param toAddress The destination
     * @param amount The amount
     * @return The hex-encoded raw transaction
     */
    String createRawTransaction(Address fromAddress, Address toAddress, BigDecimal amount) {
        def outputs = new HashMap<Address, BigDecimal>()
        outputs[toAddress] = amount
        return createRawTransaction(fromAddress, outputs)
    }



    /**
     * Returns the Bitcoin balance of an address.
     *
     * @param address The address
     * @return The balance
     */
    BigDecimal getBitcoinBalance(Address address) {
        // NOTE: because null is currently removed from the argument lists passed via RPC, using it here for default
        // values would result in the RPC call "listunspent" with arguments [["address"]], which is invalid, similar
        // to a call with arguments [null, null, ["address"]], as expected arguments are either [], [int], [int, int]
        // or [int, int, array]
        return getBitcoinBalance(address, 1, defaultMaxConf)
    }

    /**
     * Returns the Bitcoin balance of an address where spendable outputs have at least {@code minConf} confirmations.
     *
     * @param address The address
     * @param minConf Minimum amount of confirmations
     * @return The balance
     */
    BigDecimal getBitcoinBalance(Address address, Integer minConf) {
        return getBitcoinBalance(address, minConf, defaultMaxConf)
    }

    /**
     * Returns the Bitcoin balance of an address where spendable outputs have at least {@code minConf} and not more
     * than {@code maxConf} confirmations.
     *
     * @param address The address
     * @param minConf Minimum amount of confirmations
     * @param maxConf Maximum amount of confirmations
     * @return The balance
     */
    BigDecimal getBitcoinBalance(Address address, Integer minConf, Integer maxConf) {
        def btcBalance = new BigDecimal(0)
        def unspentOutputs = listUnspent(minConf, maxConf, [address])

        for (unspentOutput in unspentOutputs) {
            btcBalance += unspentOutput.amount
        }

        return btcBalance
    }

    /**
     * Sends BTC from an address to a destination, whereby no new change address is created, and any leftover is
     * returned to the sending address.
     *
     * @param fromAddress The source to spent from
     * @param toAddress   The destination address
     * @param amount      The amount to transfer
     * @return The transaction hash
     */
    Sha256Hash sendBitcoin(Address fromAddress, Address toAddress, BigDecimal amount) {
        def outputs = new HashMap<Address, BigDecimal>()
        outputs[toAddress] = amount
        return sendBitcoin(fromAddress, outputs)
    }

    /**
     * Sends BTC from an address to the destinations, whereby no new change address is created, and any leftover is
     * returned to the sending address.
     *
     * @param fromAddress The source to spent from
     * @param outputs     The destinations and amounts to transfer
     * @return The transaction hash
     */
    Sha256Hash sendBitcoin(Address fromAddress, Map<Address, BigDecimal> outputs) {
        def unsignedTxHex = createRawTransaction(fromAddress, outputs)
        def signingResult = signRawTransaction(unsignedTxHex)

        assert signingResult["complete"] == true

        def signedTxHex = signingResult["hex"] as String
        def txid = sendRawTransaction(signedTxHex)

        return txid
    }

    /**
     * Collects <b>all</b> unspent outputs and spends the whole amount minus {@code stdRelayTxFee}, which is sent
     * to a new address, as fee, to sweep dust and to minimize the number of unspent outputs, to avoid creating too
     * large transactions. No new block is generated afterwards.
     *
     * Can be used in cleanupSpec() methods of integration tests.
     *
     * @see <a href="https://github.com/OmniLayer/OmniJ/issues/50">Issue #50 on GitHub</a>
     *
     * @return True, if enough outputs with a value of at least {@code stdRelayTxFee} were spent
     */
    Boolean consolidateCoins() {
        def amountIn = new BigDecimal(0)
        def inputs = new ArrayList<Outpoint>()
        def unspentOutputs = listUnspent(1, defaultMaxConf)

        // Gather inputs
        for (unspentOutput in unspentOutputs) {
            amountIn += unspentOutput.amount
            inputs << new Outpoint(unspentOutput.txid, unspentOutput.vout)
        }

        // Check if there is a sufficient high amount to sweep at all
        if (amountIn < stdRelayTxFee) {
            return false
        }

        // No receiver, just spend most of it as fee (!)
        def outputs = new HashMap<Address, BigDecimal>()
        outputs[newAddress] = stdRelayTxFee

        def unsignedTxHex = client.createRawTransaction(inputs, outputs)
        def signingResult = client.signRawTransaction(unsignedTxHex)

        assert signingResult.complete == true

        def signedTxHex = signingResult.hex as String
        def txid = client.sendRawTransaction(signedTxHex, true)

        return true
    }

    Transaction createSignedTransaction(ECKey fromKey, List<TransactionOutput> outputs) {
        Address fromAddress = fromKey.toAddress(netParams)
        Transaction tx = new Transaction(netParams)

        List<TransactionOutput> unspentOutputs = listUnspentJ(fromAddress)

        // Add outputs to the transaction
        outputs.each {
            tx.addOutput(it)
        }

        // Calculate change (units are satoshis)
        long amountIn     = (long) unspentOutputs.sum { TransactionOutput it -> it.value.longValue() }
        long amountOut    = (long) outputs.sum { TransactionOutput it -> it.value.longValue() }
        long amountChange = amountIn - amountOut - stdTxFeeSatoshis
        if (amountChange < 0) {
            // TODO: Throw Exception
            println "Insufficient funds: ${amountIn} < ${amountOut + stdTxFeeSatoshis}"
        }
        if (amountChange > 0) {
            // Add a change output
            tx.addOutput(Coin.valueOf(amountChange), fromAddress)
        }

        // Add all UTXOs for fromAddress as inputs
        unspentOutputs.each {
            tx.addSignedInput(it, fromKey)
        }

        return tx;
    }

    Transaction createSignedTransaction(ECKey fromKey, Address toAddress, Coin amount) {
        def outputs = [new TransactionOutput(netParams, null, amount, toAddress)]
        return createSignedTransaction(fromKey, outputs)
    }

    /**
     * Build a list of bitcoinj <code>TransactionOutput</code>s using <code>listUnspent</code>
     * and <code>getRawTransaction</code> RPCs
     *
     * @param fromAddress Address to get UTXOs for
     * @return All unspent TransactionOutputs for fromAddress
     */
    List<TransactionOutput> listUnspentJ(Address fromAddress) {
        List<UnspentOutput> unspentOutputsRPC = listUnspent(0, defaultMaxConf, [fromAddress]) // RPC UnspentOutput objects
        List<TransactionOutput> unspentOutputsJ = unspentOutputsRPC.collect {
            getRawTransaction(it.txid).getOutput(it.vout) }  // bitcoinj TransactionOutput objects
        return unspentOutputsJ
    }

    List<TransactionOutPoint> listUnspentOutPoints(Address fromAddress) {
        List<UnspentOutput> unspentOutputsRPC = listUnspent(0, defaultMaxConf, [fromAddress]) // RPC UnspentOutput objects
        List<TransactionOutPoint> unspentOutPoints = unspentOutputsRPC.collect {
            new TransactionOutPoint(netParams, it.vout, it.txid) } // bitcoinj TransactionOutPoint objects
        return unspentOutPoints
    }

    /**
     * Convert from BigDecimal BTC value to <code>Long</code>.
     *
     * @param btc Bitcoin amount in BTC units
     * @return Long with units of satoshis
     */
    Long btcToSatoshis(final BigDecimal btc) {
        BigDecimal satoshisDecimal = btc.multiply(satoshisPerBTCDecimal);
        return satoshisDecimal.longValueExact();
    }

    /**
     * Convert from BigDecimal BTC value to <code>Coin</code> type.
     *
     * @param btc Bitcoin amount in BTC units
     * @return bitcoinj <code>Coin</code> type (uses Satoshis internally)
     */
    Coin btcToCoin(final BigDecimal btc) {
        return Coin.valueOf(btcToSatoshis(btc));
    }
}
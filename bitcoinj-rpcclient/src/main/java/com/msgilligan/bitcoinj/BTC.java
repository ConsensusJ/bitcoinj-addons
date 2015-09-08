package com.msgilligan.bitcoinj;

import org.bitcoinj.core.Coin;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * Utility class for converting BTC (BigDecimal) to satoshis (Coin class)
 */
@Deprecated
public class BTC  {
    private static final BigDecimal satoshisPerBTCDecimal = new BigDecimal(Coin.COIN.value);

    private static long btcToSatoshisLong(final BigDecimal btc) {
        BigDecimal satoshisDecimal = btc.multiply(satoshisPerBTCDecimal);
        return satoshisDecimal.longValueExact();
    }

    /**
     * Convert from BigDecimal BTC value to Satoshis <code>BigInteger</code>.
     * @param btc An amount of bitcoin in the BTC unit
     * @return The equivalent amount in satoshis
     */
    @Deprecated
    public static BigInteger btcToSatoshis(final BigDecimal btc) {
        return BigInteger.valueOf(btcToSatoshisLong(btc));
    }

    /**
     * Convert from BigDecimal BTC value to <code>Coin</code> type.
     *
     * @param btc Bitcoin amount in BTC units
     * @return bitcoinj <code>Coin</code> type (uses Satoshis internally)
     */
    @Deprecated
    public static Coin btcToCoin(final BigDecimal btc) {
        return Coin.valueOf(btcToSatoshisLong(btc));
    }
}

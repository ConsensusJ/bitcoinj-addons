#!/bin/bash
set -x

function cleanup {
    kill -15 $BTCPID
}
trap cleanup EXIT

BTCD=copied-artifacts/src/bitcoind
DATADIR=$HOME/.bitcoin
LOGDIR=logs
MSCLOG=/tmp/mastercore.log

# Assume bitcoind built elsewhere and copied by Jenkins Copy Artifact plugin
chmod +x $BTCD

# Setup bitcoin conf and data dir
mkdir -p $DATADIR
cp -n bitcoin.conf $DATADIR

# Setup logging
mkdir -p $LOGDIR
touch $MSCLOG
ln -sf $MSCLOG $LOGDIR/mastercore.log

# Remove all regtest data
rm -rf $DATADIR/regtest

# Run bitcoind in regtest mode
$BTCD -regtest -datadir=$DATADIR > $LOGDIR/bitcoin.log &
BTCSTATUS=$?
BTCPID=$!

# Give server some time to start
#sleep 30

# Run integration tests
echo "Running Bitcoin RPC integration tests in regtest mode..."
./gradlew clean :bitcoin-rpc:regTest
GRADLESTATUS=$?

exit $GRADLESTATUS

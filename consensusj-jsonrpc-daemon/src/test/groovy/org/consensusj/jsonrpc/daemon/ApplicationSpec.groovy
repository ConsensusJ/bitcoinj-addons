package org.consensusj.jsonrpc.daemon

import io.micronaut.runtime.EmbeddedApplication
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import org.consensusj.jsonrpc.JsonRpcMessage
import org.consensusj.jsonrpc.JsonRpcResponse
import org.consensusj.jsonrpc.groovy.DynamicRpcClient
import spock.lang.Specification

import jakarta.inject.Inject

/**
 *
 */
@MicronautTest
class ApplicationSpec extends Specification {
    @Inject
    EmbeddedServer server

    void 'test it works'() {
        expect:
        server.running
        server.URI.getScheme() == "http"
        server.URI.getHost() == "localhost"
    }

    void 'hit it with a JSON-RPC request'() {
        given:
        def testString = 'Hello jsonrpc-echod!'
        def endpoint = URI.create(server.URI.toString()+"/jsonrpc")
        def client = new DynamicRpcClient(JsonRpcMessage.Version.V2, endpoint, "", "")

        when:
        String result = client.echo(testString)

        then:
        result == testString
    }

}

package net.consensys.pantheon.ethereum.jsonrpc.internal.methods;

import static org.assertj.core.api.Assertions.assertThat;

import net.consensys.pantheon.ethereum.eth.EthProtocol;
import net.consensys.pantheon.ethereum.jsonrpc.internal.JsonRpcRequest;
import net.consensys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcResponse;
import net.consensys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcSuccessResponse;
import net.consensys.pantheon.ethereum.p2p.wire.Capability;

import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

public class EthProtocolVersionTest {
  private EthProtocolVersion method;
  private final String JSON_RPC_VERSION = "2.0";
  private final String ETH_METHOD = "eth_protocolVersion";
  private Set<Capability> supportedCapabilities;

  @Test
  public void returnsCorrectMethodName() {
    setupSupportedEthProtocols();
    assertThat(method.getName()).isEqualTo(ETH_METHOD);
  }

  @Test
  public void shouldReturn63WhenMaxProtocolIsETH63() {

    setupSupportedEthProtocols();

    final JsonRpcRequest request = requestWithParams();
    final JsonRpcResponse expectedResponse = new JsonRpcSuccessResponse(request.getId(), 63);
    final JsonRpcResponse actualResponse = method.response(request);
    assertThat(actualResponse).isEqualToComparingFieldByField(expectedResponse);
  }

  @Test
  public void shouldReturnNullNoEthProtocolsSupported() {

    supportedCapabilities = new HashSet<>();
    supportedCapabilities.add(Capability.create("istanbul", 64));
    method = new EthProtocolVersion(supportedCapabilities);

    final JsonRpcRequest request = requestWithParams();
    final JsonRpcResponse expectedResponse = new JsonRpcSuccessResponse(request.getId(), null);
    final JsonRpcResponse actualResponse = method.response(request);
    assertThat(actualResponse).isEqualToComparingFieldByField(expectedResponse);
  }

  @Test
  public void shouldReturn63WhenMixedProtocolsSupported() {

    setupSupportedEthProtocols();
    supportedCapabilities.add(Capability.create("istanbul", 64));
    method = new EthProtocolVersion(supportedCapabilities);

    final JsonRpcRequest request = requestWithParams();
    final JsonRpcResponse expectedResponse = new JsonRpcSuccessResponse(request.getId(), 63);
    final JsonRpcResponse actualResponse = method.response(request);
    assertThat(actualResponse).isEqualToComparingFieldByField(expectedResponse);
  }

  private JsonRpcRequest requestWithParams(final Object... params) {
    return new JsonRpcRequest(JSON_RPC_VERSION, ETH_METHOD, params);
  }

  private void setupSupportedEthProtocols() {
    supportedCapabilities = new HashSet<>();
    supportedCapabilities.add(EthProtocol.ETH62);
    supportedCapabilities.add(EthProtocol.ETH63);
    method = new EthProtocolVersion(supportedCapabilities);
  }
}

package net.consensys.pantheon.ethereum.jsonrpc.websocket.methods;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import net.consensys.pantheon.ethereum.jsonrpc.internal.methods.JsonRpcMethod;
import net.consensys.pantheon.ethereum.jsonrpc.websocket.subscription.SubscriptionManager;

import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class WebSocketMethodsFactoryTest {

  private WebSocketMethodsFactory factory;

  @Mock private SubscriptionManager subscriptionManager;
  private final Map<String, JsonRpcMethod> jsonRpcMethods = new HashMap<>();

  @Before
  public void before() {
    jsonRpcMethods.put("eth_unsubscribe", jsonRpcMethod("eth_unsubscribe"));
    factory = new WebSocketMethodsFactory(subscriptionManager, jsonRpcMethods);
  }

  @Test
  public void websocketsFactoryShouldCreateEthSubscribe() {
    final JsonRpcMethod method = factory.methods().get("eth_subscribe");

    assertThat(method).isNotNull();
    assertThat(method).isInstanceOf(EthSubscribe.class);
  }

  @Test
  public void websocketsFactoryShouldCreateEthUnsubscribe() {
    final JsonRpcMethod method = factory.methods().get("eth_unsubscribe");

    assertThat(method).isNotNull();
    assertThat(method).isInstanceOf(EthUnsubscribe.class);
  }

  @Test
  public void factoryCreatesExpectedNumberOfMethods() {
    final Map<String, JsonRpcMethod> methodsMap = factory.methods();
    assertThat(methodsMap).hasSize(2);
  }

  @Test
  public void factoryIncludesJsonRpcMethodsWhenCreatingWebsocketMethods() {
    final JsonRpcMethod jsonRpcMethod1 = jsonRpcMethod("method1");
    final JsonRpcMethod jsonRpcMethod2 = jsonRpcMethod("method2");

    final Map<String, JsonRpcMethod> jsonRpcMethodsMap = new HashMap<>();
    jsonRpcMethodsMap.put("method1", jsonRpcMethod1);
    jsonRpcMethodsMap.put("method2", jsonRpcMethod2);
    factory = new WebSocketMethodsFactory(subscriptionManager, jsonRpcMethodsMap);

    final Map<String, JsonRpcMethod> methods = factory.methods();

    assertThat(methods).containsKeys("method1", "method2");
  }

  private JsonRpcMethod jsonRpcMethod(final String name) {
    final JsonRpcMethod jsonRpcMethod = mock(JsonRpcMethod.class);
    return jsonRpcMethod;
  }
}

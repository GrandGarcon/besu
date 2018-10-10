package net.consensys.pantheon.ethereum.jsonrpc.websocket.subscription;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import net.consensys.pantheon.ethereum.jsonrpc.internal.parameters.FilterParameter;
import net.consensys.pantheon.ethereum.jsonrpc.websocket.subscription.blockheaders.NewBlockHeadersSubscription;
import net.consensys.pantheon.ethereum.jsonrpc.websocket.subscription.logs.LogsSubscription;
import net.consensys.pantheon.ethereum.jsonrpc.websocket.subscription.request.SubscribeRequest;
import net.consensys.pantheon.ethereum.jsonrpc.websocket.subscription.request.SubscriptionType;
import net.consensys.pantheon.ethereum.jsonrpc.websocket.subscription.syncing.SyncingSubscription;

import java.util.function.Function;

import org.junit.Before;
import org.junit.Test;

public class SubscriptionBuilderTest {

  private SubscriptionBuilder subscriptionBuilder;

  @Before
  public void before() {
    subscriptionBuilder = new SubscriptionBuilder();
  }

  @Test
  public void shouldBuildLogsSubscriptionWhenSubscribeRequestTypeIsLogs() {
    final FilterParameter filterParameter = filterParameter();
    final SubscribeRequest subscribeRequest =
        new SubscribeRequest(SubscriptionType.LOGS, filterParameter, null, "connectionId");
    final LogsSubscription expectedSubscription = new LogsSubscription(1L, filterParameter);

    final Subscription builtSubscription = subscriptionBuilder.build(1L, subscribeRequest);

    assertThat(builtSubscription).isEqualToComparingFieldByField(expectedSubscription);
  }

  @Test
  public void shouldBuildNewBlockHeadsSubscriptionWhenSubscribeRequestTypeIsNewBlockHeads() {
    final SubscribeRequest subscribeRequest =
        new SubscribeRequest(SubscriptionType.NEW_BLOCK_HEADERS, null, true, "connectionId");
    final NewBlockHeadersSubscription expectedSubscription =
        new NewBlockHeadersSubscription(1L, true);

    final Subscription builtSubscription = subscriptionBuilder.build(1L, subscribeRequest);

    assertThat(builtSubscription).isEqualToComparingFieldByField(expectedSubscription);
  }

  @Test
  public void shouldBuildSubscriptionWhenSubscribeRequestTypeIsNewPendingTransactions() {
    final SubscribeRequest subscribeRequest =
        new SubscribeRequest(SubscriptionType.NEW_PENDING_TRANSACTIONS, null, null, "connectionId");
    final Subscription expectedSubscription =
        new Subscription(1L, SubscriptionType.NEW_PENDING_TRANSACTIONS);

    final Subscription builtSubscription = subscriptionBuilder.build(1L, subscribeRequest);

    assertThat(builtSubscription).isEqualToComparingFieldByField(expectedSubscription);
  }

  @Test
  public void shouldBuildSubscriptionWhenSubscribeRequestTypeIsSyncing() {
    final SubscribeRequest subscribeRequest =
        new SubscribeRequest(SubscriptionType.SYNCING, null, null, "connectionId");
    final SyncingSubscription expectedSubscription =
        new SyncingSubscription(1L, SubscriptionType.SYNCING);

    final Subscription builtSubscription = subscriptionBuilder.build(1L, subscribeRequest);

    assertThat(builtSubscription).isEqualToComparingFieldByField(expectedSubscription);
  }

  @Test
  public void shouldReturnLogsSubscriptionWhenMappingLogsSubscription() {
    final Function<Subscription, LogsSubscription> function =
        subscriptionBuilder.mapToSubscriptionClass(LogsSubscription.class);
    final Subscription subscription = new LogsSubscription(1L, filterParameter());

    assertThat(function.apply(subscription)).isInstanceOf(LogsSubscription.class);
  }

  @Test
  public void shouldReturnNewBlockHeadsSubscriptionWhenMappingNewBlockHeadsSubscription() {
    final Function<Subscription, NewBlockHeadersSubscription> function =
        subscriptionBuilder.mapToSubscriptionClass(NewBlockHeadersSubscription.class);
    final Subscription subscription = new NewBlockHeadersSubscription(1L, true);

    assertThat(function.apply(subscription)).isInstanceOf(NewBlockHeadersSubscription.class);
  }

  @Test
  public void shouldReturnSubscriptionWhenMappingNewPendingTransactionsSubscription() {
    final Function<Subscription, Subscription> function =
        subscriptionBuilder.mapToSubscriptionClass(Subscription.class);
    final Subscription logsSubscription =
        new Subscription(1L, SubscriptionType.NEW_PENDING_TRANSACTIONS);

    assertThat(function.apply(logsSubscription)).isInstanceOf(Subscription.class);
  }

  @Test
  public void shouldReturnSubscriptionWhenMappingSyncingSubscription() {
    final Function<Subscription, SyncingSubscription> function =
        subscriptionBuilder.mapToSubscriptionClass(SyncingSubscription.class);
    final Subscription subscription = new SyncingSubscription(1L, SubscriptionType.SYNCING);

    assertThat(function.apply(subscription)).isInstanceOf(SyncingSubscription.class);
  }

  @Test
  @SuppressWarnings("ReturnValueIgnored")
  public void shouldThrownIllegalArgumentExceptionWhenMappingWrongSubscriptionType() {
    final Function<Subscription, LogsSubscription> function =
        subscriptionBuilder.mapToSubscriptionClass(LogsSubscription.class);
    final NewBlockHeadersSubscription subscription = new NewBlockHeadersSubscription(1L, true);

    final Throwable thrown = catchThrowable(() -> function.apply(subscription));
    assertThat(thrown)
        .hasNoCause()
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "NewBlockHeadersSubscription instance can't be mapped to type LogsSubscription");
  }

  private FilterParameter filterParameter() {
    return new FilterParameter(null, null, null, null, null);
  }
}

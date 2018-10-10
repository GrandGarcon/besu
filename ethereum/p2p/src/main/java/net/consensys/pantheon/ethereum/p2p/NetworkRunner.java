package net.consensys.pantheon.ethereum.p2p;

import net.consensys.pantheon.ethereum.p2p.api.MessageData;
import net.consensys.pantheon.ethereum.p2p.api.P2PNetwork;
import net.consensys.pantheon.ethereum.p2p.api.ProtocolManager;
import net.consensys.pantheon.ethereum.p2p.wire.Capability;
import net.consensys.pantheon.ethereum.p2p.wire.SubProtocol;
import net.consensys.pantheon.ethereum.p2p.wire.messages.DisconnectMessage.DisconnectReason;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class NetworkRunner implements AutoCloseable {
  private static final Logger LOGGER = LogManager.getLogger(NetworkRunner.class);

  private final CountDownLatch shutdown = new CountDownLatch(1);;
  private final AtomicBoolean started = new AtomicBoolean(false);
  private final AtomicBoolean stopped = new AtomicBoolean(false);

  private final ExecutorService networkExecutor =
      Executors.newFixedThreadPool(
          1, new ThreadFactoryBuilder().setNameFormat(this.getClass().getSimpleName()).build());

  private final P2PNetwork network;
  private final Map<String, SubProtocol> subProtocols;
  private final List<ProtocolManager> protocolManagers;

  private NetworkRunner(
      final P2PNetwork network,
      final Map<String, SubProtocol> subProtocols,
      final List<ProtocolManager> protocolManagers) {
    this.network = network;
    this.protocolManagers = protocolManagers;
    this.subProtocols = subProtocols;
  }

  public P2PNetwork getNetwork() {
    return network;
  }

  public static Builder builder() {
    return new Builder();
  }

  public void start() {
    if (started.compareAndSet(false, true)) {
      LOGGER.info("Starting Network.");
      setupHandlers();
      networkExecutor.submit(network);
    } else {
      LOGGER.error("Attempted to start already running network.");
    }
  }

  public void stop() {
    if (stopped.compareAndSet(false, true)) {
      LOGGER.info("Stopping Network.");
      network.stop();
      for (final ProtocolManager protocolManager : protocolManagers) {
        protocolManager.stop();
      }
      networkExecutor.shutdown();
      shutdown.countDown();
    } else {
      LOGGER.error("Attempted to stop already stopped network.");
    }
  }

  public void awaitStop() throws InterruptedException {
    shutdown.await();
    network.awaitStop();
    for (final ProtocolManager protocolManager : protocolManagers) {
      protocolManager.awaitStop();
    }
    if (!networkExecutor.awaitTermination(2L, TimeUnit.MINUTES)) {
      LOGGER.error("Network executor did not shutdown cleanly.");
      networkExecutor.shutdownNow();
      networkExecutor.awaitTermination(2L, TimeUnit.MINUTES);
    }
    LOGGER.info("Network stopped.");
  }

  private void setupHandlers() {
    // Setup message handlers
    for (final ProtocolManager protocolManager : protocolManagers) {
      for (final Capability cap : protocolManager.getSupportedCapabilities()) {
        final SubProtocol protocol = subProtocols.get(cap.getName());
        network.subscribe(
            cap,
            message -> {
              final MessageData data = message.getData();
              try {
                final int code = message.getData().getCode();
                if (!protocol.isValidMessageCode(cap.getVersion(), code)) {
                  // Handle invalid messsages by disconnecting
                  LOGGER.info(
                      "Invalid message code ({}-{}, {}) received from peer, disconnecting from:",
                      cap.getName(),
                      cap.getVersion(),
                      code,
                      message.getConnection());
                  message.getConnection().disconnect(DisconnectReason.BREACH_OF_PROTOCOL);
                  return;
                }
                protocolManager.processMessage(cap, message);
              } finally {
                data.release();
              }
            });
      }
    }

    // Setup (dis)connect handlers
    for (final ProtocolManager protocolManager : protocolManagers) {
      network.subscribeConnect(
          (connection) -> {
            if (Collections.disjoint(
                connection.getAgreedCapabilities(), protocolManager.getSupportedCapabilities())) {
              return;
            }
            protocolManager.handleNewConnection(connection);
          });

      network.subscribeDisconnect(
          (connection, disconnectReason, initiatedByPeer) -> {
            if (Collections.disjoint(
                connection.getAgreedCapabilities(), protocolManager.getSupportedCapabilities())) {
              return;
            }
            protocolManager.handleDisconnect(connection, disconnectReason, initiatedByPeer);
          });
    }
  }

  @Override
  public void close() throws Exception {
    stop();
  }

  public static class Builder {
    private Function<List<Capability>, P2PNetwork> networkProvider;
    List<ProtocolManager> protocolManagers = new ArrayList<>();
    List<SubProtocol> subProtocols = new ArrayList<>();

    public NetworkRunner build() {
      final Map<String, SubProtocol> subProtocolMap = new HashMap<>();
      for (final SubProtocol subProtocol : subProtocols) {
        subProtocolMap.put(subProtocol.getName(), subProtocol);
      }
      final List<Capability> caps =
          protocolManagers
              .stream()
              .flatMap(p -> p.getSupportedCapabilities().stream())
              .collect(Collectors.toList());
      for (final Capability cap : caps) {
        if (!subProtocolMap.containsKey(cap.getName())) {
          throw new IllegalStateException(
              "No sub-protocol found corresponding to supported capability: " + cap);
        }
      }
      final P2PNetwork network = networkProvider.apply(caps);
      return new NetworkRunner(network, subProtocolMap, protocolManagers);
    }

    public Builder protocolManagers(final List<ProtocolManager> protocolManagers) {
      this.protocolManagers = protocolManagers;
      return this;
    }

    public Builder network(final Function<List<Capability>, P2PNetwork> networkProvider) {
      this.networkProvider = networkProvider;
      return this;
    }

    public Builder subProtocols(final SubProtocol... subProtocols) {
      this.subProtocols.addAll(Arrays.asList(subProtocols));
      return this;
    }

    public Builder subProtocols(final List<SubProtocol> subProtocols) {
      this.subProtocols.addAll(subProtocols);
      return this;
    }
  }
}

package net.consensys.pantheon.ethereum.eth.manager;

import net.consensys.pantheon.ethereum.eth.manager.EthPeer.DisconnectCallback;
import net.consensys.pantheon.ethereum.p2p.api.PeerConnection;
import net.consensys.pantheon.util.Subscribers;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class EthPeers {
  public static final Comparator<EthPeer> TOTAL_DIFFICULTY =
      Comparator.comparing(
          ((final EthPeer p) -> p.chainState().getBestBlock().getTotalDifficulty()));

  public static final Comparator<EthPeer> CHAIN_HEIGHT =
      Comparator.comparing(((final EthPeer p) -> p.chainState().getEstimatedHeight()));

  public static final Comparator<EthPeer> BEST_CHAIN = CHAIN_HEIGHT.thenComparing(TOTAL_DIFFICULTY);

  public static final Comparator<EthPeer> LEAST_TO_MOST_BUSY =
      Comparator.comparing(EthPeer::outstandingRequests);

  private final int maxOutstandingRequests = 5;
  private final Map<PeerConnection, EthPeer> connections = new ConcurrentHashMap<>();
  private final String protocolName;
  private final Subscribers<ConnectCallback> connectCallbacks = new Subscribers<>();
  private final Subscribers<DisconnectCallback> disconnectCallbacks = new Subscribers<>();

  public EthPeers(final String protocolName) {
    this.protocolName = protocolName;
  }

  void registerConnection(final PeerConnection peerConnection) {
    final EthPeer peer = new EthPeer(peerConnection, protocolName, this::invokeConnectionCallbacks);
    connections.putIfAbsent(peerConnection, peer);
  }

  void registerDisconnect(final PeerConnection connection) {
    final EthPeer peer = connections.remove(connection);
    if (peer != null) {
      disconnectCallbacks.forEach(callback -> callback.onDisconnect(peer));
      peer.handleDisconnect();
    }
  }

  EthPeer peer(final PeerConnection peerConnection) {
    return connections.get(peerConnection);
  }

  public long subscribeConnect(final ConnectCallback callback) {
    return connectCallbacks.subscribe(callback);
  }

  public void unsubscribeConnect(final long id) {
    connectCallbacks.unsubscribe(id);
  }

  public long subscribeDisconnect(final DisconnectCallback callback) {
    return disconnectCallbacks.subscribe(callback);
  }

  public int peerCount() {
    return connections.size();
  }

  public int availablePeerCount() {
    return (int) availablePeers().count();
  }

  public Stream<EthPeer> availablePeers() {
    return connections.values().stream().filter(EthPeer::readyForRequests);
  }

  public Optional<EthPeer> bestPeer() {
    return availablePeers().max(BEST_CHAIN);
  }

  public Optional<EthPeer> idlePeer() {
    return idlePeers().min(LEAST_TO_MOST_BUSY);
  }

  private Stream<EthPeer> idlePeers() {
    final List<EthPeer> peers =
        availablePeers()
            .filter(p -> p.outstandingRequests() < maxOutstandingRequests)
            .collect(Collectors.toList());
    Collections.shuffle(peers);
    return peers.stream();
  }

  public Optional<EthPeer> idlePeer(final long withBlocksUpTo) {
    return idlePeers().filter(p -> p.chainState().getEstimatedHeight() >= withBlocksUpTo).findAny();
  }

  @FunctionalInterface
  public interface ConnectCallback {
    void onPeerConnected(EthPeer newPeer);
  }

  @Override
  public String toString() {
    final String connectionsList =
        String.join(
            ",", connections.values().stream().map(EthPeer::toString).collect(Collectors.toList()));
    return "EthPeers{" + "connections=" + connectionsList + '}';
  }

  private void invokeConnectionCallbacks(final EthPeer peer) {
    connectCallbacks.forEach(cb -> cb.onPeerConnected(peer));
  }
}

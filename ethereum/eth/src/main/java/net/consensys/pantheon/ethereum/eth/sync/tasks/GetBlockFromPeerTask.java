package net.consensys.pantheon.ethereum.eth.sync.tasks;

import net.consensys.pantheon.ethereum.core.Block;
import net.consensys.pantheon.ethereum.core.BlockHeader;
import net.consensys.pantheon.ethereum.core.Hash;
import net.consensys.pantheon.ethereum.eth.manager.AbstractPeerTask;
import net.consensys.pantheon.ethereum.eth.manager.EthContext;
import net.consensys.pantheon.ethereum.eth.manager.EthPeer;
import net.consensys.pantheon.ethereum.eth.manager.exceptions.IncompleteResultsException;
import net.consensys.pantheon.ethereum.mainnet.ProtocolSchedule;
import net.consensys.pantheon.ethereum.p2p.api.PeerConnection.PeerNotConnected;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Downloads a block from a peer. Will complete exceptionally if block cannot be downloaded. */
public class GetBlockFromPeerTask extends AbstractPeerTask<Block> {
  private static final Logger LOG = LogManager.getLogger();

  private final ProtocolSchedule<?> protocolSchedule;
  private final Hash hash;

  protected GetBlockFromPeerTask(
      final ProtocolSchedule<?> protocolSchedule, final EthContext ethContext, final Hash hash) {
    super(ethContext);
    this.protocolSchedule = protocolSchedule;
    this.hash = hash;
  }

  public static GetBlockFromPeerTask create(
      final ProtocolSchedule<?> protocolSchedule, final EthContext ethContext, final Hash hash) {
    return new GetBlockFromPeerTask(protocolSchedule, ethContext, hash);
  }

  @Override
  protected void executeTaskWithPeer(final EthPeer peer) throws PeerNotConnected {
    LOG.info("Downloading block {} from peer {}.", hash, peer);
    downloadHeader(peer)
        .thenCompose(this::completeBlock)
        .whenComplete(
            (r, t) -> {
              if (t != null) {
                LOG.info("Failed to download block {} from peer {}.", hash, peer);
                result.get().completeExceptionally(t);
              } else if (r.getResult().isEmpty()) {
                LOG.info("Failed to download block {} from peer {}.", hash, peer);
                result.get().completeExceptionally(new IncompleteResultsException());
              } else {
                LOG.info("Successfully downloaded block {} from peer {}.", hash, peer);
                result.get().complete(new PeerTaskResult<>(r.getPeer(), r.getResult().get(0)));
              }
            });
  }

  private CompletableFuture<PeerTaskResult<List<BlockHeader>>> downloadHeader(final EthPeer peer) {
    return executeSubTask(
        () ->
            GetHeadersFromPeerByHashTask.forSingleHash(protocolSchedule, ethContext, hash)
                .assignPeer(peer)
                .run());
  }

  private CompletableFuture<PeerTaskResult<List<Block>>> completeBlock(
      final PeerTaskResult<List<BlockHeader>> headerResult) {
    if (headerResult.getResult().isEmpty()) {
      final CompletableFuture<PeerTaskResult<List<Block>>> future = new CompletableFuture<>();
      future.completeExceptionally(new IncompleteResultsException());
      return future;
    }

    return executeSubTask(
        () -> {
          final GetBodiesFromPeerTask<?> task =
              GetBodiesFromPeerTask.forHeaders(
                  protocolSchedule, ethContext, headerResult.getResult());
          task.assignPeer(headerResult.getPeer());
          return task.run();
        });
  }
}

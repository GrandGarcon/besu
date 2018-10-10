package net.consensys.pantheon.ethereum.p2p.discovery;

import static com.google.common.net.InetAddresses.isInetAddress;
import static org.assertj.core.api.Assertions.assertThat;

import net.consensys.pantheon.ethereum.p2p.discovery.internal.FindNeighborsPacketData;
import net.consensys.pantheon.ethereum.p2p.discovery.internal.NeighborsPacketData;
import net.consensys.pantheon.ethereum.p2p.discovery.internal.Packet;
import net.consensys.pantheon.ethereum.p2p.discovery.internal.PingPacketData;
import net.consensys.pantheon.ethereum.p2p.discovery.internal.PongPacketData;
import net.consensys.pantheon.ethereum.p2p.peers.Peer;
import net.consensys.pantheon.util.NetworkUtility;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import io.pkts.Pcap;
import io.pkts.protocol.Protocol;
import io.vertx.core.buffer.Buffer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class PeerDiscoveryPacketPcapSedesTest {

  private final Instant timestamp;
  private final byte[] data;

  public PeerDiscoveryPacketPcapSedesTest(final Instant instant, final byte[] data) {
    this.timestamp = instant;
    this.data = data;
  }

  @Parameterized.Parameters(name = "{index}: {0}")
  public static Collection<Object[]> parameters() throws IOException {
    final Pcap pcap =
        Pcap.openStream(
            PeerDiscoveryPacketPcapSedesTest.class
                .getClassLoader()
                .getResourceAsStream("udp.pcap"));

    final List<Object[]> parameters = new ArrayList<>();
    pcap.loop(
        packet -> {
          if (packet.hasProtocol(Protocol.UDP)) {
            final byte[] data = packet.getPacket(Protocol.UDP).getPayload().getArray();
            parameters.add(
                new Object[] {Instant.ofEpochMilli(packet.getArrivalTime() / 1000), data});
          }
          return true;
        });
    return parameters;
  }

  @Test
  public void serializeDeserialize() {
    final Packet packet = Packet.decode(Buffer.buffer(data));
    assertThat(packet.getType()).isNotNull();
    assertThat(packet.getNodeId()).isNotNull();
    assertThat(packet.getNodeId().extractArray()).hasSize(64);

    switch (packet.getType()) {
      case PING:
        assertThat(packet.getPacketData(PingPacketData.class)).isPresent();
        final PingPacketData ping = packet.getPacketData(PingPacketData.class).get();

        assertThat(ping.getTo()).isNotNull();
        assertThat(ping.getFrom()).isNotNull();
        assertThat(isInetAddress(ping.getTo().getHost())).isTrue();
        assertThat(isInetAddress(ping.getFrom().getHost())).isTrue();
        assertThat(ping.getTo().getUdpPort()).isPositive();
        assertThat(ping.getFrom().getUdpPort()).isPositive();
        ping.getTo().getTcpPort().ifPresent(p -> assertThat(p).isPositive());
        ping.getFrom().getTcpPort().ifPresent(p -> assertThat(p).isPositive());
        assertThat(ping.getExpiration()).isPositive();
        break;

      case PONG:
        assertThat(packet.getPacketData(PongPacketData.class)).isPresent();
        final PongPacketData pong = packet.getPacketData(PongPacketData.class).get();

        assertThat(pong.getTo()).isNotNull();
        assertThat(isInetAddress(pong.getTo().getHost())).isTrue();
        assertThat(pong.getTo().getUdpPort()).isPositive();
        pong.getTo().getTcpPort().ifPresent(p -> assertThat(p).isPositive());
        assertThat(pong.getPingHash().extractArray()).hasSize(32);
        assertThat(pong.getExpiration()).isPositive();
        break;

      case FIND_NEIGHBORS:
        assertThat(packet.getPacketData(FindNeighborsPacketData.class)).isPresent();
        final FindNeighborsPacketData data =
            packet.getPacketData(FindNeighborsPacketData.class).get();
        assertThat(data.getExpiration())
            .isBetween(timestamp.getEpochSecond() - 10000, timestamp.getEpochSecond() + 10000);
        assertThat(data.getTarget().extractArray()).hasSize(64);
        assertThat(packet.getNodeId().extractArray()).hasSize(64);
        break;

      case NEIGHBORS:
        assertThat(packet.getPacketData(NeighborsPacketData.class)).isPresent();
        final NeighborsPacketData neighbors = packet.getPacketData(NeighborsPacketData.class).get();
        assertThat(neighbors.getExpiration()).isGreaterThan(0);
        assertThat(neighbors.getNodes()).isNotEmpty();

        for (final Peer p : neighbors.getNodes()) {
          assertThat(NetworkUtility.isValidPort(p.getEndpoint().getUdpPort())).isTrue();
          assertThat(isInetAddress(p.getEndpoint().getHost())).isTrue();
          assertThat(p.getId().extractArray()).hasSize(64);
        }

        break;
    }

    final byte[] encoded = packet.encode().getBytes();
    assertThat(encoded).isEqualTo(data);
  }
}

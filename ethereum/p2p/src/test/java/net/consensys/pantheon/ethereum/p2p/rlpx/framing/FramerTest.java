package net.consensys.pantheon.ethereum.p2p.rlpx.framing;

import static io.netty.buffer.ByteBufUtil.decodeHexDump;
import static io.netty.buffer.Unpooled.buffer;
import static io.netty.buffer.Unpooled.wrappedBuffer;
import static java.util.stream.Collectors.toList;
import static java.util.stream.StreamSupport.stream;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import net.consensys.pantheon.ethereum.p2p.api.MessageData;
import net.consensys.pantheon.ethereum.p2p.rlpx.handshake.HandshakeSecrets;
import net.consensys.pantheon.ethereum.p2p.wire.RawMessage;

import java.io.IOException;
import java.util.List;
import java.util.Random;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.Test;

public class FramerTest {
  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Test
  public void shouldThrowExceptionWhenMessageTooLong() {
    final byte[] aes = {
      0xa, 0x2, 0xa, 0x2, 0xa, 0x2, 0xa, 0x2, 0xa, 0x2, 0xa, 0x2, 0xa, 0x2, 0xa, 0x2, 0xa, 0x2, 0xa,
      0x2, 0xa, 0x2, 0xa, 0x2, 0xa, 0x2, 0xa, 0x2, 0xa, 0x2, 0xa, 0x2
    };
    final byte[] mac = {
      0xa, 0x2, 0xa, 0x2, 0xa, 0x2, 0xa, 0x2, 0xa, 0x2, 0xa, 0x2, 0xa, 0x2, 0xa, 0x2, 0xa, 0x2, 0xa,
      0x2, 0xa, 0x2, 0xa, 0x2, 0xa, 0x2, 0xa, 0x2, 0xa, 0x2, 0xa, 0x2
    };

    final byte[] byteArray = new byte[0xFFFFFF];
    new Random().nextBytes(byteArray);
    final ByteBuf buf = wrappedBuffer(byteArray);
    final MessageData ethMessage = new RawMessage(0x00, buf);

    final HandshakeSecrets secrets = new HandshakeSecrets(aes, mac, mac);
    final Framer framer = new Framer(secrets);

    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> framer.frame(ethMessage))
        .withMessageContaining("Message size in excess of maximum length.");
  }

  @Test
  public void deframeOne() throws IOException {
    // Load test data.
    final JsonNode td = MAPPER.readTree(FramerTest.class.getResource("/peer2.json"));
    final HandshakeSecrets secrets = secretsFrom(td, false);

    final Framer framer = new Framer(secrets);
    final JsonNode m = td.get("messages").get(0);

    assertThatCode(() -> framer.deframe(wrappedBuffer(decodeHexDump(m.get("data").asText()))))
        .doesNotThrowAnyException();
  }

  @Test
  public void deframeManyNoFragmentation() throws IOException {
    // Load test data.
    final JsonNode td = MAPPER.readTree(FramerTest.class.getResource("/peer1.json"));
    final HandshakeSecrets secrets = secretsFrom(td, false);

    final JsonNode messages = td.get("messages");
    final ByteBuf buf = buffer();

    messages.forEach(n -> buf.writeBytes(decodeHexDump(n.get("data").asText())));

    final Framer framer = new Framer(secrets);
    int i = 0;
    for (MessageData m = framer.deframe(buf); m != null; m = framer.deframe(buf)) {
      final int expectedFrameSize = messages.get(i++).get("frame_size").asInt();
      assertThat(expectedFrameSize).isEqualTo(m.getSize() + 1); // +1 for message id byte.
    }
    // All messages were processed.
    assertThat(i).isEqualTo(messages.size());
  }

  @Test
  public void deframeManyWithExtremeOneByteFragmentation() throws IOException {
    // Load test data.
    final JsonNode td = MAPPER.readTree(FramerTest.class.getResource("/peer1.json"));
    final HandshakeSecrets secrets = secretsFrom(td, false);

    final JsonNode messages = td.get("messages");

    //
    // TCP is a stream-based, fragmenting protocol; protocol messages can arrive chunked in smaller
    // packets
    // arbitrarily; however it is guaranteed that fragments will arrive in order.
    //
    // Here we test our framer is capable of reassembling fragments into protocol messages, by
    // simulating
    // an aggressive 1-byte fragmentation model.
    //
    final ByteBuf all = buffer();
    messages.forEach(n -> all.writeBytes(decodeHexDump(n.get("data").asText())));

    final Framer framer = new Framer(secrets);

    int i = 0;
    final ByteBuf in = buffer();
    while (all.isReadable()) {
      in.writeByte(all.readByte());
      final MessageData msg = framer.deframe(in);
      if (msg != null) {
        final int expectedFrameSize = messages.get(i++).get("frame_size").asInt();
        assertThat(expectedFrameSize).isEqualTo(msg.getSize() + 1); // +1 for message id byte.
        assertThat(in.readableBytes()).isZero();
      }
    }
    // All messages were processed.
    assertThat(i).isEqualTo(messages.size());
  }

  @Test
  public void frameMessage() throws IOException {
    // This is a circular test.
    //
    // This test decrypts all messages in the test vectors; it then impersonates the sending end
    // by swapping the ingress and egress MACs and frames the plaintext messages.
    // We then verify if the resulting ciphertexts are equal to our test vectors.
    //
    final JsonNode td = MAPPER.readTree(FramerTest.class.getResource("/peer1.json"));
    HandshakeSecrets secrets = secretsFrom(td, false);
    Framer framer = new Framer(secrets);

    final JsonNode messages = td.get("messages");
    final List<MessageData> decrypted =
        stream(messages.spliterator(), false)
            .map(n -> decodeHexDump(n.get("data").asText()))
            .map(Unpooled::wrappedBuffer)
            .map(framer::deframe)
            .collect(toList());

    secrets = secretsFrom(td, true);
    framer = new Framer(secrets);

    for (int i = 0; i < decrypted.size(); i++) {
      final ByteBuf b = framer.frame(decrypted.get(i));
      final byte[] enc = new byte[b.readableBytes()];
      b.readBytes(enc);
      final byte[] expected = decodeHexDump(messages.get(i).get("data").asText());
      assertThat(expected).isEqualTo(enc);
    }
  }

  private HandshakeSecrets secretsFrom(final JsonNode td, final boolean swap) {
    final byte[] aes = decodeHexDump(td.get("aes_secret").asText());
    final byte[] mac = decodeHexDump(td.get("mac_secret").asText());

    final byte[] e1 = decodeHexDump(td.get("egress_gen").get(0).asText());
    final byte[] e2 = decodeHexDump(td.get("egress_gen").get(1).asText());
    final byte[] i1 = decodeHexDump(td.get("ingress_gen").get(0).asText());
    final byte[] i2 = decodeHexDump(td.get("ingress_gen").get(1).asText());

    // 3rd parameter (token) is irrelevant.
    final HandshakeSecrets secrets = new HandshakeSecrets(aes, mac, mac);

    if (!swap) {
      secrets.updateEgress(e1).updateEgress(e2).updateIngress(i1).updateIngress(i2);
    } else {
      secrets.updateIngress(e1).updateIngress(e2).updateEgress(i1).updateEgress(i2);
    }

    return secrets;
  }
}

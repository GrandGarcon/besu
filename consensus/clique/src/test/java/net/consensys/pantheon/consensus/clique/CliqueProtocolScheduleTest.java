package net.consensys.pantheon.consensus.clique;

import static org.assertj.core.api.Java6Assertions.assertThat;

import net.consensys.pantheon.crypto.SECP256K1.KeyPair;
import net.consensys.pantheon.ethereum.mainnet.ProtocolSchedule;
import net.consensys.pantheon.ethereum.mainnet.ProtocolSpec;

import io.vertx.core.json.JsonObject;
import org.junit.Test;

public class CliqueProtocolScheduleTest {

  @Test
  public void protocolSpecsAreCreatedAtBlockDefinedInJson() {
    final String jsonInput =
        "{\"chainId\": 4,\n"
            + "\"homesteadBlock\": 1,\n"
            + "\"eip150Block\": 2,\n"
            + "\"eip155Block\": 3,\n"
            + "\"eip158Block\": 3,\n"
            + "\"byzantiumBlock\": 1035301}";

    final JsonObject jsonObject = new JsonObject(jsonInput);

    final ProtocolSchedule<CliqueContext> protocolSchedule =
        CliqueProtocolSchedule.create(jsonObject, KeyPair.generate());

    final ProtocolSpec<CliqueContext> homesteadSpec = protocolSchedule.getByBlockNumber(1);
    final ProtocolSpec<CliqueContext> tangerineWhistleSpec = protocolSchedule.getByBlockNumber(2);
    final ProtocolSpec<CliqueContext> spuriousDragonSpec = protocolSchedule.getByBlockNumber(3);
    final ProtocolSpec<CliqueContext> byzantiumSpec = protocolSchedule.getByBlockNumber(1035301);

    assertThat(homesteadSpec.equals(tangerineWhistleSpec)).isFalse();
    assertThat(tangerineWhistleSpec.equals(spuriousDragonSpec)).isFalse();
    assertThat(spuriousDragonSpec.equals(byzantiumSpec)).isFalse();
  }
}

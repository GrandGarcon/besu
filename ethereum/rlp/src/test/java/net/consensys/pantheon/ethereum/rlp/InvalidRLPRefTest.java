package net.consensys.pantheon.ethereum.rlp;

import net.consensys.pantheon.testutil.JsonTestParameters;

import java.util.Collection;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/** The Ethereum reference RLP tests. */
@RunWith(Parameterized.class)
public class InvalidRLPRefTest {

  private static final String[] TEST_CONFIG_FILES = {
    // TODO: upstream these additional tests to the ethereum tests repo
    "net/consensys/pantheon/ethereum/rlp/invalidRLPTest.json", "RLPTests/invalidRLPTest.json"
  };

  private final InvalidRLPRefTestCaseSpec spec;

  public InvalidRLPRefTest(final String name, final InvalidRLPRefTestCaseSpec spec) {
    this.spec = spec;
  }

  @Parameters(name = "Name: {0}")
  public static Collection<Object[]> getTestParametersForConfig() {
    return JsonTestParameters.create(InvalidRLPRefTestCaseSpec.class).generate(TEST_CONFIG_FILES);
  }

  /** Test RLP decoding. */
  @Test(expected = RLPException.class)
  public void decode() throws Exception {
    RLP.decode(spec.getRLP());
  }
}

package net.consensys.pantheon.consensus.ibft;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static net.consensys.pantheon.consensus.ibft.IbftProtocolContextFixture.protocolContext;
import static org.assertj.core.api.Assertions.assertThat;

import net.consensys.pantheon.consensus.common.VoteType;
import net.consensys.pantheon.crypto.SECP256K1;
import net.consensys.pantheon.crypto.SECP256K1.KeyPair;
import net.consensys.pantheon.crypto.SECP256K1.Signature;
import net.consensys.pantheon.ethereum.core.Address;
import net.consensys.pantheon.ethereum.core.BlockHeader;
import net.consensys.pantheon.ethereum.core.BlockHeaderTestFixture;
import net.consensys.pantheon.ethereum.core.Hash;
import net.consensys.pantheon.ethereum.mainnet.BlockHeaderValidator;
import net.consensys.pantheon.ethereum.mainnet.HeaderValidationMode;
import net.consensys.pantheon.util.bytes.BytesValue;
import net.consensys.pantheon.util.uint.UInt256;

import java.math.BigInteger;
import java.util.List;

import org.junit.Test;

public class IbftBlockHeaderValidationRulesetFactoryTest {

  @Test
  public void ibftValidateHeaderPasses() {
    final KeyPair proposerKeyPair = KeyPair.generate();
    final Address proposerAddress =
        Address.extract(Hash.hash(proposerKeyPair.getPublicKey().getEncodedBytes()));

    final List<Address> validators = singletonList(proposerAddress);

    final BlockHeader parentHeader = buildBlockHeader(1, proposerKeyPair, validators, null);
    final BlockHeader blockHeader = buildBlockHeader(2, proposerKeyPair, validators, parentHeader);

    final BlockHeaderValidator<IbftContext> validator =
        IbftBlockHeaderValidationRulesetFactory.ibftBlockHeaderValidator(5);

    assertThat(
            validator.validateHeader(
                blockHeader, parentHeader, protocolContext(validators), HeaderValidationMode.FULL))
        .isTrue();
  }

  @Test
  public void ibftValidateHeaderFails() {
    final KeyPair proposerKeyPair = KeyPair.generate();
    final Address proposerAddress =
        Address.extract(Hash.hash(proposerKeyPair.getPublicKey().getEncodedBytes()));

    final List<Address> validators = singletonList(proposerAddress);

    final BlockHeader parentHeader = buildBlockHeader(1, proposerKeyPair, validators, null);
    final BlockHeader blockHeader = buildBlockHeader(2, proposerKeyPair, validators, null);

    final BlockHeaderValidator<IbftContext> validator =
        IbftBlockHeaderValidationRulesetFactory.ibftBlockHeaderValidator(5);

    assertThat(
            validator.validateHeader(
                blockHeader, parentHeader, protocolContext(validators), HeaderValidationMode.FULL))
        .isFalse();
  }

  private BlockHeader buildBlockHeader(
      final long number,
      final KeyPair proposerKeyPair,
      final List<Address> validators,
      final BlockHeader parent) {
    final BlockHeaderTestFixture builder = new BlockHeaderTestFixture();

    if (parent != null) {
      builder.parentHash(parent.getHash());
    }
    builder.number(number);
    builder.gasLimit(5000);
    builder.timestamp(6000 * number);
    builder.mixHash(
        Hash.fromHexString("0x63746963616c2062797a616e74696e65206661756c7420746f6c6572616e6365"));
    builder.ommersHash(Hash.EMPTY_LIST_HASH);
    builder.nonce(VoteType.DROP.getNonceValue());
    builder.difficulty(UInt256.ONE);

    // Construct an extraData block
    final IbftExtraData initialIbftExtraData =
        new IbftExtraData(
            BytesValue.wrap(new byte[IbftExtraData.EXTRA_VANITY_LENGTH]),
            emptyList(),
            Signature.create(BigInteger.ONE, BigInteger.ONE, (byte) 0),
            validators);

    builder.extraData(initialIbftExtraData.encode());
    final BlockHeader parentHeader = builder.buildHeader();
    final Hash proposerSealHash =
        IbftBlockHashing.calculateDataHashForProposerSeal(parentHeader, initialIbftExtraData);

    final Signature proposerSignature = SECP256K1.sign(proposerSealHash, proposerKeyPair);

    final IbftExtraData proposedData =
        new IbftExtraData(
            BytesValue.wrap(new byte[IbftExtraData.EXTRA_VANITY_LENGTH]),
            singletonList(proposerSignature),
            proposerSignature,
            validators);

    final Hash headerHashForCommitters =
        IbftBlockHashing.calculateDataHashForCommittedSeal(parentHeader, proposedData);
    final Signature proposerAsCommitterSignature =
        SECP256K1.sign(headerHashForCommitters, proposerKeyPair);

    final IbftExtraData sealedData =
        new IbftExtraData(
            BytesValue.wrap(new byte[IbftExtraData.EXTRA_VANITY_LENGTH]),
            singletonList(proposerAsCommitterSignature),
            proposerSignature,
            validators);

    builder.extraData(sealedData.encode());
    return builder.buildHeader();
  }
}

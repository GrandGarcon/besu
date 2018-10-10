package net.consensys.pantheon.consensus.ibft;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import net.consensys.pantheon.consensus.common.EpochManager;
import net.consensys.pantheon.consensus.common.VoteTally;
import net.consensys.pantheon.consensus.common.VoteType;
import net.consensys.pantheon.crypto.SECP256K1;
import net.consensys.pantheon.crypto.SECP256K1.KeyPair;
import net.consensys.pantheon.crypto.SECP256K1.Signature;
import net.consensys.pantheon.ethereum.chain.MutableBlockchain;
import net.consensys.pantheon.ethereum.core.Address;
import net.consensys.pantheon.ethereum.core.BlockHeader;
import net.consensys.pantheon.ethereum.core.BlockHeaderTestFixture;
import net.consensys.pantheon.ethereum.core.Hash;
import net.consensys.pantheon.util.bytes.BytesValue;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;

import org.junit.Test;

public class VoteTallyUpdaterTest {

  private static final long EPOCH_LENGTH = 30_000;
  public static final Signature INVALID_SEAL =
      Signature.create(BigInteger.ONE, BigInteger.ONE, (byte) 0);
  private final VoteTally voteTally = mock(VoteTally.class);
  private final MutableBlockchain blockchain = mock(MutableBlockchain.class);
  private final KeyPair proposerKeyPair = KeyPair.generate();
  private final Address proposerAddress =
      Address.extract(Hash.hash(proposerKeyPair.getPublicKey().getEncodedBytes()));
  private final Address subject = Address.fromHexString("007f4a23ca00cd043d25c2888c1aa5688f81a344");
  private final Address validator1 =
      Address.fromHexString("00dae27b350bae20c5652124af5d8b5cba001ec1");

  private final VoteTallyUpdater updater = new VoteTallyUpdater(new EpochManager(EPOCH_LENGTH));

  @Test
  public void voteTallyUpdatedWithVoteFromBlock() {
    final BlockHeaderTestFixture headerBuilder = new BlockHeaderTestFixture();
    headerBuilder.number(1);
    headerBuilder.nonce(VoteType.ADD.getNonceValue());
    headerBuilder.coinbase(subject);
    addProposer(headerBuilder);
    final BlockHeader header = headerBuilder.buildHeader();

    updater.updateForBlock(header, voteTally);

    verify(voteTally).addVote(proposerAddress, subject, VoteType.ADD);
  }

  @Test
  public void voteTallyNotUpdatedWhenBlockHasNoVoteSubject() {
    final BlockHeaderTestFixture headerBuilder = new BlockHeaderTestFixture();
    headerBuilder.number(1);
    headerBuilder.nonce(VoteType.ADD.getNonceValue());
    headerBuilder.coinbase(Address.fromHexString("0000000000000000000000000000000000000000"));
    addProposer(headerBuilder);
    final BlockHeader header = headerBuilder.buildHeader();

    updater.updateForBlock(header, voteTally);

    verifyZeroInteractions(voteTally);
  }

  @Test
  public void outstandingVotesDiscardedWhenEpochReached() {
    final BlockHeaderTestFixture headerBuilder = new BlockHeaderTestFixture();
    headerBuilder.number(EPOCH_LENGTH);
    headerBuilder.nonce(VoteType.ADD.getNonceValue());
    headerBuilder.coinbase(Address.fromHexString("0000000000000000000000000000000000000000"));
    addProposer(headerBuilder);
    final BlockHeader header = headerBuilder.buildHeader();

    updater.updateForBlock(header, voteTally);

    verify(voteTally).discardOutstandingVotes();
    verifyNoMoreInteractions(voteTally);
  }

  @Test
  public void buildVoteTallyByExtractingValidatorsFromGenesisBlock() {
    final BlockHeaderTestFixture headerBuilder = new BlockHeaderTestFixture();
    headerBuilder.number(0);
    headerBuilder.nonce(VoteType.ADD.getNonceValue());
    headerBuilder.coinbase(Address.fromHexString("0000000000000000000000000000000000000000"));
    addProposer(headerBuilder, asList(subject, validator1));
    final BlockHeader header = headerBuilder.buildHeader();

    when(blockchain.getChainHeadBlockNumber()).thenReturn(EPOCH_LENGTH);
    when(blockchain.getBlockHeader(EPOCH_LENGTH)).thenReturn(Optional.of(header));

    final VoteTally voteTally = updater.buildVoteTallyFromBlockchain(blockchain);
    assertThat(voteTally.getCurrentValidators()).containsExactly(subject, validator1);
  }

  @Test
  public void buildVoteTallyByExtractingValidatorsFromEpochBlock() {
    final BlockHeaderTestFixture headerBuilder = new BlockHeaderTestFixture();
    headerBuilder.number(EPOCH_LENGTH);
    headerBuilder.nonce(VoteType.ADD.getNonceValue());
    headerBuilder.coinbase(Address.fromHexString("0000000000000000000000000000000000000000"));
    addProposer(headerBuilder, asList(subject, validator1));
    final BlockHeader header = headerBuilder.buildHeader();

    when(blockchain.getChainHeadBlockNumber()).thenReturn(EPOCH_LENGTH);
    when(blockchain.getBlockHeader(EPOCH_LENGTH)).thenReturn(Optional.of(header));

    final VoteTally voteTally = updater.buildVoteTallyFromBlockchain(blockchain);
    assertThat(voteTally.getCurrentValidators()).containsExactly(subject, validator1);
  }

  @Test
  public void addVotesFromBlocksAfterMostRecentEpoch() {
    final BlockHeaderTestFixture headerBuilder = new BlockHeaderTestFixture();
    headerBuilder.number(EPOCH_LENGTH);
    headerBuilder.nonce(VoteType.ADD.getNonceValue());
    headerBuilder.coinbase(Address.fromHexString("0000000000000000000000000000000000000000"));
    addProposer(headerBuilder, singletonList(validator1));
    final BlockHeader epochHeader = headerBuilder.buildHeader();

    headerBuilder.number(EPOCH_LENGTH + 1);
    headerBuilder.coinbase(subject);
    final BlockHeader voteBlockHeader = headerBuilder.buildHeader();

    when(blockchain.getChainHeadBlockNumber()).thenReturn(EPOCH_LENGTH + 1);
    when(blockchain.getBlockHeader(EPOCH_LENGTH)).thenReturn(Optional.of(epochHeader));
    when(blockchain.getBlockHeader(EPOCH_LENGTH + 1)).thenReturn(Optional.of(voteBlockHeader));

    final VoteTally voteTally = updater.buildVoteTallyFromBlockchain(blockchain);
    assertThat(voteTally.getCurrentValidators()).containsExactly(subject, validator1);
  }

  private void addProposer(final BlockHeaderTestFixture builder) {
    addProposer(builder, singletonList(proposerAddress));
  }

  private void addProposer(final BlockHeaderTestFixture builder, final List<Address> validators) {

    final IbftExtraData initialIbftExtraData =
        new IbftExtraData(
            BytesValue.wrap(new byte[IbftExtraData.EXTRA_VANITY_LENGTH]),
            emptyList(),
            INVALID_SEAL,
            validators);

    builder.extraData(initialIbftExtraData.encode());
    final BlockHeader header = builder.buildHeader();
    final Hash proposerSealHash =
        IbftBlockHashing.calculateDataHashForProposerSeal(header, initialIbftExtraData);

    final Signature proposerSignature = SECP256K1.sign(proposerSealHash, proposerKeyPair);

    final IbftExtraData proposedData =
        new IbftExtraData(
            BytesValue.wrap(new byte[IbftExtraData.EXTRA_VANITY_LENGTH]),
            singletonList(proposerSignature),
            proposerSignature,
            validators);

    final Hash headerHashForCommitters =
        IbftBlockHashing.calculateDataHashForCommittedSeal(header, proposedData);
    final Signature proposerAsCommitterSignature =
        SECP256K1.sign(headerHashForCommitters, proposerKeyPair);

    final IbftExtraData sealedData =
        new IbftExtraData(
            BytesValue.wrap(new byte[IbftExtraData.EXTRA_VANITY_LENGTH]),
            singletonList(proposerAsCommitterSignature),
            proposerSignature,
            validators);

    builder.extraData(sealedData.encode());
  }
}

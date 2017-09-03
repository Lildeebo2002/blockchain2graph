package com.oakinvest.b2g.batch.bitcoin;

import com.oakinvest.b2g.domain.bitcoin.BitcoinAddress;
import com.oakinvest.b2g.domain.bitcoin.BitcoinBlock;
import com.oakinvest.b2g.domain.bitcoin.BitcoinBlockState;
import com.oakinvest.b2g.dto.ext.bitcoin.bitcoind.BitcoindBlockData;
import com.oakinvest.b2g.repository.bitcoin.BitcoinRepositories;
import com.oakinvest.b2g.service.BitcoinDataService;
import com.oakinvest.b2g.service.StatusService;
import com.oakinvest.b2g.service.bitcoin.BitcoinDataServiceCacheLoader;
import com.oakinvest.b2g.service.bitcoin.BitcoinDataServiceCacheStore;
import com.oakinvest.b2g.util.bitcoin.batch.BitcoinBatchTemplate;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;

import static com.oakinvest.b2g.configuration.ParametersConfiguration.BITCOIND_BUFFER_SIZE;
import static com.oakinvest.b2g.domain.bitcoin.BitcoinBlockState.BLOCK_DATA_IMPORTED;

/**
 * Bitcoin import blocks batch.
 * Created by straumat on 27/02/17.
 */
@Component
public class BitcoinBatchBlocks extends BitcoinBatchTemplate {

    /**
     * Bitcoind cache loader.
     */
    private final BitcoinDataServiceCacheLoader bitcoindCacheLoader;

	/**
	 * Log prefix.
	 */
	private static final String PREFIX = "Blocks batch";

    /**
     * Constructor.
     *
     * @param newBitcoinRepositories    bitcoin repositories
     * @param newBitcoinDataService     bitcoin data service
     * @param newStatus                 status
     * @param newBitcoindCacheLoader    bitcoin cache loader
     * @param newCacheStore             cache store
     */
    public BitcoinBatchBlocks(final BitcoinRepositories newBitcoinRepositories, final BitcoinDataService newBitcoinDataService, final StatusService newStatus, final BitcoinDataServiceCacheLoader newBitcoindCacheLoader, final BitcoinDataServiceCacheStore newCacheStore) {
        super(newBitcoinRepositories, newBitcoinDataService, newStatus, newCacheStore);
        bitcoindCacheLoader = newBitcoindCacheLoader;
    }

	/**
	 * Returns the log prefix to display in each log.
	 */
	@Override
	public final String getLogPrefix() {
		return PREFIX;
	}

	/**
	 * Return the block to process.
	 *
	 * @return block to process.
	 */
	@Override
    protected final Optional<Integer> getBlockHeightToProcess() {
		// We retrieve the next block to process according to the database.
		int blockToProcess = (int) (getBlockRepository().count() + 1);
        final Optional<Integer> totalBlockCount = getBitcoinDataService().getBlockCount();

		// We check if that next block exists by retrieving the block count.
        if (totalBlockCount.isPresent()) {
				// We update the global status of blockcount (if needed).
				if (totalBlockCount.get() != getStatus().getTotalBlockCount()) {
					getStatus().setTotalBlockCount(totalBlockCount.get());
				}
				// We return the block to process.
				if (blockToProcess <= totalBlockCount.get()) {
				    // We load the cache.
                    if (blockToProcess + BITCOIND_BUFFER_SIZE <= totalBlockCount.get()) {
                        bitcoindCacheLoader.loadCache(blockToProcess);
                    }
					// If there is still block after this one, we continue.
                    return Optional.of(blockToProcess);
				} else {
					return Optional.empty();
				}
			} else {
				// Error while retrieving the number of blocks in bitcoind.
				return Optional.empty();
			}
	}

	/**
	 * Process block.
	 *
	 * @param blockHeight block height to process.
	 */
	@Override
	protected final Optional<BitcoinBlock> processBlock(final int blockHeight) {
		Optional<BitcoindBlockData> blockData = getBitcoinDataService().getBlockData(blockHeight);

		// -------------------------------------------------------------------------------------------------------------
		// If we have the data.
		if (blockData.isPresent()) {

			// ---------------------------------------------------------------------------------------------------------
			// Then, if the block doesn't exists, we map it to save it.
			BitcoinBlock blockToProcess = getBlockRepository().findByHash(blockData.get().getBlock().getHash());
			if (blockToProcess == null) {
				blockToProcess = getMapper().blockDataToBitcoinBlock(blockData.get());
            }

            // TODO Remove - only for debug purpose.
            blockToProcess.getTransactions()
                    .forEach(t -> {
                        if (t.getOutputs().size() != blockData.get().getRawTransactionResult(t.getTxId()).get().getVout().size()) {
                            addError("Should never append (outputs)");
                        }
                        if (t.getInputs().size() != blockData.get().getRawTransactionResult(t.getTxId()).get().getVin().size()) {
                            addError("Should never append (inputs)");
                        }

                    });

            // ---------------------------------------------------------------------------------------------------------
            // We create all the addresses.
            addLog("Listing all addresses from " + blockToProcess.getTx().size() + " transaction(s)");
            blockData.get().getAddresses()
                    .stream()
                    //.parallelStream() // In parallel.
                    .filter(Objects::nonNull) // If the address is not null.
                    .filter(address -> !getAddressRepository().exists(address))  // If the address doesn't exists.
                    .forEach(a -> {
                        BitcoinAddress address = new BitcoinAddress(a);
                        getAddressRepository().save(address);
                        addLog("- Address " + address + " created with id " + address.getId());
                    });

            // ---------------------------------------------------------------------------------------------------------
			// We return the block.
			return Optional.of(blockToProcess);

		} else {
			// Or nothing if we did not retrieve the data.
			addError("No response from bitcoind for block n°" + getFormattedBlockHeight(blockHeight));
			return Optional.empty();
		}
	}

	/**
	 * Return the state to set to the block that has been processed.
	 *
	 * @return state to set of the block that has been processed.
	 */
	@Override
	protected final BitcoinBlockState getNewStateOfProcessedBlock() {
		return BLOCK_DATA_IMPORTED;
	}

}

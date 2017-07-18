package com.oakinvest.b2g.repository.bitcoin;

import com.oakinvest.b2g.domain.bitcoin.BitcoinTransactionOutput;
import org.springframework.data.neo4j.annotation.Query;
import org.springframework.data.neo4j.repository.GraphRepository;
import org.springframework.stereotype.Repository;

/**
 * BitcoinTransactionOutput repository.
 * Created by straumat on 22/03/17.
 */
@Repository
public interface BitcoinTransactionOutputRepository extends GraphRepository<BitcoinTransactionOutput> {

    /**
     * Find a transaction output with tx id & key.
     *
     * @param txId transaction id
     * @param index index
     * @return transaction output
     */
    @Query("MATCH (to:BitcoinTransactionOutput) USING INDEX to:BitcoinTransactionOutput(txid, n) WHERE to.txid = {0} and to.n = {1} RETURN to")
    BitcoinTransactionOutput findByTxIdAndIndex(String txId, int index);

    /**
     * Find a transaction by key (txid-n).
     *
     * @param key key
     * @return transaction output
     */
    @Query("MATCH (to:BitcoinTransactionOutput) USING INDEX to:BitcoinTransactionOutput(key) WHERE to.key = {0} RETURN to")
    BitcoinTransactionOutput findByKey(String key);


}

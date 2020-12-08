package org.xrpl.xrpl4j.model.client.admin;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.primitives.UnsignedInteger;
import org.immutables.value.Value.Immutable;
import org.xrpl.xrpl4j.model.client.rippled.XrplResult;
import org.xrpl.xrpl4j.model.jackson.modules.LedgerIndexSerializer;

/**
 * Result from request to advance_ledger.
 */
@Immutable
@JsonSerialize(as = ImmutableAcceptLedgerResult.class)
@JsonDeserialize(as = ImmutableAcceptLedgerResult.class)
public interface AcceptLedgerResult extends XrplResult {

  static ImmutableAcceptLedgerResult.Builder builder() {
    return ImmutableAcceptLedgerResult.builder();
  }

  /**
   * The Ledger Index of the current open ledger these stats describe.
   */
  @JsonSerialize(using = LedgerIndexSerializer.class)
  @JsonProperty("ledger_current_index")
  UnsignedInteger ledgerCurrentIndex();

}

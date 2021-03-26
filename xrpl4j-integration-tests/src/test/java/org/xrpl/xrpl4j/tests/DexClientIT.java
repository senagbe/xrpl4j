package org.xrpl.xrpl4j.tests;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.primitives.UnsignedInteger;
import com.google.common.primitives.UnsignedLong;
import org.junit.jupiter.api.Test;
import org.xrpl.xrpl4j.client.JsonRpcClientErrorException;
import org.xrpl.xrpl4j.client.dex.DexClient;
import org.xrpl.xrpl4j.client.dex.model.Balance;
import org.xrpl.xrpl4j.client.dex.model.LimitOrder;
import org.xrpl.xrpl4j.client.dex.model.OrderBook;
import org.xrpl.xrpl4j.client.dex.model.Side;
import org.xrpl.xrpl4j.client.dex.model.Ticker;
import org.xrpl.xrpl4j.model.client.accounts.AccountInfoResult;
import org.xrpl.xrpl4j.model.client.fees.FeeResult;
import org.xrpl.xrpl4j.model.client.transactions.SubmitResult;
import org.xrpl.xrpl4j.model.flags.Flags;
import org.xrpl.xrpl4j.model.transactions.Address;
import org.xrpl.xrpl4j.model.transactions.IssuedCurrencyAmount;
import org.xrpl.xrpl4j.model.transactions.OfferCreate;
import org.xrpl.xrpl4j.model.transactions.XrpCurrencyAmount;
import org.xrpl.xrpl4j.wallet.Wallet;

import java.math.BigDecimal;
import java.util.List;

public class DexClientIT extends AbstractIT {

  private static final String USD = "USD";
  private static final String XRP = "XRP";
  private final DexClient dexClient = new DexClient(xrplClient);

  @Test
  public void testSingleCurrencyBalance() throws JsonRpcClientErrorException {
    Wallet issuer = this.createRandomAccount();
    Wallet purchaser = this.createRandomAccount();

    offerIssuedCurrency(issuer);

    askForIssuedCurrency(purchaser, issuer.classicAddress());

    List<Balance> balances = dexClient.getBalances(purchaser.classicAddress());

    BigDecimal usdBalance = new BigDecimal("0.5");
    BigDecimal xrpBalance = new BigDecimal("998.99999");
    BigDecimal reserve = new BigDecimal("25");

    assertThat(balances).isNotEmpty()
      .containsExactlyInAnyOrder(
        Balance.builder().currency(XRP)
          .available(xrpBalance.subtract(reserve))
          .total(xrpBalance)
          .locked(reserve)
          .build(),
        Balance.builder().currency(USD)
          .available(usdBalance)
          .total(usdBalance)
          .locked(BigDecimal.ZERO)
          .build()
      );
  }

  @Test
  public void testSingleCurrencyMultipleIssuerBalance() throws JsonRpcClientErrorException {
    Wallet issuerOne = this.createRandomAccount();
    Wallet issuerTwo = this.createRandomAccount();
    Wallet purchaser = this.createRandomAccount();

    String USD = "USD";
    offerIssuedCurrency(issuerOne);
    offerIssuedCurrency(issuerTwo);

    askForIssuedCurrency(purchaser, issuerOne.classicAddress());
    askForIssuedCurrency(purchaser, issuerTwo.classicAddress());

    List<Balance> balances = dexClient.getBalances(purchaser.classicAddress());
    BigDecimal usdBalance = new BigDecimal("1");
    BigDecimal xrpBalance = new BigDecimal("997.99998");
    BigDecimal reserve = new BigDecimal("30");

    assertThat(balances).isNotEmpty()
      .containsExactlyInAnyOrder(
        Balance.builder().currency(XRP)
          .available(xrpBalance.subtract(reserve).stripTrailingZeros())
          .total(xrpBalance)
          .locked(reserve)
          .build(),
        Balance.builder().currency(USD)
          .available(usdBalance)
          .total(usdBalance)
          .locked(BigDecimal.ZERO)
          .build()
      );
  }


  @Test
  public void testEmptyOrderBook() throws JsonRpcClientErrorException {
    Wallet issuer = this.createRandomAccount();

    Ticker ticker = Ticker.builder()
      .baseCurrency(XRP)
      .counterCurrency(USD)
      .build();

    OrderBook result = dexClient.getOrderBook(ticker, issuer.classicAddress());
    assertThat(result.asks()).isEmpty();
    assertThat(result.bids()).isEmpty();
  }

  @Test
  public void testOrderBookWithBidsAndAsks() throws JsonRpcClientErrorException {
    Wallet purchaser = this.createRandomAccount();
    Wallet issuer = this.createRandomAccount();

    Ticker ticker = Ticker.builder()
      .baseCurrency(XRP)
      .counterCurrency(USD)
      .build();

    BigDecimal tenDollars = new BigDecimal("10");
    BigDecimal twentyDollars = new BigDecimal("20");
    BigDecimal oneDollar = BigDecimal.ONE;

    BigDecimal oneXrp = BigDecimal.ONE;
    BigDecimal oneHundredXrp = new BigDecimal("100");
    BigDecimal fiftyXrp = new BigDecimal("50");
    BigDecimal twoXrp = new BigDecimal(2);


    offerIssuedCurrency(issuer, oneHundredXrp, oneDollar); // offer USD for xrp at 0.01
    offerIssuedCurrency(issuer, fiftyXrp, tenDollars); // offer USD for xrp at .20

    // ask 20 USD for 1 XRP (XRP exchange rate $20)
    askForIssuedCurrency(purchaser, issuer.classicAddress(), oneXrp, twentyDollars);
    // ask 10 USD for 2 XRP (XRP exchange rate $5)
    askForIssuedCurrency(purchaser, issuer.classicAddress(), twoXrp, tenDollars);

    OrderBook result = dexClient.getOrderBook(ticker, issuer.classicAddress());
    assertThat(result.asks()).isNotEmpty()
      .containsExactly(
        LimitOrder.builder()
          .ticker(ticker)
          .baseQuantity(twoXrp)
          .counterPrice(new BigDecimal(5))
          .side(Side.SELL)
          .build(),
        LimitOrder.builder()
          .ticker(ticker)
          .baseQuantity(oneXrp)
          .counterPrice(new BigDecimal(20).stripTrailingZeros())
          .side(Side.SELL)
          .build()
      );
    assertThat(result.bids()).isNotEmpty()
      .containsExactly(
        LimitOrder.builder()
          .ticker(ticker)
          .baseQuantity(fiftyXrp)
          .counterPrice(new BigDecimal("0.2"))
          .side(Side.BUY)
          .build(),
        LimitOrder.builder()
          .ticker(ticker)
          .baseQuantity(oneHundredXrp)
          .counterPrice(new BigDecimal("0.01"))
          .side(Side.BUY)
          .build()
      );
  }

  private void askForIssuedCurrency(Wallet purchaser, Address issuer) throws JsonRpcClientErrorException {
    askForIssuedCurrency(purchaser, issuer, BigDecimal.ONE, new BigDecimal("0.01"));
  }

  private void askForIssuedCurrency(Wallet purchaser, Address issuer, BigDecimal xrpQuantity, BigDecimal usdQuantity) throws JsonRpcClientErrorException {
    FeeResult feeResult = xrplClient.fee();
    AccountInfoResult accountInfoResult =
      this.scanForResult(() -> this.getCurrentAccountInfo(purchaser.classicAddress()));

    //////////////////////
    // Create an Offer
    UnsignedInteger sequence = accountInfoResult.accountData().sequence();
    OfferCreate offerCreate = OfferCreate.builder()
      .account(purchaser.classicAddress())
      .fee(feeResult.drops().minimumFee())
      .sequence(sequence)
      .signingPublicKey(purchaser.publicKey())
      .takerGets(XrpCurrencyAmount.ofXrp(xrpQuantity))
      .takerPays(
        IssuedCurrencyAmount.builder()
          .currency(USD)
          .issuer(issuer)
          .value(usdQuantity.toString())
          .build()
      )
      .flags(Flags.OfferCreateFlags.builder()
        .tfFullyCanonicalSig(true)
        .tfSell(true)
        .build())
      .build();

    // OFFER anyone who pays 0.01 USD can get 1 XRP  (at most 100 XRP per USD)

    SubmitResult<OfferCreate> response = xrplClient.submit(purchaser, offerCreate);
    logger.info("offer transaction {} response {}", response.transactionResult().transaction().hash(), response.result());
    assertThat(response.result()).isEqualTo("tesSUCCESS");

    awaitValidatedTransaction(response);
  }

  private void awaitValidatedTransaction(SubmitResult<?> response) {
    this.scanForResult(
      () -> getValidatedTransaction(
        response.transactionResult().transaction().hash().orElseThrow(() -> new IllegalStateException("no hash found")), OfferCreate.class)
    );
  }

  private void offerIssuedCurrency(Wallet issuerWallet) throws JsonRpcClientErrorException {
    offerIssuedCurrency(issuerWallet, new BigDecimal("200"), new BigDecimal("100"));
  }

  private void offerIssuedCurrency(Wallet issuerWallet, BigDecimal xrpQuantity, BigDecimal usdQuantity)
    throws JsonRpcClientErrorException {
    AccountInfoResult accountInfoResult =
      this.scanForResult(() -> this.getCurrentAccountInfo(issuerWallet.classicAddress()));

    //////////////////////
    // Create an Offer
    UnsignedInteger sequence = accountInfoResult.accountData().sequence();
    OfferCreate offerCreate = OfferCreate.builder()
      .account(issuerWallet.classicAddress())
      .fee(XrpCurrencyAmount.of(UnsignedLong.valueOf(10)))
      .sequence(sequence)
      .signingPublicKey(issuerWallet.publicKey())
      .takerGets(IssuedCurrencyAmount.builder()
        .currency(USD)
        .issuer(issuerWallet.classicAddress())
        .value(usdQuantity.toString())
        .build()
      )
      .takerPays(XrpCurrencyAmount.ofXrp(xrpQuantity))
      .flags(Flags.OfferCreateFlags.builder()
        .tfFullyCanonicalSig(true)
        .tfSell(true)
        .build())
      .build();


    // OFFER anyone who pays 200 XRP can get 100 USD (at least 2 XRP PER USD)

    SubmitResult<OfferCreate> response = xrplClient.submit(issuerWallet, offerCreate);
    logger.info("sell offer transaction {} response {}", response.transactionResult().transaction().hash(), response.result());
    assertThat(response.result()).isEqualTo("tesSUCCESS");

    awaitValidatedTransaction(response);
  }


}

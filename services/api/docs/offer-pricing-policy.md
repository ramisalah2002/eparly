# Offer pricing policy

## Canonical tariff

`amountMinor` is the standard recurring price in the smallest currency unit, for
one `billingPeriod`. It is also the price charged after a temporary promotion
ends. The currency and period belong to that amount; EUR is required for savings
estimates. A null amount, currency, or usable billing period is unknown, never
zero.

`promotionPriceType` makes the temporary price unambiguous:

- `PERCENTAGE`: `promotionPercent` is required and
  `priceAfterPromotionMinor` must be null. The temporary amount is
  `amountMinor * (100 - promotionPercent) / 100`, rounded once to the nearest
  cent with `HALF_UP`.
- `FIXED_PRICE`: `priceAfterPromotionMinor` is the temporary recurring amount
  in minor units, and `promotionPercent` must be null. The value cannot exceed
  the standard amount.

These representations are mutually exclusive. `promotionDurationMonths` is
the number of months after a successful subscription for which the temporary
rate applies. It is not the campaign's subscription window. That window is
represented by the inclusive `promotionValidFrom` and `promotionValidUntil`
dates; null endpoints mean no known endpoint, not an indefinitely applicable
discount. A missing or invalid duration prevents applying the promotion.

Existing records have no `promotionPriceType`. They are deliberately treated
as ambiguous: their promotion is not used until it is explicitly revalidated
and typed. Their standard `amountMinor` remains unchanged and is not
reinterpreted. Non-empty pricing discount conditions or offer-level eligibility
conditions are not automatically considered satisfied; without a structured
eligibility decision the promotion is excluded from calculations and marked
unverified in the pricing response.

## Verification and availability

The existing offer availability policy remains in force: offer status, provider,
territory, dates, reliable standard price, and fresh official evidence are
required. General offer evidence retains the configured 30-day freshness.

A promotion additionally requires its own `OfferEvidence` with
`evidenceScope=PROMOTION`, verified status, valid official HTTPS URL and
consistent consultation/verification dates. Promotion evidence is fresh for
seven days by default (`eparly.offer.catalog.promotion-freshness-threshold`).
The exact freshness boundary is accepted; a later verification timestamp, an
expired proof, or a stale newest promotion proof is rejected. Legacy evidence
with a null scope cannot validate a promotion. Future or expired subscription
windows do not qualify. The comparison and catalogue responses expose
`promotionVerified`; promotion fields in comparison are only populated when
the promotion is currently applicable and verified.

Base offer availability uses `OFFER` evidence (or null-scoped legacy evidence,
which retains its former whole-offer meaning). `PRICING` evidence alone does not
validate the offer's characteristics and `PROMOTION` evidence cannot replace
the general offer proof. New evidence should always declare its scope.

## Fees and unknowns

Amounts are minor currency units. A fee value of zero means explicitly known
to be zero; null means unknown. Activation fees, cancellation fees, and other
switching fees are counted once as one-off costs. The free-text catalogue
`otherFees` is not parsed as money; a missing user-confirmed amount therefore
remains missing. Savings are not classified as possible unless current cost,
alternative cost, and each required switching-fee amount are known. No
unknown charge is silently substituted with zero.

## Billing periods and rounding

Costs are prorated monthly equivalents, not a reconstruction of invoice dates
or actual payment events. The estimate uses 52 weekly cycles per year,
one-third of a quarterly cycle per month, and one-twelfth of a yearly cycle
per month. Monthly uses one cycle per month. Promotion and standard segments
are accumulated at high decimal precision and the total is rounded once to
whole minor units using `HALF_UP`; effective monthly values are rounded by the
same rule. This avoids separate-segment cent rounding. Annual and 24-month
totals use the same period ratios.

For example, a monthly standard tariff of 1,999 minor units with a fixed
temporary tariff of 999 minor units for three months costs
`3 * 999 + 9 * 1,999 = 20,988` minor units over 12 months, before fees.
An activation fee of 3,000 minor units is added once, not multiplied by the
horizon.

## Savings and API compatibility

Net potential savings are `current estimated cost - alternative estimated cost
- known switching fees`. They are estimates, not observed bank savings. An
unverified promotion is not applied; when the verified standard price is
known, the estimate can use that standard price without assuming a discount.
An unknown standard price or required fee yields insufficient data. Currency
conversion is not performed. When a verified promotion is applied, savings
traceability points to the promotion-specific proof; otherwise it points to the
general offer proof.

Existing pricing fields remain in the API. Additive fields expose the
promotion price type and subscription window; `promotionVerified` indicates
whether the promotion may currently be used. In `SavingsEstimateResponse`,
`promotionPriceMinor` is the effective temporary amount when verified, and
`priceAfterPromotionMinor` is the canonical standard amount. Clients must not
interpret a null promotion amount as zero.

## History and concurrency

V18 adds typed promotion metadata, a scoped evidence marker, a version for
optimistic concurrency on pricing rows, and JSONB old/new tariff snapshots to
price history. Existing price-history rows and savings-estimate snapshots are
preserved. The current price revision path records the complete tariff before
and after a standard price change. Historical savings snapshots are never
recalculated against changed catalogue values.

## Remaining limits

Promotion eligibility is conservative and text conditions are not parsed or
automatically resolved for a user. Free-text fees are not itemized. Cost
proration does not model exact billing dates, partial cycles, tax changes, or
payment timing. The current catalogue revision operation changes the standard
amount; a complete controlled tariff-maintenance workflow is outside this
change.

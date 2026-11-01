# Feast Reserve Budget System & Deterministic Gatekeeper Architecture

Implement the Feast Budget formulation, deterministic gatekeeper validation functions (`check`, `propose_tradeoff`, `apply_update`, `undo`), a 4-tier Firestore data architecture, manual meal/expense logging with crowdsourced staging, and a rich interactive Compose UI showing live budget buckets and trade-off proposals.

---

### User Review & Critical Decisions

- **Integer Paisa Currency (Confirmed)**: All monetary values in the engine and Firestore are strictly stored and computed in integer paisa ($1\text{ Tk} = 100\text{ paisa}$) to eliminate floating-point drift. Display values are formatted as $\text{৳}(paisa / 100.0)$.
- **Feast Allocation & Cancellation (Confirmed)**: Monthly pool $P = \text{Monthly Budget} - \text{Fixed Costs}$. Feasts are set at $f = 2 \times b_0$. When a meal shortfall $s = X - A$ exceeds unallocated funds $U$, the nearest feast is consumed; remainder $r = F - s$ is credited to $U$ and the feast is cancelled atomically upon user confirmation.
- **Strict Gatekeeper Invariant (Confirmed)**: The AI (Gemini via Firebase AI Logic) acts strictly as a conversational planner that invokes deterministic gatekeeper tools (`check_feasibility`, `request_meal_rearrangement`). It never mutates database documents directly.
- **Multi-Table Firestore Architecture**:
  1. `central_foods`: Pre-populated verified dishes with kcal, standard pricing, partner restuarant flags.
  2. `partner_restaurants`: Curated list of partner outlets with discount percentages and location details.
  3. `users/{userId}`: Profile, pool $P$, fixed costs, unallocated balance $U$, and active feasts list.
  4. `users/{userId}/meals`: User's personalized meal slots.
  5. `users/{userId}/payments`: Transaction ledger with `trxId`, merchant, method (bKash/Nagad), timestamps.
  6. `community_food_submissions`: Developer staging table for crowdsourced user-entered custom meals.

---

## 1. Overview & Core Concept

NutriMate AI helps budget-conscious students balance daily nutrition with celebratory splurges ("Feasts"). Rather than treating daily budgets as rigid silos, upcoming feasts act as flexible financial buffers. If a student craves a premium meal (e.g. Kacchi Biriyani for ৳120 on an ৳80.88 lunch allowance), the gatekeeper calculates the exact trade-off: use unallocated reserves first, or propose cancelling an upcoming feast (e.g. Friday feast). The AI presents the plain-language trade-off, requires explicit student confirmation, executes atomic ledger updates, and allows one-tap undo.

```
┌────────────────────────────────────────────────────────────────────────┐
│                        MONTHLY FOOD POOL (P)                           │
│                     (e.g., 700,000 paisa = ৳7,000)                     │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
          ┌─────────────────────────┼────────────────────────┐
          ▼                         ▼                        ▼
┌──────────────────┐      ┌──────────────────┐     ┌──────────────────┐
│   DAILY MEALS    │      │  PLANNED FEASTS  │     │   UNALLOCATED    │
│  sum(allowances) │  +   │   sum(budgets)   │  +  │   BALANCE (U)    │ <= P
│ breakfast (20%)  │      │  Feast 1 (৳466)  │     │  Buffer money /  │
│ lunch (40%)      │      │  Feast 2 (৳466)  │     │  Feast remainder │
│ snack (15%)      │      │                  │     │                  │
│ dinner (25%)     │      │                  │     │                  │
└──────────────────┘      └──────────────────┘     └──────────────────┘
```

---

## 2. User Experience & Visual Design

### Key User Flows

1. **Bucket Dashboard (Feast & Budget Overview)**:
   - Header displays Monthly Pool $P$, Base Daily Budget $b_0$, Active Daily Budget $b$, and Unallocated Balance $U$.
   - Interactive Feast Cards showing upcoming feast dates (e.g., "Friday Feast #1: ৳466.66 [Active]").
   - Real-time ledger health badge: `sum(allowances) + sum(feasts) <= P` (Verified).

2. **Meal Upgrade & Trade-off Proposal**:
   - Tapping an upgrade or chatting with AI ("I want Kacchi Biriyani for lunch") triggers the gatekeeper.
   - If price $X \le A$: Instant green badge, meal updated.
   - If price $X > A$: The Gatekeeper calculates shortfall $s = X - A$. An interactive Trade-Off Card appears:
     - Shortfall breakdown: $\text{Shortfall } s = \text{৳}39.12$.
     - Source: "Cancelling Friday Feast (৳466.66). Remainder ৳427.54 moves to Unallocated Balance $U$."
     - Action buttons: **Accept & Cancel Feast** vs **Decline (Show Alternatives)**.
   - On accept: Feast is marked cancelled, allowance updated to $X$, meal saved, and an **Undo** banner appears.

3. **Manual Expense & Custom Meal Submission**:
   - Quick "Log Expense / Add Meal" FAB and dialog.
   - Student enters: Restaurant name, Dish name, Spent amount, Calories, and meal category.
   - Immediate effect: User's daily meal and ledger are updated locally and synced to `/users/{userId}/meals`.
   - Staging effect: The submission is simultaneously saved to `community_food_submissions` for developer review and inclusion into the central food catalog.

4. **Visual Theme**:
   - Maintains NutriMate's deep midnight glassmorphic styling (`#0B1020`, frosted cards, neon mint `#7CFFB2` for budget health, amber `#FFD25A` for feast status, and alert red for cancellations).

---

## 3. Key Product Decisions & Trade-Offs

- **Deterministic Kotlin Gatekeeper Layer**:
  - *Chosen Approach*: Implement `FeastBudgetGatekeeper` in Kotlin. The AI engine passes tool calls (`check(meal, item, price)`) to the gatekeeper, which performs exact integer math and Firestore atomic writes.
  - *Why*: Large Language Models can hallucinate mathematical balances and currency arithmetic. Offloading all validation, ledger inequality checks, and transactional updates to deterministic Kotlin code guarantees 100% ledger safety and reduces AI token consumption by over 70%.
- **All-or-Nothing Feast Cancellation Default**:
  - *Chosen Approach*: Consuming a feast cancels it completely and dumps the full remainder $r = F - s$ into unallocated balance $U$.
  - *Why*: Reduces cognitive load on students compared to micro-fractional feasts (e.g. having a ৳34.10 "feast" remaining).
- **Dual-Write on Custom Meal Logging**:
  - *Chosen Approach*: Write user's custom meal to their own private subcollection immediately, while writing a sanitized copy to `community_food_submissions`.
  - *Why*: Zero latency for the user, while building an authentic crowd-sourced catalog of university mess and street stall menus.

---

## 4. Technical Architecture & Data Strategy

### System Architecture Diagram

```
┌────────────────────────────────────────────────────────────────────────┐
│                        Jetpack Compose UI                              │
│  ┌───────────────────────┐ ┌────────────────┐ ┌─────────────────────┐  │
│  │ Feast Bucket Overview │ │ Trade-off Card │ │ Manual Log Dialog   │  │
│  └───────────▲───────────┘ └───────▲────────┘ └──────────▲──────────┘  │
└──────────────┼─────────────────────┼─────────────────────┼─────────────┘
               │                     │                     │
┌──────────────┴─────────────────────┴─────────────────────┴─────────────┐
│                          NutriMateViewModel                            │
│           (StateFlow<FeastUiState>, Undo History Stack)                │
└──────────────────────────────▲─────────────────────────────────────────┘
                               │
┌──────────────────────────────┴─────────────────────────────────────────┐
│              Deterministic FeastBudgetGatekeeper                       │
│  - Integer paisa arithmetic (P, b0, b, f, U)                           │
│  - checkFeasibility(mealId, newPricePaisa, newKcal)                   │
│  - evaluateTradeoff(mealId, shortfallPaisa)                           │
│  - applyRearrangement(tradeoffPlan) -> Atomic Write                    │
│  - rollbackLastAction() -> Undo Support                                │
└──────────────────────────────▲─────────────────────────────────────────┘
                               │
┌──────────────────────────────┴─────────────────────────────────────────┐
│               Firebase Firestore (Enterprise Applet DB)                │
│  ├── central_foods/           (read-only catalog for suggestions)      │
│  ├── partner_restaurants/     (partner discounts & metadata)           │
│  ├── community_submissions/   (crowdsourced dev staging table)         │
│  └── users/{userId}/                                                   │
│      ├── (profile & feast buckets)                                     │
│      ├── meals/               (user's active meal slots)               │
│      └── payments/            (transaction history)                    │
└────────────────────────────────────────────────────────────────────────┘
```

### Firestore Schema Details

1. `central_foods/{foodId}`:
   `{ id, name, restaurant, pricePaisa, calories, isPartner, discountTag, mealSlot }`
2. `partner_restaurants/{partnerId}`:
   `{ id, name, discountRate, cuisine, campusArea }`
3. `community_food_submissions/{submissionId}`:
   `{ id, submittedBy, restaurant, dishName, pricePaisa, calories, mealCategory, status: 'pending', createdAt }`
4. `users/{userId}`:
   `{ userId, monthlyBudgetPaisa, fixedCostsPaisa, poolPaisa, unallocatedPaisa, baseDailyBudgetPaisa, dailyBudgetPaisa, feasts: [ { id, date, budgetPaisa, isCancelled, title } ] }`
5. `users/{userId}/meals/{mealId}`:
   `{ id, name, emoji, allowancePaisa, targetKcal, selectedPlace, selectedTitle, selectedPricePaisa }`
6. `users/{userId}/payments/{paymentId}`:
   `{ id, trxId, vendorName, billAmountPaisa, discountPaisa, netPaidPaisa, paymentMethod, createdAt }`

---

## 5. Implementation Phases & Verification Plan

### Phase 1: Core Mathematical Engine & Models
- Implement `FeastEngine.kt` with pure functions for pool calculations, feast sizing, shortfall funding resolution, and ledger verification.
- Unit test worked example with exact integer paisa arithmetic ($P = 700,000$, $b_0 = 23,333$, $f = 46,666$, $b = 20,222$, shortfall $s = 3,912$).

### Phase 2: Firestore Gatekeeper & Repository
- Update `firestore.rules` and `firebase-blueprint.json` to secure `central_foods`, `partner_restaurants`, and `community_food_submissions`.
- Implement `FeastBudgetGatekeeper.kt` with atomic state updates and undo snapshot stack.

### Phase 3: Interactive UI & Feast Demo Screen
- Build `FeastDashboardCard` displaying budget buckets, active/cancelled feasts, and real-time ledger rule status.
- Build `TradeoffProposalDialog` showing before/after states, shortfall funding source, and Confirm/Decline options.
- Build `ManualMealEntryDialog` supporting custom restaurant/meal logging with dual-write to user meals and community submissions.
- Wire into `MainActivity` with navigation tabs and full undo support.

### Phase 4: Verification & Build
- Verify build with `compile_applet`.
- Verify zero-regression against auth and security rules gates.

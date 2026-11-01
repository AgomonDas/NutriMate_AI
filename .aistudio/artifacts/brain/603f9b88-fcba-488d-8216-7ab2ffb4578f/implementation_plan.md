# NutriMate Chat AI: Gemini Function Calling & Feast Reserve Engine

Implement an AI meal planner chat assistant powered by Gemini (`gemini-3.8-flash`) with function calling. The AI assists budget-constrained students in Bangladesh to adjust their meal plans, evaluate upgrades through a deterministic gatekeeper, navigate feast trade-offs, and answer nutrition questions—without ever editing the database directly or performing untrusted financial arithmetic.

---

### User Review & Critical Decisions

> [!IMPORTANT]
> The following decisions were confirmed based on your preferences:

- **Gemini Model & Function Calling**: Powered by `gemini-3.8-flash` via Firebase AI Logic, registered with 5 client-side deterministic tools (`search_menu`, `check`, `find_funding`, `update`, `get_alternatives`).
- **Tool-Call Trace Display**: Monospace tool execution badges in the chat stream showing real-time step-by-step tool invocation (e.g., `search_menu("biriyani") → 1 match`, `check("lunch", "kacchi_biriyani") → over_budget by Tk 39.12`, `find_funding(...) → cancel Friday feast`).
- **Confirmation UX**: When an `update` tool invocation returns `needs_confirmation`, the chat UI renders an inline actionable card with **Confirm** and **Decline** buttons alongside the assistant's plain-English explanation.
- **Backend Isolation**: AI never modifies tables directly; state is mutated exclusively through signed tokens passed to the `update()` gatekeeper.

---

### 1. Overview & Core Concept

NutriMate AI assists students managing tight monthly food budgets (default Tk 7,000 / 700,000 paisa) across 4 daily meal slots (Breakfast 20%, Lunch 40%, Snacks 15%, Dinner 25%) and 2 planned weekend feasts (Tk 466.66 each).

The chat copilot categorizes incoming messages into two distinct pathways:
1. **Plan-Changing Requests** (*"I want biriyani for lunch"*, *"Find me a cheaper dinner"*):
   - The model must use tools and cannot invent prices or edit data directly.
   - It performs `search_menu`, `check`, `find_funding`, and `update`.
   - If an item exceeds today's slot allowance, it finds funding (unallocated buffer $U$ first, or sacrifice of nearest planned feast).
   - If a feast is affected, it requests user consent before applying.
2. **Food & Nutrition Questions** (*"Is a protein shake worth it for gym?"*, *"How much protein in 2 eggs?"*):
   - The model answers directly in conversational text with zero tool calls and zero state mutations.

---

### 2. User Experience & Visual Design

#### Key User Flows

```
[ User sends message: "I want kacchi biriyani for lunch" ]
                         │
                         ▼
┌────────────────────────────────────────────────────────┐
│ 1. AI executes search_menu("biriyani", "lunch")        │
│    └─ [Badge]: 🔍 search_menu("biriyani") → 1 item     │
├────────────────────────────────────────────────────────┤
│ 2. AI executes check("lunch", "kacchi_biriyani")       │
│    └─ [Badge]: ⚖️ check(lunch, item=5) → Over by ৳39.12│
├────────────────────────────────────────────────────────┤
│ 3. AI executes find_funding("lunch", "kacchi_biriyani")│
│    └─ [Badge]: 💡 find_funding → Cancel Friday Feast   │
├────────────────────────────────────────────────────────┤
│ 4. AI executes update(token="token_feast_cancel_...")  │
│    └─ [Badge]: 🔒 update(...) → needs_confirmation     │
└────────────────────────────────────────────────────────┘
                         │
                         ▼
┌────────────────────────────────────────────────────────┐
│ Assistant Message:                                     │
│ "Kacchi biriyani is ৳120 and your lunch allowance is   │
│ ৳80.88. I can cover the gap by cancelling Friday's     │
│ feast. You'd lose that feast. Tap Confirm to apply."  │
│                                                        │
│ [ ✅ Confirm Trade-off ]   [ ❌ Decline / Alternatives] │
└────────────────────────────────────────────────────────┘
```

#### Visual Identity & Chat Elements
- **Monospace Tool Badges**: Compact dark pills (`#090E1A` background, emerald border `#34D98A`, typography `JetBrains Mono / Monospace` 11sp) displaying each function call name, input arguments, and result preview.
- **Interactive In-Chat Action Cards**: Embedded directly inside assistant bubbles when `needs_confirmation` is returned, allowing immediate 1-tap confirmation or cancellation.
- **Quick Suggestion Chips**: Horizontal scroll list above the input field:
  - 🍛 *I want biriyani for lunch* (Triggers feast trade-off flow)
  - 🥪 *Swap snack to fruit cart* (Triggers instant in-budget swap)
  - 💸 *Something cheaper for dinner* (Triggers `get_alternatives`)
  - 💪 *Is protein shake worth it?* (Triggers pure nutritional advice)

---

### 3. Key Product Decisions & Trade-Offs

#### Decision 1: Token-Efficient Compact System Context
- **Chosen Approach**: Inject only a 120-token dynamic context snippet into the Gemini prompt:
  ```
  Today: Sunday, Nov 1.
  Budget Pool: Tk 7,000. Unallocated: Tk 0.
  Allowances: Breakfast Tk 40.44, Lunch Tk 80.88, Snacks Tk 30.33, Dinner Tk 50.55.
  Next Feast: Friday Feast #1 (Tk 466.66, active).
  User: Student, non-vegetarian.
  ```
- **Why**: Eliminates 3,600+ tokens of full-month calendar dump per turn, speeding up responses by 5x and staying well within latency budgets.

#### Decision 2: Signed Ephemeral Tokens for Write Protection
- **Chosen Approach**: `update()` requires a cryptographic/UUID token generated by `check()` or `find_funding()`.
- **Why**: Prevents hallucinated price updates or unsolicited mutations; the model cannot make changes without the backend validating the dry-run state first.

---

### 4. Technical Architecture & Data Strategy

```
┌─────────────────────────────────────────────────────────────────┐
│                    Jetpack Compose Chat UI                      │
│             (ChatSheet.kt with Tool Trace Badges)               │
└──────────────┬───────────────────────────────────▲──────────────┘
               │ (User message)                    │ (Stream / Badges)
               ▼                                   │
┌─────────────────────────────────────────────────────────────────┐
│                    NutriMateChatViewModel                       │
│    - Maintains message history & active proposal tokens         │
│    - Orchestrates multi-turn tool execution loop (max 6 steps)  │
└──────────────┬───────────────────────────────────▲──────────────┘
               │ Function Calls                    │ Tool Results
               ▼                                   │
┌─────────────────────────────────────────────────────────────────┐
│                  NutriMateAiToolsGatekeeper                     │
│  ├─ search_menu(query, slot, max_price_tk)                      │
│  ├─ check(slot, item_id)                                        │
│  ├─ find_funding(slot, item_id)                                 │
│  ├─ update(token)                                               │
│  └─ get_alternatives(slot, max_price_tk)                        │
└──────────────┬───────────────────────────────────▲──────────────┘
               │ Read / Validate                   │ Update State
               ▼                                   │
┌─────────────────────────────────────────────────────────────────┐
│            FeastEngine & NutriMateFirestoreRepository           │
│    - Integer paisa arithmetic (৳1 = 100 paisa)                  │
│    - Strict Ledger Invariant: sum(allowances) + feasts <= P     │
│    - Dual-write to user meal slots & community staging table    │
└─────────────────────────────────────────────────────────────────┘
```

#### Tool Definitions
1. `search_menu(query: String, slot: String, max_price_tk: Int?)`
   - Returns up to 5 items: `{item_id, name, place, price_tk, kcal}`.
2. `check(slot: String, item_id: String)`
   - Read-only dry run. Returns `{ok: true, price_tk, slot_budget_tk, kcal, check_token}` or `{ok: false, reason: "over_budget", short_by_tk}`.
3. `find_funding(slot: String, item_id: String)`
   - Evaluates buffer $U$ then upcoming planned feast $F$. Returns list of options with plain-English summary and signed funding token.
4. `update(token: String)`
   - Only write tool. Validates token; returns `{status: "applied" | "needs_confirmation" | "rejected", explanation: String}`.
5. `get_alternatives(slot: String, max_price_tk: Int?)`
   - Returns 2–3 cheaper options that fit strictly within `slot_budget_tk`.

---

### Implementation Phases

- **Phase 1: Tool Gatekeeper & In-Memory Catalog Engine**
  - Implement `NutriMateAiToolsGatekeeper.kt` containing the 5 specified tools, token generator, and deterministic state transitions.
- **Phase 2: Gemini Tool Calling Integration (Firebase AI Logic)**
  - Configure `gemini-3.8-flash` with Function Declarations for all 5 tools.
  - Implement the multi-step execution loop in `NutriMateChatViewModel` handling function call dispatching and tool responses.
- **Phase 3: Chat UI with Trace Badges & Trade-off Confirmation**
  - Update `ChatSheet.kt` to render monospace badges, tool arguments, return values, and interactive trade-off confirmation dialogs.
- **Phase 4: Verification & Automated Tests**
  - Write Robolectric / unit tests verifying both standard in-budget swaps and over-budget feast cancellations with tool trace history.

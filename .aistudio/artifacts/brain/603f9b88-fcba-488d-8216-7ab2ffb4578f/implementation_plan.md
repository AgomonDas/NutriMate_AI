# NutriMate 2-Tier Hybrid AI & Multi-Turn Chat Architecture (MVP Rewrite)

Transform the NutriMate AI chat system into a production-grade, MVP-quality 2-tier hybrid assistant. The system pairs an ultra-fast (<20ms) on-device intent classifier and deterministic gatekeeper with a cloud-based, multi-turn Gemini 3.8 Flash copilot grounded in the user's active campus meals and budget ledger.

---

### User Review & Critical Decisions

> [!IMPORTANT]
> The following architectural and UX choices have been confirmed for the MVP rewrite:

- **Confirmed Decision 1 (Model & Routing)**: Hybrid on-device router paired with **Gemini 3.8 Flash** (`models/gemini-3.8-flash`) maintaining multi-turn conversation memory.
- **Confirmed Decision 2 (Tool Trace Presentation)**: Collapsible monospace step cards featuring real-time latency badges (e.g., `⚡ 8ms · on-device`, `⚡ 420ms · Gemini 3.8 Flash`) and status chips.
- **Confirmed Decision 3 (AI Persona)**: Friendly Bangladeshi campus budget copilot using authentic Dhaka student context (Taka ৳, BUET/Palashi/DU hubs, mess plans, and local food items).

---

### 1. Overview & Core Concept

- **What It Does**: Enables students to converse naturally with NutriMate about meal swaps, budget checks, and nutritional advice. Instant actions (budget checks, menu searches, feast trade-offs) resolve in milliseconds locally, while nuanced dietary advice and feast explanations stream smoothly from Gemini 3.8 Flash without multi-turn latency bottlenecks.
- **Target Audience**: University students in Bangladesh managing daily food budgets, wanting fast meal changes without waiting 15–20 seconds for multiple cloud round-trips.
- **Key Value**: Deterministic budget enforcement (no hallucinated prices or rogue ledger edits) combined with empathetic, conversational AI and transparent, inspectable tool execution.

---

### 2. User Experience & Visual Design

#### Key User Flows

1. **Plan Swap Flow (e.g. "I want Kacchi Biriyani for lunch")**:
   - User types or taps quick prompt chip.
   - Tier 1 router classifies intent in ~10ms, executes `search_menu` and `check` against live Firestore/campus catalog.
   - Collapsible monospace cards appear in chat showing each step and execution time.
   - If over budget, `find_funding` calculates the exact feast sacrifice required.
   - Tier 2 Gemini 3.8 Flash crafts a polite, contextual trade-off message with inline Confirm/Decline buttons.
   - Tapping Confirm mutates the budget ledger, syncs Firestore, and cancels the feast.

2. **Conversational Multi-Turn Advice Flow (e.g. "Is a gym protein shake worth it on my budget?")**:
   - Router identifies nutritional inquiry.
   - Gemini 3.8 Flash generates a thoughtful response grounded in student economics in Bangladesh (৳).
   - Follow-up turns (e.g., "What can I eat instead to get 30g protein?") remember the previous turn's context seamlessly.

#### Visual Styling & Layout

```
┌────────────────────────────────────────────────────────┐
│  NutriMate AI Copilot              [⚡ Hybrid Active] ✕ │
│  Dhaka Campus · Live Budget Ledger                     │
├────────────────────────────────────────────────────────┤
│                                                        │
│  [User Bubble]                                         │
│  "Can I swap my dinner to beef tehari?"                │
│                                                        │
│  ┌──────────────────────────────────────────────────┐  │
│  │ ⚡ 11ms · on-device              [▼ Monospace]    │  │
│  │ > search_menu(query="tehari", slot="dinner")     │  │
│  │   ↳ Found: Old Dhaka Beef Tehari (৳51, 780 kcal) │  │
│  │ > check(slot="dinner", item_id="rb_1")           │  │
│  │   ↳ Status: OK (fits ৳65 slot allowance)         │  │
│  │ > update(token="chk_8f1a...")                    │  │
│  │   ↳ Status: applied                              │  │
│  └──────────────────────────────────────────────────┘  │
│                                                        │
│  [Assistant Bubble - Gemini 3.8 Flash]                 │
│  "Updated your dinner to Old Dhaka Beef Tehari from     │
│   Royal Biriyani (৳51, 780 kcal). You saved ৳14 under  │
│   your allowance!"                                     │
│                                                        │
├────────────────────────────────────────────────────────┤
│  [🍛 Biriyani] [🥗 Cheaper lunch] [💪 Protein options] │
│  [Type a meal change or question...        ] [Send ➢]  │
└────────────────────────────────────────────────────────┘
```

- **Collapsible Step Cards**: Styled as modern dark terminal boxes (`#08101E`) with neon mint border (`#6AF0A6`), monospace text, toggleable details, and execution badges.
- **Conversation Bubbles**: M3 dark gradient glass for user and assistant, with clear contrast, markdown formatting, and accessible touch targets.

---

### 3. Key Product Decisions & Trade-Offs

- **Decision 1: Two-Tier Hybrid over Pure Cloud Function Calling**:
  - *Chosen Approach*: On-device deterministic routing for Step 1 (search, budget validation, token signing), Cloud Gemini 3.8 Flash for multi-turn synthesis and nutritional advice.
  - *Why*: Pure cloud function calling requires 3 to 4 sequential round-trips over mobile networks (often taking 10–25 seconds in Bangladesh). The hybrid approach brings latency down to < 1.2s while guaranteeing mathematical budget consistency.
- **Decision 2: Persistent Multi-Turn Conversation Memory**:
  - *Chosen Approach*: Maintain a Gemini `Chat` session across user turns in the ViewModel, preserving message history.
  - *Why*: Allows students to ask natural follow-ups ("What about dinner?", "Is that enough protein?") without repeating context.
- **Decision 3: Zero Hallucination Budget Gatekeeper**:
  - *Chosen Approach*: The LLM is never allowed to directly calculate balances or write to the database. All plan changes must bear a cryptographically signed HMAC token issued by `FeastBudgetGatekeeper`.

---

### 4. Technical Architecture & Data Strategy

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                            NutriMate UI (ChatSheet)                          │
└───────────────────────┬──────────────────────────────▲───────────────────────┘
                        │ User Prompt                  │ StateFlow<List<ChatMessage>>
┌───────────────────────▼──────────────────────────────┴───────────────────────┐
│                              NutriMateViewModel                              │
└───────────────────────┬──────────────────────────────▲───────────────────────┘
                        │ processUserMessage()         │ Emits steps & replies
┌───────────────────────▼──────────────────────────────┴───────────────────────┐
│                             NutriMateChatEngine                              │
│                                                                              │
│   ┌───────────────────────────────┐     ┌────────────────────────────────┐   │
│   │   TIER 1: On-Device Router    │     │   TIER 2: Cloud Gemini 3.8     │   │
│   │   - Regex & Slot Extractor    │     │   - Firebase AI Logic          │   │
│   │   - Latency: <15ms            │     │   - Multi-Turn Chat Session    │   │
│   │   - search_menu / check       │     │   - Empathetic Student Coach   │   │
│   └───────────────┬───────────────┘     └────────────────▲───────────────┘   │
│                   │ Tokens                               │ Context & Tradeoffs
┌───────────────────▼──────────────────────────────────────┴───────────────────┐
│                      NutriMateAiToolsGatekeeper                              │
│   - Enforces Allowance, Feast Reserves & Signs Audit Tokens                  │
└───────────────────┬──────────────────────────────────────────────────────────┘
                    │ Reads Catalog & Syncs Slots
┌───────────────────▼──────────────────────────────────────────────────────────┐
│                   NutriMateFirestoreRepository & Firestore                   │
│   - /restaurants (coordinates, menus)                                        │
│   - /central_foods & /users/{uid}/meals                                      │
└──────────────────────────────────────────────────────────────────────────────┘
```

#### Proposed Component Enhancements

1. **`NutriMateChatEngine.kt`**:
   - Refactor to maintain a persistent `Chat` session with `gemini-3.8-flash` via Firebase AI Logic.
   - Track timing markers (`System.currentTimeMillis()`) across on-device operations and cloud inference to stamp latency on all tool execution messages.
   - Robust error handling with `withTimeout(30_000L)`, catching `CancellationException` and surfacing friendly fallback messages.

2. **`NutriMateOnDeviceRouter.kt`**:
   - Enhance intent classification and slot recognition for authentic Bengali campus food queries (e.g. "kacchi", "khichuri", "tehari", "paratha", "shingara", "dim", "cha").
   - Extract budget constraints (e.g. "under 60 tk", "below 100", "cheaper").

3. **`ChatSheet.kt`**:
   - Implement `CollapsibleToolStepCard` composable for `MessageSender.FUNCTION_CALL`:
     - Displays tool name, latency tag (`⚡ 14ms`), and execution status.
     - Click-to-expand details showing arguments and return payload.
   - Add animated typing/processing indicator while Gemini is synthesizing.

4. **`CampusLocationTest.kt` & Test Suite**:
   - Unit tests validating on-device classification accuracy, latency stamping, and multi-turn message state transitions.

# NutriMate AI 🥗✨

A modern, native Android application built with Kotlin and Jetpack Compose. NutriMate AI is designed for students and young adults to eat well, hit nutritional targets, and stick to a realistic monthly food budget with real-time AI assistance and student partner discounts.

## Key Features

- **Personalized Onboarding & Calorie Calculator**:
  - Interactive phone sign-up and profile sizing.
  - Automatic daily calorie requirement calculation using the Mifflin–St Jeor formula adapted for age, height, weight, and activity level.
  - Student status, campus/institute tracking, and mess/hall plan integration.

- **Today's Plan & Hero Budget Ring**:
  - Real-time elapsed day progress circular indicator.
  - Daily budget allocation (৳200/day) and calorie targets (2,310 kcal).
  - Expandable meal slots (Breakfast 🍳, Lunch 🍛, Snacks 🥪, Dinner 🍲) with restaurant options, partner discounts, price breakdown, and single-tap swaps.

- **Ask AI Assistant**:
  - Conversational nutrition and budget companion.
  - Executes real-time meal swaps, feasibility checks (`check(...)`), and budget plan updates (`update(...)`).
  - Evaluates dietary options (e.g., protein cost comparisons: eggs & yellow lentils vs. commercial protein shakes).
  - Quick action chips for fast meal decisions.

- **QR Pay & Partner Checkout**:
  - Bangla QR, bKash, and Nagad simulator with animated camera laser viewfinder.
  - Automatic student partner discount calculation (−10% at Star Kabab & Biriyani).
  - Instant receipt generation and automatic food budget logging.

- **Monthly Budget & Savings Overview**:
  - 31-day spend tracking bar graph highlighting the active day.
  - Student discount savings tracker.
  - Meal type spending breakdown (Lunch, Dinner, Breakfast, Snacks).

- **Student Profile**:
  - Comprehensive body metrics (BMI, height, weight).
  - Lifestyle and dietary preference toggles (Vegetarian, Gym, Mess plan, Location).
  - Onboarding re-run affordance.

## Feast Reserve Budget Formulation & Engine

All monetary calculations are performed in **integer paisa** ($1\text{ Tk} = 100\text{ paisa}$) for exact mathematical precision:
- **Monthly Pool**: $P = \text{Monthly Budget} - \text{Fixed Costs}$ (e.g. $700,000\text{ paisa} = \text{৳}7,000$).
- **Base Daily Budget**: $b_0 = P / D$ (e.g. $23,333\text{ paisa} = \text{৳}233.33$).
- **Feast Budget**: $f = 2 \times b_0$ (e.g. $46,666\text{ paisa} = \text{৳}466.66$ per feast, $n = 2$ feasts).
- **Daily Budget**: $b = (P - n \times f) / D$ (e.g. $20,222\text{ paisa} = \text{৳}202.22$).
- **Meal Allowance ($A$)**: Split as Breakfast 20%, Lunch 40% ($8,088\text{ paisa} = \text{৳}80.88$), Snacks 15%, Dinner 25%.
- **Ledger Invariant**: $\sum(\text{meal allowances}) + \sum(\text{planned feast budgets}) + U \le P$.
- **Deterministic Gatekeeper**: The AI does not touch the database directly; it communicates via tool calling (`check()`, `rearrangeFeast()`, `applyUpdate()`).
  - If a meal item price $X > A$: calculates shortfall $s = X - A$.
  - Uses unallocated balance $U$ first; if insufficient, proposes cancelling an upcoming feast (budget $F$). Remainder $r = F - s$ returns to $U$.
  - Requires explicit user confirmation; includes 1-tap **Undo** restoring both meal and feast.

## Cloud Backend & Multi-Tier Tables

- **Central Food Catalog** (`/central_foods/{foodId}`): Verified campus meals, calories, pricing, partner tags.
- **Partner Restaurants** (`/partner_restaurants/{partnerId}`): Outlets with student discounts.
- **Crowdsourced Staging** (`/community_food_submissions/{submissionId}`): Holds custom meal/expense entries logged by students for developer review.
- **User Profile & Feast Buckets** (`/users/{userId}`): Student profile, pool $P$, unallocated balance $U$, and active feasts.
- **Personalized Meal Slots** (`/users/{userId}/meals/{mealId}`): Real-time user daily meal allocations.
- **Payment History** (`/users/{userId}/payments/{paymentId}`): Transaction log with `trxId`, merchant, method (bKash/Nagad), and timestamps.

## Tech Stack

- **Platform**: Android (minSdk 26, targetSdk 36)
- **Language**: Kotlin 2.2.21
- **UI Toolkit**: Jetpack Compose with Material Design 3
- **Backend**: Firebase Firestore (BoM 33.10.0), Firebase Authentication, Jetpack Credential Manager
- **Design System**: Glassmorphism with deep midnight gradient backgrounds and vibrant mint/cyan/orange neon accents
- **Build System**: Gradle 9.3.1 with Android Gradle Plugin 9.1.1

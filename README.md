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

## Tech Stack

- **Platform**: Android (minSdk 26, targetSdk 36)
- **Language**: Kotlin 2.2.21
- **UI Toolkit**: Jetpack Compose with Material Design 3
- **Design System**: Glassmorphism with deep midnight gradient backgrounds and vibrant mint/cyan/orange neon accents
- **Build System**: Gradle 9.3.1 with Android Gradle Plugin 9.1.1

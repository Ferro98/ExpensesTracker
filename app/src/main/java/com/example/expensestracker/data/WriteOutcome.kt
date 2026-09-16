package com.example.expensestracker.data

/**
 * How a Firestore write actually went, for call sites that need to tell "genuinely failed" apart
 * from "still offline" - the two look identical as thrown exceptions but call for different UI:
 * a real failure is worth a retry prompt, a timeout isn't (the write already applied to the local
 * cache and will sync on its own; suggesting a retry there is how duplicate submissions happen).
 */
enum class WriteOutcome { SUCCESS, TIMED_OUT, FAILED }

package com.example.healthpro.genie

/**
 * Per-app UI automation flow configuration.
 *
 * Flow configs define the sequence of AccessibilityService actions
 * to perform within a target app (search → result → cart → checkout).
 *
 * Configs are loaded from JSON (res/raw/flow_configs.json) and can be
 * updated remotely without app changes.
 */

// ── Flow Config ──────────────────────────────────────────────

data class UiFlowConfig(
    val platformId: String,       // e.g. "SWIGGY"
    val version: Int,             // Config version for update tracking
    val appName: String,
    val packageName: String,
    val steps: List<FlowStep>
)

// ── Flow Steps ───────────────────────────────────────────────

sealed class FlowStep(
    open val name: String,
    open val timeoutMs: Long = 5000L,
    open val description: String = ""   // Human-readable, shown in UI
) {
    /** Wait until a node matching [selector] appears. */
    data class WaitForNode(
        val selector: NodeSelector,
        override val name: String = "wait",
        override val timeoutMs: Long = 5000L,
        override val description: String = ""
    ) : FlowStep(name, timeoutMs, description)

    /** Find and click a node matching [selector]. */
    data class ClickNode(
        val selector: NodeSelector,
        override val name: String = "click",
        override val timeoutMs: Long = 5000L,
        override val description: String = ""
    ) : FlowStep(name, timeoutMs, description)

    /** Find a node matching [selector] and type [textKey] into it.
     *  [textKey] = "QUERY" means substitute the user's search query. */
    data class TypeText(
        val selector: NodeSelector,
        val textKey: String = "QUERY",
        override val name: String = "type",
        override val timeoutMs: Long = 5000L,
        override val description: String = ""
    ) : FlowStep(name, timeoutMs, description)

    /** Perform an IME action (e.g., submit/search) on the focused input. */
    data class PerformIme(
        val imeAction: Int = 0,   // 0 = default (search/go/done)
        override val name: String = "ime",
        override val timeoutMs: Long = 2000L,
        override val description: String = "Submit search"
    ) : FlowStep(name, timeoutMs, description)

    /** Click the first matching node from a list (e.g., first search result). */
    data class ClickFirstMatch(
        val selector: NodeSelector,
        override val name: String = "click_first",
        override val timeoutMs: Long = 5000L,
        override val description: String = ""
    ) : FlowStep(name, timeoutMs, description)

    /** Scroll in a direction. */
    data class Scroll(
        val direction: ScrollDirection = ScrollDirection.FORWARD,
        override val name: String = "scroll",
        override val timeoutMs: Long = 2000L,
        override val description: String = "Scrolling..."
    ) : FlowStep(name, timeoutMs, description)

    /** Wait for a fixed delay (e.g., for content to load). */
    data class Delay(
        val delayMs: Long,
        override val name: String = "delay",
        override val timeoutMs: Long = 0L,
        override val description: String = "Waiting for content..."
    ) : FlowStep(name, timeoutMs, description)

    /** Terminal state: stop before payment. User makes final tap. */
    data class StopBeforePayment(
        override val name: String = "stop_payment",
        override val timeoutMs: Long = 0L,
        override val description: String = "Ready! Please review and place your order."
    ) : FlowStep(name, timeoutMs, description)

    /** Terminal state: stopped because OTP / Captcha / sign-in detected. */
    data class StopForAuth(
        val reason: String = "Authentication required",
        override val name: String = "stop_auth",
        override val timeoutMs: Long = 0L,
        override val description: String = "Please complete authentication manually."
    ) : FlowStep(name, timeoutMs, description)
}

enum class ScrollDirection { FORWARD, BACKWARD }

// ── Node Selector ────────────────────────────────────────────

/**
 * Defines how to find an AccessibilityNodeInfo.
 *
 * SELECTOR PRECEDENCE (enforced in GenieAccessibilityService):
 *   1. resourceId        — most stable, OEM-safe
 *   2. text              — exact match
 *   3. textContains      — partial match
 *   4. contentDescription — accessibility label
 *   5. className         — widget type (e.g. EditText)
 *   6. useFirstClickable — absolute last resort
 *
 * Multiple fields can be set for AND-matching (all must match).
 * For OR-matching, use [alternateSelectors].
 */
data class NodeSelector(
    val resourceId: String? = null,
    val text: String? = null,
    val textContains: String? = null,
    val contentDescription: String? = null,
    val descriptionContains: String? = null,
    val className: String? = null,
    val useFirstClickable: Boolean = false,
    val alternateSelectors: List<NodeSelector>? = null   // Tried in order if primary fails
)

// ── Step Status (for UI updates) ─────────────────────────────

data class StepStatus(
    val stepIndex: Int,
    val totalSteps: Int,
    val stepName: String,
    val description: String,
    val state: StepState
)

enum class StepState {
    RUNNING,
    SUCCESS,
    FAILED,
    STOPPED_PAYMENT,   // Reached checkout — user takes over
    STOPPED_AUTH       // OTP / Captcha detected
}

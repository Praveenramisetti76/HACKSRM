package com.example.healthpro.genie

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.KeyguardManager
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Core AccessibilityService for Genie automation.
 *
 * Handles node traversal, click, setText, IME actions, scroll.
 * Runs a deterministic state machine per-app flow.
 *
 * SECURITY:
 * - Only runs after explicit user consent
 * - Requires screen unlocked + this app in foreground to start
 * - Never taps "Place Order" / "Pay" — stops before payment
 * - Stops on OTP / Captcha / sign-in detection
 *
 * PRIVACY:
 * - Logs contain only step name + pass/fail + timestamp
 * - No addresses, payment info, or prescription text logged
 */
class GenieAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "GenieA11y"
        var instance: GenieAccessibilityService? = null
            private set

        fun isRunning(): Boolean = instance != null

        // Status updates flow (observed by GenieViewModel)
        private val _statusFlow = MutableSharedFlow<StepStatus>(replay = 1)
        val statusFlow: SharedFlow<StepStatus> = _statusFlow

        // Automation completion callback
        private val _completionFlow = MutableSharedFlow<AutomationResult>(replay = 1)
        val completionFlow: SharedFlow<AutomationResult> = _completionFlow
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var automationJob: Job? = null

    // ── Lifecycle ────────────────────────────────────────────

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "GenieAccessibilityService connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // We use targeted event types (configured in XML) to reduce noise.
        // Event processing is handled within the flow execution coroutine.
    }

    override fun onInterrupt() {
        cancelAutomation()
        Log.w(TAG, "GenieAccessibilityService interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        cancelAutomation()
        serviceScope.cancel()
        instance = null
        Log.i(TAG, "GenieAccessibilityService destroyed")
    }

    // ── Security Guards ──────────────────────────────────────

    fun isScreenUnlocked(): Boolean {
        val keyguard = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        return keyguard?.isKeyguardLocked == false
    }

    // ── Flow Execution ───────────────────────────────────────

    fun executeFlow(config: UiFlowConfig, searchQuery: String) {
        cancelAutomation()

        automationJob = serviceScope.launch {
            try {
                Log.i(TAG, "Starting flow: ${config.platformId} query=\"[REDACTED]\"")

                val steps = config.steps
                for ((index, step) in steps.withIndex()) {
                    // Check cancellation
                    ensureActive()

                    // Emit status
                    _statusFlow.emit(StepStatus(
                        stepIndex = index,
                        totalSteps = steps.size,
                        stepName = step.name,
                        description = step.description,
                        state = StepState.RUNNING
                    ))

                    // Execute step with timeout
                    val success = if (step.timeoutMs > 0) {
                        withTimeoutOrNull(step.timeoutMs) {
                            executeStep(step, searchQuery)
                        } ?: false
                    } else {
                        executeStep(step, searchQuery)
                    }

                    // Check for terminal states
                    when (step) {
                        is FlowStep.StopBeforePayment -> {
                            _statusFlow.emit(StepStatus(
                                stepIndex = index, totalSteps = steps.size,
                                stepName = step.name, description = step.description,
                                state = StepState.STOPPED_PAYMENT
                            ))
                            _completionFlow.emit(AutomationResult.StoppedAtPayment(step.description))
                            Log.i(TAG, "Flow stopped at payment (step $index)")
                            return@launch
                        }
                        is FlowStep.StopForAuth -> {
                            _statusFlow.emit(StepStatus(
                                stepIndex = index, totalSteps = steps.size,
                                stepName = step.name, description = step.description,
                                state = StepState.STOPPED_AUTH
                            ))
                            _completionFlow.emit(AutomationResult.StoppedForAuth(step.reason))
                            Log.i(TAG, "Flow stopped for auth (step $index)")
                            return@launch
                        }
                        else -> { /* continue */ }
                    }

                    if (!success) {
                        // Step failed
                        _statusFlow.emit(StepStatus(
                            stepIndex = index, totalSteps = steps.size,
                            stepName = step.name,
                            description = "Failed: Could not complete '${step.description}'",
                            state = StepState.FAILED
                        ))
                        _completionFlow.emit(AutomationResult.Failed(
                            failedAtStep = index,
                            stepName = step.name,
                            reason = "Could not find or interact with UI element for: ${step.description}"
                        ))
                        Log.w(TAG, "Flow failed at step $index: ${step.name}")
                        return@launch
                    }

                    // Step succeeded
                    _statusFlow.emit(StepStatus(
                        stepIndex = index, totalSteps = steps.size,
                        stepName = step.name, description = step.description,
                        state = StepState.SUCCESS
                    ))
                    Log.i(TAG, "Step $index/${steps.size} succeeded: ${step.name}")
                }

                // All steps completed without terminal state
                _completionFlow.emit(AutomationResult.Completed)

            } catch (e: CancellationException) {
                _completionFlow.emit(AutomationResult.Cancelled)
                Log.i(TAG, "Flow cancelled")
            } catch (e: Exception) {
                _completionFlow.emit(AutomationResult.Failed(
                    failedAtStep = -1,
                    stepName = "unknown",
                    reason = "Unexpected error: ${e.message}"
                ))
                Log.e(TAG, "Flow error", e)
            }
        }
    }

    fun cancelAutomation() {
        automationJob?.cancel()
        automationJob = null
    }

    fun retryFromStep(config: UiFlowConfig, searchQuery: String, fromStep: Int) {
        cancelAutomation()
        val trimmedConfig = config.copy(steps = config.steps.drop(fromStep))
        executeFlow(trimmedConfig, searchQuery)
    }

    // ── Step Execution ───────────────────────────────────────

    private suspend fun executeStep(step: FlowStep, searchQuery: String): Boolean {
        return when (step) {
            is FlowStep.WaitForNode -> waitForNode(step.selector)
            is FlowStep.ClickNode -> clickBySelector(step.selector)
            is FlowStep.TypeText -> {
                val text = if (step.textKey == "QUERY") searchQuery else step.textKey
                typeBySelector(step.selector, text)
            }
            is FlowStep.PerformIme -> performImeAction()
            is FlowStep.ClickFirstMatch -> clickFirstClickable(step.selector)
            is FlowStep.Scroll -> {
                if (step.direction == ScrollDirection.FORWARD) scrollForward()
                else scrollBackward()
            }
            is FlowStep.Delay -> {
                delay(step.delayMs)
                true
            }
            is FlowStep.StopBeforePayment -> true  // Handled in caller
            is FlowStep.StopForAuth -> true          // Handled in caller
        }
    }

    // ── Node Finding (Selector Precedence) ───────────────────

    /**
     * Find a node using the defined precedence:
     * 1. resourceId → 2. text → 3. textContains → 4. contentDescription
     * → 5. className → 6. firstClickable
     *
     * If primary selector fails, tries alternateSelectors in order.
     */
    private fun findNode(selector: NodeSelector): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null

        // Try primary selector
        val primary = findNodeWithSelector(root, selector)
        if (primary != null) return primary

        // Try alternate selectors
        selector.alternateSelectors?.forEach { alt ->
            val node = findNodeWithSelector(root, alt)
            if (node != null) return node
        }

        return null
    }

    private fun findNodeWithSelector(root: AccessibilityNodeInfo, sel: NodeSelector): AccessibilityNodeInfo? {
        // Priority 1: resourceId
        sel.resourceId?.let { id ->
            val nodes = root.findAccessibilityNodeInfosByViewId(id)
            if (nodes.isNotEmpty()) return nodes[0]
        }

        // Priority 2: exact text
        sel.text?.let { text ->
            val nodes = root.findAccessibilityNodeInfosByText(text)
            nodes.forEach { node ->
                if (node.text?.toString() == text) return node
            }
        }

        // Priority 3: textContains
        sel.textContains?.let { partial ->
            val nodes = root.findAccessibilityNodeInfosByText(partial)
            if (nodes.isNotEmpty()) return nodes[0]
        }

        // Priority 4: contentDescription
        sel.contentDescription?.let { desc ->
            return findNodeByDescriptionRecursive(root, desc, exact = true)
        }
        sel.descriptionContains?.let { desc ->
            return findNodeByDescriptionRecursive(root, desc, exact = false)
        }

        // Priority 5: className
        sel.className?.let { cls ->
            return findNodeByClassRecursive(root, cls)
        }

        // Priority 6: first clickable child
        if (sel.useFirstClickable) {
            return findFirstClickableRecursive(root)
        }

        return null
    }

    // ── Recursive Helpers ────────────────────────────────────

    private fun findNodeByDescriptionRecursive(
        node: AccessibilityNodeInfo,
        desc: String,
        exact: Boolean
    ): AccessibilityNodeInfo? {
        val nodeDesc = node.contentDescription?.toString() ?: ""
        if (exact && nodeDesc == desc) return node
        if (!exact && nodeDesc.contains(desc, ignoreCase = true)) return node

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findNodeByDescriptionRecursive(child, desc, exact)
            if (result != null) return result
        }
        return null
    }

    private fun findNodeByClassRecursive(
        node: AccessibilityNodeInfo,
        className: String
    ): AccessibilityNodeInfo? {
        if (node.className?.toString() == className) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findNodeByClassRecursive(child, className)
            if (result != null) return result
        }
        return null
    }

    private fun findFirstClickableRecursive(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isClickable && node.isVisibleToUser) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findFirstClickableRecursive(child)
            if (result != null) return result
        }
        return null
    }

    // ── Actions ──────────────────────────────────────────────

    private suspend fun waitForNode(selector: NodeSelector): Boolean {
        // Poll for node appearance
        repeat(10) {
            if (findNode(selector) != null) return true
            delay(500)
        }
        return false
    }

    private suspend fun clickBySelector(selector: NodeSelector): Boolean {
        repeat(5) {
            val node = findNode(selector)
            if (node != null) return clickNode(node)
            delay(500)
        }
        return false
    }

    private fun clickNode(node: AccessibilityNodeInfo): Boolean {
        // Try clicking the node directly
        if (node.isClickable) {
            return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
        // Try clicking a clickable parent
        var parent = node.parent
        while (parent != null) {
            if (parent.isClickable) {
                return parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            parent = parent.parent
        }
        return false
    }

    private suspend fun typeBySelector(selector: NodeSelector, text: String): Boolean {
        repeat(5) {
            val node = findNode(selector)
            if (node != null) return setNodeText(node, text)
            delay(500)
        }
        return false
    }

    private fun setNodeText(node: AccessibilityNodeInfo, text: String): Boolean {
        // Focus the node
        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        node.performAction(AccessibilityNodeInfo.ACTION_CLICK)

        // Clear existing text
        val clearArgs = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "")
        }
        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, clearArgs)

        // Set new text
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    private fun performImeAction(): Boolean {
        val root = rootInActiveWindow ?: return false
        val editText = findNodeByClassRecursive(root, "android.widget.EditText")
        if (editText != null) {
            // Try submitting via IME action on the focused node
            // ACTION_IME_ENTER is available on API 30+, fallback to click
            return try {
                editText.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
            } catch (e: Exception) {
                // Fallback: simulate enter press is not directly possible via a11y,
                // so we click the node which may trigger submit on single-line fields
                editText.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
        }
        return false
    }

    private suspend fun clickFirstClickable(selector: NodeSelector): Boolean {
        repeat(5) {
            val root = rootInActiveWindow ?: return@repeat
            val node = if (selector.useFirstClickable) {
                findFirstClickableRecursive(root)
            } else {
                findNode(selector)
            }
            if (node != null) return clickNode(node)
            delay(500)
        }
        return false
    }

    private fun scrollForward(): Boolean {
        val root = rootInActiveWindow ?: return false
        return findScrollableNode(root)?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            ?: false
    }

    private fun scrollBackward(): Boolean {
        val root = rootInActiveWindow ?: return false
        return findScrollableNode(root)?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
            ?: false
    }

    private fun findScrollableNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isScrollable) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findScrollableNode(child)
            if (result != null) return result
        }
        return null
    }
}

// ── Automation Result ────────────────────────────────────────

sealed class AutomationResult {
    object Completed : AutomationResult()
    object Cancelled : AutomationResult()
    data class StoppedAtPayment(val message: String) : AutomationResult()
    data class StoppedForAuth(val reason: String) : AutomationResult()
    data class Failed(
        val failedAtStep: Int,
        val stepName: String,
        val reason: String
    ) : AutomationResult()
}

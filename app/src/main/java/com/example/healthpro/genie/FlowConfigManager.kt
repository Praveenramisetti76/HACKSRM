package com.example.healthpro.genie

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.example.healthpro.R
import java.io.InputStreamReader

/**
 * Loads UiFlowConfig from JSON resource (res/raw/flow_configs.json).
 *
 * Configs are versioned and shipped as a raw JSON resource.
 * Future: can fetch updated configs from a remote endpoint.
 */
object FlowConfigManager {

    private var cachedConfigs: Map<String, UiFlowConfig>? = null

    /**
     * Get the flow config for a given platform.
     */
    fun getConfig(context: Context, platform: Platform): UiFlowConfig? {
        return getAllConfigs(context)[platform.name]
    }

    /**
     * Get all loaded flow configs.
     */
    fun getAllConfigs(context: Context): Map<String, UiFlowConfig> {
        cachedConfigs?.let { return it }

        val configs = loadFromJson(context)
        cachedConfigs = configs
        return configs
    }

    /**
     * Reload configs (e.g. after a remote update).
     */
    fun reload(context: Context) {
        cachedConfigs = null
        getAllConfigs(context)
    }

    // ── JSON Loading ─────────────────────────────────────────

    private fun loadFromJson(context: Context): Map<String, UiFlowConfig> {
        return try {
            val inputStream = context.resources.openRawResource(R.raw.flow_configs)
            val reader = InputStreamReader(inputStream)
            val type = object : TypeToken<FlowConfigsJson>() {}.type
            val json: FlowConfigsJson = Gson().fromJson(reader, type)
            reader.close()

            json.configs.associate { config ->
                config.platformId to config.toUiFlowConfig()
            }
        } catch (e: Exception) {
            // Fallback to hardcoded defaults if JSON fails
            getHardcodedDefaults()
        }
    }

    // ── JSON Data Classes ────────────────────────────────────

    private data class FlowConfigsJson(
        val schemaVersion: Int,
        val configs: List<FlowConfigJson>
    )

    private data class FlowConfigJson(
        val platformId: String,
        val version: Int,
        val appName: String,
        val packageName: String,
        val steps: List<FlowStepJson>
    ) {
        fun toUiFlowConfig(): UiFlowConfig = UiFlowConfig(
            platformId = platformId,
            version = version,
            appName = appName,
            packageName = packageName,
            steps = steps.map { it.toFlowStep() }
        )
    }

    private data class FlowStepJson(
        val type: String,
        val name: String = "",
        val timeoutMs: Long = 5000L,
        val description: String = "",
        val selector: NodeSelectorJson? = null,
        val textKey: String? = null,
        val delayMs: Long? = null,
        val direction: String? = null,
        val imeAction: Int? = null,
        val reason: String? = null,
        val alternateSelectors: List<NodeSelectorJson>? = null
    ) {
        fun toFlowStep(): FlowStep = when (type) {
            "wait" -> FlowStep.WaitForNode(
                selector = selector?.toNodeSelector() ?: NodeSelector(),
                name = name, timeoutMs = timeoutMs, description = description
            )
            "click" -> FlowStep.ClickNode(
                selector = selector?.toNodeSelector() ?: NodeSelector(),
                name = name, timeoutMs = timeoutMs, description = description
            )
            "type" -> FlowStep.TypeText(
                selector = selector?.toNodeSelector() ?: NodeSelector(),
                textKey = textKey ?: "QUERY",
                name = name, timeoutMs = timeoutMs, description = description
            )
            "ime" -> FlowStep.PerformIme(
                imeAction = imeAction ?: 0,
                name = name, timeoutMs = timeoutMs, description = description
            )
            "click_first" -> FlowStep.ClickFirstMatch(
                selector = selector?.toNodeSelector() ?: NodeSelector(),
                name = name, timeoutMs = timeoutMs, description = description
            )
            "scroll" -> FlowStep.Scroll(
                direction = if (direction == "backward") ScrollDirection.BACKWARD else ScrollDirection.FORWARD,
                name = name, timeoutMs = timeoutMs, description = description
            )
            "delay" -> FlowStep.Delay(
                delayMs = delayMs ?: 2000L,
                name = name, timeoutMs = 0L, description = description
            )
            "stop_payment" -> FlowStep.StopBeforePayment(
                name = name, description = description
            )
            "stop_auth" -> FlowStep.StopForAuth(
                reason = reason ?: "Authentication required",
                name = name, description = description
            )
            else -> FlowStep.Delay(delayMs = 1000L, name = "unknown_$type")
        }
    }

    private data class NodeSelectorJson(
        val resourceId: String? = null,
        val text: String? = null,
        val textContains: String? = null,
        val contentDescription: String? = null,
        val descriptionContains: String? = null,
        val className: String? = null,
        val useFirstClickable: Boolean = false,
        val alternateSelectors: List<NodeSelectorJson>? = null
    ) {
        fun toNodeSelector(): NodeSelector = NodeSelector(
            resourceId = resourceId,
            text = text,
            textContains = textContains,
            contentDescription = contentDescription,
            descriptionContains = descriptionContains,
            className = className,
            useFirstClickable = useFirstClickable,
            alternateSelectors = alternateSelectors?.map { it.toNodeSelector() }
        )
    }

    // ── Hardcoded Fallback Defaults ──────────────────────────

    private fun getHardcodedDefaults(): Map<String, UiFlowConfig> = mapOf(
        "SWIGGY" to UiFlowConfig(
            platformId = "SWIGGY",
            version = 1,
            appName = "Swiggy",
            packageName = "in.swiggy.android",
            steps = listOf(
                FlowStep.Delay(2000L, name = "wait_app_load", description = "Waiting for Swiggy to load..."),
                FlowStep.ClickNode(
                    selector = NodeSelector(descriptionContains = "search", alternateSelectors = listOf(
                        NodeSelector(textContains = "Search"),
                        NodeSelector(resourceId = "search")
                    )),
                    name = "tap_search", description = "Opening search..."
                ),
                FlowStep.TypeText(
                    selector = NodeSelector(className = "android.widget.EditText"),
                    textKey = "QUERY",
                    name = "type_query", description = "Typing search query..."
                ),
                FlowStep.PerformIme(name = "submit_search", description = "Submitting search..."),
                FlowStep.Delay(2500L, name = "wait_results", description = "Waiting for results..."),
                FlowStep.ClickFirstMatch(
                    selector = NodeSelector(useFirstClickable = true),
                    name = "select_result", description = "Selecting first result..."
                ),
                FlowStep.Delay(1500L, name = "wait_item", description = "Loading item details..."),
                FlowStep.ClickNode(
                    selector = NodeSelector(textContains = "ADD", alternateSelectors = listOf(
                        NodeSelector(textContains = "Add"),
                        NodeSelector(descriptionContains = "add")
                    )),
                    name = "add_to_cart", description = "Adding to cart..."
                ),
                FlowStep.Delay(1500L, name = "wait_cart", description = "Updating cart..."),
                FlowStep.ClickNode(
                    selector = NodeSelector(textContains = "Checkout", alternateSelectors = listOf(
                        NodeSelector(textContains = "View Cart"),
                        NodeSelector(textContains = "cart"),
                        NodeSelector(textContains = "Proceed")
                    )),
                    name = "go_checkout", description = "Opening checkout..."
                ),
                FlowStep.StopBeforePayment(description = "Cart is ready! Please review and place your order.")
            )
        ),
        "AMAZON" to UiFlowConfig(
            platformId = "AMAZON",
            version = 1,
            appName = "Amazon",
            packageName = "in.amazon.mShop.android.shopping",
            steps = listOf(
                FlowStep.Delay(2500L, name = "wait_app_load", description = "Waiting for Amazon to load..."),
                FlowStep.ClickNode(
                    selector = NodeSelector(resourceId = "in.amazon.mShop.android.shopping:id/chrome_search_hint_view", alternateSelectors = listOf(
                        NodeSelector(descriptionContains = "search"),
                        NodeSelector(textContains = "Search Amazon")
                    )),
                    name = "tap_search", description = "Opening search..."
                ),
                FlowStep.TypeText(
                    selector = NodeSelector(className = "android.widget.EditText"),
                    textKey = "QUERY",
                    name = "type_query", description = "Typing search query..."
                ),
                FlowStep.PerformIme(name = "submit_search", description = "Submitting search..."),
                FlowStep.Delay(3000L, name = "wait_results", description = "Waiting for results..."),
                FlowStep.ClickFirstMatch(
                    selector = NodeSelector(useFirstClickable = true),
                    name = "select_product", description = "Selecting first product..."
                ),
                FlowStep.Delay(2000L, name = "wait_product", description = "Loading product page..."),
                FlowStep.ClickNode(
                    selector = NodeSelector(text = "Add to Cart", alternateSelectors = listOf(
                        NodeSelector(textContains = "Add to Cart"),
                        NodeSelector(descriptionContains = "Add to Cart")
                    )),
                    name = "add_to_cart", description = "Adding to cart..."
                ),
                FlowStep.Delay(2000L, name = "wait_added", description = "Item added to cart..."),
                FlowStep.ClickNode(
                    selector = NodeSelector(textContains = "Proceed", alternateSelectors = listOf(
                        NodeSelector(textContains = "Cart"),
                        NodeSelector(textContains = "Checkout"),
                        NodeSelector(textContains = "Buy")
                    )),
                    name = "go_checkout", description = "Opening checkout..."
                ),
                FlowStep.StopBeforePayment(description = "Cart is ready! Please review and complete your purchase.")
            )
        )
    )
}

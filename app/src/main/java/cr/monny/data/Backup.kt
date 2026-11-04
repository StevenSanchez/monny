package cr.monny.data

import org.json.JSONArray
import org.json.JSONObject

/*
 * Respaldo completo en JSON (sin dependencias externas).
 *
 * El archivo incluye todas las tablas y sus IDs, para poder restaurar en otro
 * teléfono conservando las relaciones entre gastos, cuotas, sobres, etc.
 * Al restaurar se reemplaza todo el contenido actual.
 */

data class BackupData(
    val cards: List<Card> = emptyList(),
    val categories: List<Category> = emptyList(),
    val expenses: List<Expense> = emptyList(),
    val payments: List<Payment> = emptyList(),
    val covers: List<Cover> = emptyList(),
    val envelopes: List<Envelope> = emptyList(),
    val envelopeTxs: List<EnvelopeTx> = emptyList(),
    val plans: List<InstallmentPlan> = emptyList(),
    val recurring: List<RecurringExpense> = emptyList(),
    val recurringCharges: List<RecurringCharge> = emptyList(),
)

/** Respaldo completo en JSON, sin dependencias externas. */
object Backup {
    const val VERSION = 1

    /** Serializa todas las listas a un JSON legible. */
    fun toJson(data: BackupData): String {
        val root = JSONObject()
        root.put("app", "monny")
        root.put("version", VERSION)
        root.put("exportedAt", System.currentTimeMillis())

        root.put("cards", JSONArray().apply {
            data.cards.forEach { c ->
                put(JSONObject().apply {
                    put("id", c.id); put("name", c.name); put("bank", c.bank)
                    put("last4", c.last4); put("colorHex", c.colorHex)
                    put("cutDay", c.cutDay); put("paymentDueDays", c.paymentDueDays)
                    put("notifyCut", c.notifyCut); put("notifyDaysBeforeCut", c.notifyDaysBeforeCut)
                    put("notifyPayment", c.notifyPayment); put("archived", c.archived)
                    put("cutDayInNextCycle", c.cutDayInNextCycle)
                })
            }
        })

        root.put("categories", JSONArray().apply {
            data.categories.forEach { c ->
                put(JSONObject().apply {
                    put("id", c.id); put("name", c.name); put("colorHex", c.colorHex)
                    put("iconKey", c.iconKey); put("sortOrder", c.sortOrder)
                    put("archived", c.archived); put("budgetCents", c.budgetCents)
                })
            }
        })

        root.put("expenses", JSONArray().apply {
            data.expenses.forEach { e ->
                put(JSONObject().apply {
                    put("id", e.id); put("cardId", e.cardId); put("categoryId", e.categoryId)
                    put("date", e.date); put("cycleKey", e.cycleKey)
                    put("amountCents", e.amountCents); put("merchant", e.merchant)
                    put("note", e.note); put("receiptPath", e.receiptPath)
                    put("createdAt", e.createdAt)
                    put("planId", e.planId ?: JSONObject.NULL)
                    put("installmentIndex", e.installmentIndex ?: JSONObject.NULL)
                    put("installmentCount", e.installmentCount ?: JSONObject.NULL)
                    put("sharedPercent", e.sharedPercent)
                    put("recurringId", e.recurringId ?: JSONObject.NULL)
                })
            }
        })

        root.put("payments", JSONArray().apply {
            data.payments.forEach { p ->
                put(JSONObject().apply {
                    put("id", p.id); put("cardId", p.cardId); put("cycleKey", p.cycleKey)
                    put("date", p.date); put("amountCents", p.amountCents)
                    put("note", p.note); put("receiptPath", p.receiptPath)
                    put("createdAt", p.createdAt)
                })
            }
        })

        root.put("covers", JSONArray().apply {
            data.covers.forEach { c ->
                put(JSONObject().apply {
                    put("id", c.id); put("cardId", c.cardId); put("cycleKey", c.cycleKey)
                    put("date", c.date); put("amountCents", c.amountCents)
                    put("envelopeId", c.envelopeId ?: JSONObject.NULL)
                    put("note", c.note); put("createdAt", c.createdAt)
                })
            }
        })

        root.put("envelopes", JSONArray().apply {
            data.envelopes.forEach { e ->
                put(JSONObject().apply {
                    put("id", e.id); put("name", e.name)
                    put("colorHex", e.colorHex); put("createdAt", e.createdAt)
                })
            }
        })

        root.put("envelopeTxs", JSONArray().apply {
            data.envelopeTxs.forEach { t ->
                put(JSONObject().apply {
                    put("id", t.id); put("envelopeId", t.envelopeId)
                    put("date", t.date); put("amountCents", t.amountCents)
                    put("note", t.note); put("kind", t.kind); put("createdAt", t.createdAt)
                })
            }
        })

        root.put("plans", JSONArray().apply {
            data.plans.forEach { p ->
                put(JSONObject().apply {
                    put("id", p.id); put("cardId", p.cardId); put("categoryId", p.categoryId)
                    put("merchant", p.merchant); put("totalCents", p.totalCents)
                    put("months", p.months); put("firstDate", p.firstDate)
                    put("note", p.note); put("createdAt", p.createdAt)
                })
            }
        })

        root.put("recurring", JSONArray().apply {
            data.recurring.forEach { r ->
                put(JSONObject().apply {
                    put("id", r.id); put("cardId", r.cardId); put("categoryId", r.categoryId)
                    put("merchant", r.merchant); put("amountCents", r.amountCents)
                    put("firstDate", r.firstDate); put("note", r.note)
                    put("active", r.active); put("sharedPercent", r.sharedPercent)
                    put("createdAt", r.createdAt)
                })
            }
        })

        root.put("recurringCharges", JSONArray().apply {
            data.recurringCharges.forEach { c ->
                put(JSONObject().apply {
                    put("ruleId", c.ruleId); put("cycleKey", c.cycleKey)
                })
            }
        })

        return root.toString()
    }

    /** Lee un respaldo y valida que sea de Monny y de una versión conocida. */
    fun parse(json: String): BackupData {
        val root = JSONObject(json)
        require(root.optString("app") == "monny") { "El archivo no es un respaldo de Monny" }
        require(root.optInt("version", 0) in 1..VERSION) { "Versión de respaldo no compatible" }

        return BackupData(
            cards = root.optJSONArray("cards").objects().map { o ->
                Card(
                    id = o.getLong("id"), name = o.getString("name"),
                    bank = o.optString("bank"), last4 = o.optString("last4"),
                    colorHex = o.optString("colorHex", "#1F5C4B"),
                    cutDay = o.optInt("cutDay", 1),
                    paymentDueDays = o.optInt("paymentDueDays", 14),
                    notifyCut = o.optBoolean("notifyCut", true),
                    notifyDaysBeforeCut = o.optInt("notifyDaysBeforeCut", 3),
                    notifyPayment = o.optBoolean("notifyPayment", true),
                    archived = o.optBoolean("archived", false),
                    cutDayInNextCycle = o.optBoolean("cutDayInNextCycle", true),
                )
            },
            categories = root.optJSONArray("categories").objects().map { o ->
                Category(
                    id = o.getLong("id"), name = o.getString("name"),
                    colorHex = o.optString("colorHex", "#1F5C4B"),
                    iconKey = o.optString("iconKey", "otros"),
                    sortOrder = o.optInt("sortOrder", 0),
                    archived = o.optBoolean("archived", false),
                    budgetCents = o.optLong("budgetCents", 0),
                )
            },
            expenses = root.optJSONArray("expenses").objects().map { o ->
                Expense(
                    id = o.getLong("id"), cardId = o.getLong("cardId"),
                    categoryId = o.getLong("categoryId"), date = o.getLong("date"),
                    cycleKey = o.getLong("cycleKey"), amountCents = o.getLong("amountCents"),
                    merchant = o.optString("merchant"), note = o.optString("note"),
                    receiptPath = o.optStringOrNull("receiptPath"),
                    createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                    planId = if (o.isNull("planId")) null else o.getLong("planId"),
                    installmentIndex = if (o.isNull("installmentIndex")) null else o.getInt("installmentIndex"),
                    installmentCount = if (o.isNull("installmentCount")) null else o.getInt("installmentCount"),
                    sharedPercent = o.optInt("sharedPercent", 0),
                    recurringId = if (o.isNull("recurringId")) null else o.getLong("recurringId"),
                )
            },
            payments = root.optJSONArray("payments").objects().map { o ->
                Payment(
                    id = o.getLong("id"), cardId = o.getLong("cardId"),
                    cycleKey = o.getLong("cycleKey"), date = o.getLong("date"),
                    amountCents = o.getLong("amountCents"), note = o.optString("note"),
                    receiptPath = o.optStringOrNull("receiptPath"),
                    createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                )
            },
            covers = root.optJSONArray("covers").objects().map { o ->
                Cover(
                    id = o.getLong("id"), cardId = o.getLong("cardId"),
                    cycleKey = o.getLong("cycleKey"), date = o.getLong("date"),
                    amountCents = o.getLong("amountCents"),
                    envelopeId = if (o.isNull("envelopeId")) null else o.getLong("envelopeId"),
                    note = o.optString("note"),
                    createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                )
            },
            envelopes = root.optJSONArray("envelopes").objects().map { o ->
                Envelope(
                    id = o.getLong("id"), name = o.getString("name"),
                    colorHex = o.optString("colorHex", "#D9A441"),
                    createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                )
            },
            envelopeTxs = root.optJSONArray("envelopeTxs").objects().map { o ->
                EnvelopeTx(
                    id = o.getLong("id"), envelopeId = o.getLong("envelopeId"),
                    date = o.getLong("date"), amountCents = o.getLong("amountCents"),
                    note = o.optString("note"), kind = o.optString("kind", "aporte"),
                    createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                )
            },
            plans = root.optJSONArray("plans").objects().map { o ->
                InstallmentPlan(
                    id = o.getLong("id"), cardId = o.getLong("cardId"),
                    categoryId = o.getLong("categoryId"), merchant = o.optString("merchant"),
                    totalCents = o.getLong("totalCents"), months = o.getInt("months"),
                    firstDate = o.getLong("firstDate"), note = o.optString("note"),
                    createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                )
            },
            recurring = root.optJSONArray("recurring").objects().map { o ->
                RecurringExpense(
                    id = o.getLong("id"), cardId = o.getLong("cardId"),
                    categoryId = o.getLong("categoryId"), merchant = o.optString("merchant"),
                    amountCents = o.getLong("amountCents"), firstDate = o.getLong("firstDate"),
                    note = o.optString("note"), active = o.optBoolean("active", true),
                    sharedPercent = o.optInt("sharedPercent", 0),
                    createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                )
            },
            recurringCharges = root.optJSONArray("recurringCharges").objects().map { o ->
                RecurringCharge(ruleId = o.getLong("ruleId"), cycleKey = o.getLong("cycleKey"))
            },
        )
    }

    private fun JSONArray?.objects(): List<JSONObject> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { runCatching { getJSONObject(it) }.getOrNull() }
    }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }
}

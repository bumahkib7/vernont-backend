package com.vernont.workflow.flows.collection

import com.vernont.domain.product.ProductCollection

data class CreateCollectionInput(
    val title: String,
    val handle: String? = null,
    val metadata: Map<String, Any>? = null
)

data class CreateCollectionOutput(
    val collection: ProductCollection
)

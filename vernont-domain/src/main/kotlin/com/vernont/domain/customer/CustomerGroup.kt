package com.vernont.domain.customer

import com.vernont.domain.common.BaseEntity
import jakarta.persistence.*
import jakarta.validation.constraints.NotBlank

@Entity
@Table(
    name = "customer_group",
    indexes = [
        Index(name = "idx_customer_group_name", columnList = "name", unique = true),
        Index(name = "idx_customer_group_deleted_at", columnList = "deleted_at")
    ]
)
@NamedEntityGraph(
    name = "CustomerGroup.withCustomers",
    attributeNodes = [
        NamedAttributeNode("customers")
    ]
)
class CustomerGroup : BaseEntity() {

    @NotBlank
    @Column(nullable = false, unique = true)
    var name: String = ""

    @Column(columnDefinition = "TEXT")
    var description: String? = null

    @ManyToMany(mappedBy = "groups", fetch = FetchType.LAZY)
    var customers: MutableSet<Customer> = mutableSetOf()

    fun addCustomer(customer: Customer) {
        customers.add(customer)
        customer.groups.add(this)
    }

    fun removeCustomer(customer: Customer) {
        customers.remove(customer)
        customer.groups.remove(this)
    }

    fun getCustomerCount(): Int = customers.size

    fun hasCustomer(customerId: String): Boolean {
        return customers.any { it.id == customerId }
    }
}

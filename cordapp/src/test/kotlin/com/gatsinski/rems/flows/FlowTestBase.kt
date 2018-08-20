package com.gatsinski.rems.flows

import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.getOrThrow
import net.corda.testing.core.singleIdentity
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.StartedMockNode
import org.junit.After
import org.junit.Before

open class FlowTestBase {
    protected val address = "City, Test Residential Quarter, Building 1, Entrance A, №1"
    protected lateinit var network: MockNetwork
    protected lateinit var ownerNode: StartedMockNode
    protected lateinit var owner: Party

    @Before
    fun initial_setup() {
        network = MockNetwork(listOf("com.gatsinski.rems"), threadPerNode = true)
        ownerNode = network.createNode()
        owner = ownerNode.info.singleIdentity()
    }

    @After
    fun tearDown() {
        network.stopNodes()
    }

    protected fun registerRealEstate(): SignedTransaction {
        val flow = RegisterFlow.Initiator(address = address)
        return ownerNode.startFlow(flow).getOrThrow()
    }

    protected fun rentRealEstate(linearId: UniqueIdentifier, tenant: Party): SignedTransaction {
        val flow = RentFlow.Initiator(linearId = linearId, tenant = tenant)
        return ownerNode.startFlow(flow).getOrThrow()
    }

    protected fun terminateRealEstateRent(linearId: UniqueIdentifier): SignedTransaction {
        val flow = TerminateRentFlow.Initiator(linearId = linearId)
        return ownerNode.startFlow(flow).getOrThrow()
    }

    protected fun sellRealEstate(linearId: UniqueIdentifier, buyer: Party): SignedTransaction {
        val flow = SellFlow.Initiator(linearId = linearId, buyer = buyer)
        return ownerNode.startFlow(flow).getOrThrow()
    }
}



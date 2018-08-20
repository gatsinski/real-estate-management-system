package com.gatsinski.rems.flows

import com.gatsinski.rems.RealEstate
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.getOrThrow
import net.corda.testing.core.singleIdentity
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.StartedMockNode
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

class RegisterFlowTests {
    private val address = "City, Test Residential Quarter, Building 1, Entrance A, №1"
    private lateinit var network: MockNetwork
    private lateinit var ownerNode: StartedMockNode
    private lateinit var owner: Party

    @Before
    fun setup() {
        network = MockNetwork(listOf("com.gatsinski.rems"), threadPerNode = true)
        ownerNode = network.createNode()
        owner = ownerNode.info.singleIdentity()
    }

    @After
    fun tearDown() {
        network.stopNodes()
    }

    private fun registerRealEstate(
        ownerNode: StartedMockNode,
        address: String
    ): SignedTransaction {
        val flow = RegisterFlow.Initiator(address = address)
        return ownerNode.startFlow(flow).getOrThrow()
    }

    @Test
    fun `Real estate should be registered successfully`() {
        val signedTransaction = registerRealEstate(ownerNode, address)
        network.waitQuiescent()
        val state = ownerNode.services.loadState(
            signedTransaction.tx.outRef<RealEstate>(0).ref
        ).data as RealEstate

        assertEquals(state.owner, owner)
        assertEquals(state.address, address)
    }
}

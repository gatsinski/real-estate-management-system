package com.gatsinski.rems.flows

import com.gatsinski.rems.RealEstate
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.FlowException
import net.corda.core.identity.Party
import net.corda.core.node.services.queryBy
import net.corda.testing.core.singleIdentity
import net.corda.testing.node.StartedMockNode
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SellFlowTests : FlowTestBase() {
    protected lateinit var buyerNode: StartedMockNode
    protected lateinit var buyer: Party

    @Before
    fun setup() {
        buyerNode = network.createNode()
        buyer = buyerNode.info.singleIdentity()
    }

    @Test
    fun `Real estate sell should complete successfully`() {
        val registerTransaction = registerRealEstate()
        network.waitQuiescent()

        val inputState = registerTransaction.tx.outputStates.single() as RealEstate
        val sellTransaction = sellRealEstate(linearId = inputState.linearId, buyer = buyer)
        network.waitQuiescent()

        val outputState = sellTransaction.tx.outputStates.single() as RealEstate
        assertEquals(buyer, outputState.owner)
    }

    @Test
    fun `Real estate sell should be recorded by all parties`() {
        val signedTransaction = registerRealEstate()
        network.waitQuiescent()

        val inputState = signedTransaction.tx.outputStates.single() as RealEstate
        sellRealEstate(linearId = inputState.linearId, buyer = buyer)
        network.waitQuiescent()

        buyerNode.transaction {
            val buyerStates = buyerNode.services.vaultService.queryBy<RealEstate>().states
            assertEquals(1, buyerStates.size, "One state should be recorded")

            val realEstate = buyerStates.single().state.data
            assertEquals(buyer, realEstate.owner, "The new owner should be recorded")
        }

        ownerNode.transaction {
            val sellerStates = ownerNode.services.vaultService.queryBy<RealEstate>().states
            assertTrue(sellerStates.isEmpty(), "The old state should be consumed")
        }
    }

    @Test
    fun `Real estate sell flow should return a transaction signed by all parties`() {
        val registerTransaction = registerRealEstate()
        network.waitQuiescent()

        val inputState = registerTransaction.tx.outputStates.single() as RealEstate
        val sellTransaction = sellRealEstate(linearId = inputState.linearId, buyer = buyer)

        sellTransaction.verifyRequiredSignatures()
    }

    @Test
    fun `Real estate sell should fail if invalid linear ID is provided`() {
        assertFailsWith<FlowException> {
            sellRealEstate(linearId = UniqueIdentifier(), buyer = buyer)
        }
    }
}

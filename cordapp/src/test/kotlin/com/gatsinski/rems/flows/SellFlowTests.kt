package com.gatsinski.rems.flows

import com.gatsinski.rems.RealEstate
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.FlowException
import net.corda.core.identity.Party
import net.corda.core.node.services.queryBy
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.getOrThrow
import net.corda.testing.core.singleIdentity
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.StartedMockNode
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SellFlowTests {
    private val address = "City, Test Residential Quarter, Building 1, Entrance A, №1"
    private lateinit var network: MockNetwork
    private lateinit var sellerNode: StartedMockNode
    private lateinit var buyerNode: StartedMockNode
    private lateinit var seller: Party
    private lateinit var buyer: Party

    @Before
    fun setup() {
        network = MockNetwork(listOf("com.gatsinski.rems"), threadPerNode = true)
        sellerNode = network.createNode()
        buyerNode = network.createNode()
        seller = sellerNode.info.singleIdentity()
        buyer = buyerNode.info.singleIdentity()
    }

    @After
    fun tearDown() {
        network.stopNodes()
    }

    private fun registerRealEstate(): SignedTransaction {
        val flow = RegisterFlow.Initiator(address = address)
        return sellerNode.startFlow(flow).getOrThrow()
    }

    private fun sellRealEstate(linearId: UniqueIdentifier): SignedTransaction {
        val flow = SellFlow.Initiator(linearId = linearId, buyer = buyer)
        return sellerNode.startFlow(flow).getOrThrow()
    }

    @Test
    fun `Real estate sell should complete successfully`() {
        val registerTransaction = registerRealEstate()
        network.waitQuiescent()

        val inputState = registerTransaction.tx.outputStates.single() as RealEstate
        val sellTransaction = sellRealEstate(inputState.linearId)
        network.waitQuiescent()

        val outputState = sellTransaction.tx.outputStates.single() as RealEstate
        assertEquals(buyer, outputState.owner)
    }

    @Test
    fun `Real estate sell should be recorded by all parties`() {
        val signedTransaction = registerRealEstate()
        network.waitQuiescent()

        val inputState = signedTransaction.tx.outputStates.single() as RealEstate
        sellRealEstate(inputState.linearId)
        network.waitQuiescent()

        buyerNode.transaction {
            val buyerStates = buyerNode.services.vaultService.queryBy<RealEstate>().states
            assertEquals(1, buyerStates.size, "One state should be recorded")

            val realEstate = buyerStates.single().state.data
            assertEquals(buyer, realEstate.owner, "The new owner should be recorded")
        }

        sellerNode.transaction {
            val sellerStates = sellerNode.services.vaultService.queryBy<RealEstate>().states
            assertTrue(sellerStates.isEmpty(), "The old state should be consumed")
        }
    }

    @Test
    fun `Real estate sell flow should return a transaction signed by all parties`() {
        val registerTransaction = registerRealEstate()
        network.waitQuiescent()

        val inputState = registerTransaction.tx.outputStates.single() as RealEstate
        val sellTransaction = sellRealEstate(inputState.linearId)

        sellTransaction.verifyRequiredSignatures()
    }

    @Test
    fun `Real estate sell should fail if invalid linear ID is provided`() {
        assertFailsWith<FlowException> {
            sellRealEstate(linearId = UniqueIdentifier())
        }
    }
    
}

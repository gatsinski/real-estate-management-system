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

class RentFlowTests {
    private val address = "City, Test Residential Quarter, Building 1, Entrance A, №1"
    private lateinit var network: MockNetwork
    private lateinit var ownerNode: StartedMockNode
    private lateinit var tenantNode: StartedMockNode
    private lateinit var owner: Party
    private lateinit var tenant: Party

    @Before
    fun setup() {
        network = MockNetwork(listOf("com.gatsinski.rems"), threadPerNode = true)
        ownerNode = network.createNode()
        tenantNode = network.createNode()
        owner = ownerNode.info.singleIdentity()
        tenant = tenantNode.info.singleIdentity()
    }

    @After
    fun tearDown() {
        network.stopNodes()
    }

    private fun registerRealEstate(): SignedTransaction {
        val flow = RegisterFlow.Initiator(address = address)
        return ownerNode.startFlow(flow).getOrThrow()
    }

    private fun rentRealEstate(linearId: UniqueIdentifier): SignedTransaction {
        val flow = RentFlow.Initiator(linearId = linearId, tenant = tenant)
        return ownerNode.startFlow(flow).getOrThrow()
    }

    @Test
    fun `Real estate rent should complete successfully`() {
        val registerFlowSignedTransaction = registerRealEstate()
        network.waitQuiescent()

        val inputState = registerFlowSignedTransaction.tx.outputStates.single() as RealEstate
        val rentFlowSignedTransaction = rentRealEstate(linearId = inputState.linearId)
        network.waitQuiescent()

        val outputState = rentFlowSignedTransaction.tx.outputStates.single() as RealEstate
        assertEquals(tenant, outputState.tenant)
    }

    @Test
    fun `Real estate rent should be recorded by all parties`() {
        val signedTransaction = registerRealEstate()
        network.waitQuiescent()

        val inputState = signedTransaction.tx.outputStates.single() as RealEstate
        rentRealEstate(linearId = inputState.linearId)
        network.waitQuiescent()

        tenantNode.transaction {
            val sellerStates = tenantNode.services.vaultService.queryBy<RealEstate>().states
            assertEquals(1, sellerStates.size, "One state should be recorded")

            val realEstate = sellerStates.single().state.data
            assertEquals(tenant, realEstate.tenant, "The new tenant should be recorded")
        }

        ownerNode.transaction {
            val sellerStates = ownerNode.services.vaultService.queryBy<RealEstate>().states
            assertEquals(1, sellerStates.size, "One state should be recorded")

            val realEstate = sellerStates.single().state.data
            assertEquals(tenant, realEstate.tenant, "The new tenant should be recorded")
        }
    }

    @Test
    fun `Real estate rent flow should return a transaction signed by all parties`() {
        val registerFlowSignedTransaction = registerRealEstate()
        network.waitQuiescent()

        val inputState = registerFlowSignedTransaction.tx.outputStates.single() as RealEstate
        val rentFlowSignedTransaction = rentRealEstate(inputState.linearId)

        rentFlowSignedTransaction.verifyRequiredSignatures()
    }

    @Test
    fun `Real estate rent should fail if invalid linear ID is provided`() {
        assertFailsWith<FlowException> {
            rentRealEstate(linearId = UniqueIdentifier())
        }
    }
}

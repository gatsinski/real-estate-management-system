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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TerminateRentFlowTests {
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

    private fun terminateRentRealEstate(linearId: UniqueIdentifier): SignedTransaction {
        val flow = TerminateRentFlow.Initiator(linearId = linearId)
        return ownerNode.startFlow(flow).getOrThrow()
    }


    @Test
    fun `Real estate rent termination should complete successfully`() {
        val registerTransaction = registerRealEstate()
        network.waitQuiescent()

        val inputState = registerTransaction.tx.outputStates.single() as RealEstate

        rentRealEstate(linearId = inputState.linearId)
        network.waitQuiescent()

        val terminateRentTransaction = terminateRentRealEstate(linearId = inputState.linearId)
        network.waitQuiescent()

        val outputState = terminateRentTransaction.tx.outputStates.single() as RealEstate
        assertNull(outputState.tenant, "The tenant should be removed")
    }

    @Test
    fun `Real estate rent termination should be recorded by all parties`() {
        val registerTransaction = registerRealEstate()
        network.waitQuiescent()

        val inputState = registerTransaction.tx.outputStates.single() as RealEstate

        rentRealEstate(linearId = inputState.linearId)
        network.waitQuiescent()

        val terminateRentTransaction = terminateRentRealEstate(linearId = inputState.linearId)
        val transactionHash = terminateRentTransaction.id
        network.waitQuiescent()

        ownerNode.transaction {
            val ownerStates = ownerNode.services.vaultService.queryBy<RealEstate>().states
            assertEquals(1, ownerStates.size, "One state should be recorded")

            val transaction = ownerNode.services.validatedTransactions.getTransaction(transactionHash)
            assertNotNull(transaction)
        }

        tenantNode.transaction {
            val tenantStates = tenantNode.services.vaultService.queryBy<RealEstate>().states
            assertTrue(tenantStates.isEmpty(), "The old state should be consumed")
        }
    }

    @Test
    fun `Real estate rent termination flow should return a transaction signed by all parties`() {
        val registerTransaction = registerRealEstate()
        network.waitQuiescent()

        val inputState = registerTransaction.tx.outputStates.single() as RealEstate

        rentRealEstate(linearId = inputState.linearId)
        network.waitQuiescent()

        val terminateRentTransaction = terminateRentRealEstate(linearId = inputState.linearId)
        network.waitQuiescent()

        terminateRentTransaction.verifyRequiredSignatures()
    }

    @Test
    fun `Real estate rent termination should fail if invalid linear ID is provided`() {
        assertFailsWith<FlowException> {
            terminateRentRealEstate(linearId = UniqueIdentifier())
        }
    }
}

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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TerminateRentFlowTests : FlowTestBase() {
    protected lateinit var tenantNode: StartedMockNode
    protected lateinit var tenant: Party

    @Before
    fun setup() {
        tenantNode = network.createNode()
        tenant = tenantNode.info.singleIdentity()
    }

    @Test
    fun `Real estate rent termination should complete successfully`() {
        val linearId = prepareRentTermination(tenant = tenant)
        val terminateRentTransaction = terminateRealEstateRent(linearId = linearId)
        network.waitQuiescent()

        val outputState = terminateRentTransaction.tx.outputStates.single() as RealEstate
        assertNull(outputState.tenant, "The tenant should be removed")
    }

    @Test
    fun `Real estate rent termination should be recorded by all parties`() {
        val linearId = prepareRentTermination(tenant = tenant)
        val terminateRentTransaction = terminateRealEstateRent(linearId = linearId)
        network.waitQuiescent()
        val transactionHash = terminateRentTransaction.id

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
        val linearId = prepareRentTermination(tenant = tenant)
        val terminateRentTransaction = terminateRealEstateRent(linearId = linearId)
        network.waitQuiescent()

        terminateRentTransaction.verifyRequiredSignatures()
    }

    @Test
    fun `Real estate rent termination should fail if invalid linear ID is provided`() {
        assertFailsWith<FlowException> {
            terminateRealEstateRent(linearId = UniqueIdentifier())
        }
    }

    fun prepareRentTermination(tenant: Party): UniqueIdentifier {
        val registerTransaction = registerRealEstate()
        network.waitQuiescent()

        val inputState = registerTransaction.tx.outputStates.single() as RealEstate

        rentRealEstate(linearId = inputState.linearId, tenant = tenant)
        network.waitQuiescent()

        return inputState.linearId
    }
}

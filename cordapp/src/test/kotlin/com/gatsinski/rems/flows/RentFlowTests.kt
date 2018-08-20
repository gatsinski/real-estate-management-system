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

class RentFlowTests : FlowTestBase() {
    protected lateinit var tenantNode: StartedMockNode
    protected lateinit var tenant: Party

    @Before
    fun setup() {
        tenantNode = network.createNode()
        tenant = tenantNode.info.singleIdentity()
    }

    @Test
    fun `Real estate rent should complete successfully`() {
        val registerFlowSignedTransaction = registerRealEstate()
        network.waitQuiescent()

        val inputState = registerFlowSignedTransaction.tx.outputStates.single() as RealEstate
        val rentFlowSignedTransaction = rentRealEstate(linearId = inputState.linearId, tenant = tenant)
        network.waitQuiescent()

        val outputState = rentFlowSignedTransaction.tx.outputStates.single() as RealEstate
        assertEquals(tenant, outputState.tenant)
    }

    @Test
    fun `Real estate rent should be recorded by all parties`() {
        val signedTransaction = registerRealEstate()
        network.waitQuiescent()

        val inputState = signedTransaction.tx.outputStates.single() as RealEstate
        rentRealEstate(linearId = inputState.linearId, tenant = tenant)
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
        val rentFlowSignedTransaction = rentRealEstate(inputState.linearId, tenant = tenant)

        rentFlowSignedTransaction.verifyRequiredSignatures()
    }

    @Test
    fun `Real estate rent should fail if invalid linear ID is provided`() {
        assertFailsWith<FlowException> {
            rentRealEstate(linearId = UniqueIdentifier(), tenant = tenant)
        }
    }
}

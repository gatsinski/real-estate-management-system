package com.gatsinski.rems.flows

import com.gatsinski.rems.RealEstate
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.getOrThrow
import net.corda.testing.node.StartedMockNode
import org.junit.Test
import kotlin.test.assertEquals

class RegisterFlowTests : FlowTestBase() {
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

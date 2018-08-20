package com.gatsinski.rems.flows

import co.paralleluniverse.fibers.Suspendable
import com.gatsinski.rems.RealEstate
import com.gatsinski.rems.RealEstateContract
import net.corda.core.contracts.Command
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder

class RegisterFlow {

    @InitiatingFlow
    @StartableByRPC
    class Initiator(private val address: String) : FlowLogic<SignedTransaction>() {

        @Suspendable
        override fun call(): SignedTransaction {
            val notary = serviceHub.networkMapCache.notaryIdentities.first()
            val outputState = RealEstate(owner = ourIdentity, address = address)
            val command = Command(
                RealEstateContract.Commands.Register(),
                outputState.participants.map { it.owningKey })
            val transactionBuilder = TransactionBuilder(notary = notary)
                .addOutputState(outputState, RealEstateContract.PROGRAM_ID)
                .addCommand(command)

            transactionBuilder.verify(serviceHub)

            val fullySignedTransaction = serviceHub.signInitialTransaction(transactionBuilder)

            return subFlow(FinalityFlow(fullySignedTransaction))
        }

    }
}

package com.gatsinski.rems.flows

import co.paralleluniverse.fibers.Suspendable
import com.gatsinski.rems.RealEstate
import com.gatsinski.rems.RealEstateContract
import net.corda.core.contracts.Command
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.contracts.requireThat
import net.corda.core.flows.CollectSignaturesFlow
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.SignTransactionFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.node.services.Vault
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder

class SellFlow {

    @InitiatingFlow
    @StartableByRPC
    class Initiator(
        private val linearId: UniqueIdentifier,
        private val buyer: Party
    ) : FlowLogic<SignedTransaction>() {

        @Suspendable
        override fun call(): SignedTransaction {
            val notary = serviceHub.networkMapCache.notaryIdentities.first()
            val queryCriteria = QueryCriteria.LinearStateQueryCriteria(
                linearId = listOf(linearId),
                status = Vault.StateStatus.UNCONSUMED
            )
            val vaultPage = serviceHub.vaultService.queryBy<RealEstate>(queryCriteria)
            val inputStateAndRef = vaultPage.states.singleOrNull()
                ?: throw FlowException("Real estate with id $linearId is not found.")
            val inputState = inputStateAndRef.state.data

            val outputState = inputState.copy(owner = buyer)

            val signers = (inputState.participants union outputState.participants).map { it.owningKey }
            val command = Command(RealEstateContract.Commands.Sell(), signers)

            val transactionBuilder = TransactionBuilder(notary = notary)
                .addInputState(inputStateAndRef)
                .addOutputState(outputState, RealEstateContract.PROGRAM_ID)
                .addCommand(command)

            transactionBuilder.verify(serviceHub)
            val partiallySignedTransaction = serviceHub.signInitialTransaction(transactionBuilder)

            val sessions = (outputState.participants - ourIdentity).map { initiateFlow(it) }.toSet()

            val fullySignedTransaction = subFlow(CollectSignaturesFlow(partiallySignedTransaction, sessions))

            return subFlow(FinalityFlow(fullySignedTransaction))
        }
    }

    @InitiatedBy(Initiator::class)
    class Responder(private val counterPartySession: FlowSession) : FlowLogic<Unit>() {

        @Suspendable
        override fun call() {
            val signedTransactionFlow = object : SignTransactionFlow(counterPartySession) {
                override fun checkTransaction(stx: SignedTransaction) = requireThat {
                    val realEstate = stx.tx.outputStates.single()
                    "The output state must be a real estate" using (realEstate is RealEstate)
                }
            }

            subFlow(signedTransactionFlow)
        }

    }

}

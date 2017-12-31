/**
 * Project: STMv2
 * Package: stm.utils
 * File: Transactions.java
 * 
 * @author sidmishraw
 *         Last modified: Dec 18, 2017 4:12:20 PM
 */
package stm.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

import stm.STM;
import stm.Transaction;

/**
 * Utility class for building transactions or atomic blocks.
 * 
 * @author sidmishraw
 *
 *         Qualified Name: stm.utils.Transactions
 *
 */
public final class Transactions {
    
    /**
     * The transaction context for building transactions.
     * 
     * @author sidmishraw
     *
     *         Qualified Name: stm.utils.TransactionContext
     *
     */
    public static final class TransactionContext {
        
        /**
         * The transaction being built by the transaction context
         */
        private Transaction             t;
        
        /**
         * List of delayed actions that are to be added to the transaction after the transaction has been injected into
         * them.
         * 
         * Function signature = () -> Boolean
         * 
         * Internally, the functions call the actions of the transaction of the form
         * (Transaction t) -> Boolean
         */
        private List<Supplier<Boolean>> delayedActions;
        
        /**
         * Builds the transaction context for the given STM object.
         * 
         * @param stm
         *            the STM the transaction will operate upon.
         */
        public TransactionContext(STM stm) {
            this.t = new Transaction(stm);
        }
        
        /**
         * Signals the start of the transaction's action addition phase. The main reason for adding a method of this
         * name is to get a thenable `then` styled API. What I want is something like:
         * 
         * <p>
         * 
         * {@code
         *      Transactions.newT()
         *          .begin()
         *          .then((t) -> action2())
         *          .orElse((t) -> action3(), (t) -> action4()) // for choice
         *          ...
         *          .end()
         *          .done();
         * }
         * 
         * <p>
         * 
         * See also {@linkplain TransactionContext#then(Function)} and {@linkplain TransactionContext#end} for more
         * information.
         * 
         * See also {@linkplain TransactionContext#begin(Function)}
         * 
         * @param action
         *            the initial action of the transaction
         * @return the updated transaction context
         */
        public TransactionContext begin() {
            this.delayedActions = new ArrayList<>();
            return this;
        }
        
        /**
         * <p>
         * Signals the start of the transaction's action addition phase. The main reason for adding a method of this
         * name is to get a thenable `then` styled API. What I want is something like:
         * </p>
         * 
         * <p>
         * This is a shorthand for adding the first action, good for transactions with only one action.
         * </p>
         * 
         * <p>
         * 
         * {@code
         *      Transactions.newT()
         *          .begin((t) -> action1())
         *          .then((t) -> action2())
         *          ...
         *          .end()
         *          .done();
         * }
         * 
         * <p>
         * 
         * See also {@linkplain TransactionContext#then(Function)} and {@linkplain TransactionContext#end} for more
         * information.
         * 
         * @param action
         *            the initial action of the transaction
         * @return the updated transaction context
         */
        public TransactionContext begin(Function<Transaction, Boolean> action) {
            this.delayedActions = new ArrayList<>();
            this.delayedActions.add(() -> action.apply(this.t)); // () -> action.apply(this.t)
            return this;
        }
        
        /**
         * Adds an action to the transaction being constructed. Needs to be chained after
         * {@linkplain TransactionContext#begin(Function)} has been invoked.
         * 
         * All actions added are executed in order - sequentially for the particular transaction
         * 
         * <p>
         * See {@linkplain TransactionContext#begin(Function)} for more information.
         * 
         * @param action
         *            the action to be added
         * @return the updated transaction context
         */
        public TransactionContext then(Function<Transaction, Boolean> action) {
            this.delayedActions.add(() -> action.apply(this.t));
            return this;
        }
        
        /**
         * <p>
         * Signals the choice operation. If the first action fails, the second action is tried. If both fail, the
         * transaction is aborted.
         * </p>
         * 
         * @param fstChoice
         *            The first action, if succeeds, it is the final result.
         * @param sndChoice
         *            The second action that is tried if the first action fails.
         * @return The updated transaction context.
         */
        public TransactionContext orElse(Function<Transaction, Boolean> fstChoice,
                Function<Transaction, Boolean> sndChoice) {
            /**
             * <p>
             * In order to achieve this, I make a bigger action that when performed, first performs the fstChoice. If
             * fstChoice is successful, the transaction carries on as usual skipping the sndChoice. Otherwise, it
             * performs the sndChoice. If the action is successful, the transaction carries on as usual else it aborts.
             * </p>
             */
            this.delayedActions.add(() -> {
                boolean fstStatus = fstChoice.apply(this.t);
                if (fstStatus)
                    return true;
                else
                    return sndChoice.apply(this.t);
            });
            return this;
        }
        
        /**
         * Signals the end of all the actions to be performed by the transaction.
         * This method is also responsible for constructing the transaction from the information contained in the
         * {@linkplain TransactionContext}.
         * 
         * @return the updated transaction context
         */
        public TransactionContext end() {
            this.t.setActions(delayedActions);
            return this;
        }
        
        /**
         * Gives the fully constructed transaction object that can be passed around.
         * 
         * @return the fully constructed transaction.
         */
        public Transaction done() {
            return this.t;
        }
    }
    
    /**
     * Starts a new transaction context for defining a transaction
     * 
     * @param stm
     *            the STM the transaction intends to operate upon.
     * @return a new transaction context
     */
    public static TransactionContext newT(STM stm) {
        return new TransactionContext(stm);
    }
}

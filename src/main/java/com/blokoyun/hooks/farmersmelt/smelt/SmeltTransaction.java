package com.blokoyun.hooks.farmersmelt.smelt;

import java.util.List;

/** Stock is debited before rewards. Arbitrary command side effects cannot be rolled back. */
public final class SmeltTransaction {
    public interface Stock {
        int sourceAmount();
        int destinationSpace();
        void debit(int amount) throws ReflectiveOperationException;
        void credit(int amount) throws ReflectiveOperationException;
        void restore() throws ReflectiveOperationException;
    }

    public interface Commands {
        boolean dispatch(String command);
    }

    public enum Result { COMPLETED, INSUFFICIENT_STOCK, DESTINATION_FULL, COMMAND_FAILED }

    private SmeltTransaction() { }

    public static Result complete(Stock stock, ItemRule.RuleMode mode, int amount, List<String> commands,
                           Commands dispatcher) throws ReflectiveOperationException {
        if (amount <= 0 || stock.sourceAmount() < amount) {
            return Result.INSUFFICIENT_STOCK;
        }
        if (mode == ItemRule.RuleMode.PLUGIN && stock.destinationSpace() < amount) {
            return Result.DESTINATION_FULL;
        }
        if (mode == ItemRule.RuleMode.COMMANDS && commands.isEmpty()) {
            return Result.COMMAND_FAILED;
        }
        try {
            stock.debit(amount);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            try { stock.restore(); } catch (ReflectiveOperationException | RuntimeException restore) {
                ex.addSuppressed(restore);
            }
            throw ex;
        }
        if (mode == ItemRule.RuleMode.PLUGIN) {
            try {
                stock.credit(amount);
            } catch (ReflectiveOperationException | RuntimeException ex) {
                try { stock.restore(); } catch (ReflectiveOperationException | RuntimeException restore) {
                    ex.addSuppressed(restore);
                }
                throw ex;
            }
            return Result.COMPLETED;
        }
        for (String command : commands) {
            try {
                if (!dispatcher.dispatch(command)) {
                    // Commands may already have changed the player inventory. Never refund
                    // the source after an arbitrary command has started.
                    return Result.COMMAND_FAILED;
                }
            } catch (RuntimeException ex) {
                return Result.COMMAND_FAILED;
            }
        }
        return Result.COMPLETED;
    }
}

package com.blokoyun.hooks.farmersmelt.smelt;
import com.blokoyun.hooks.farmersmelt.integration.FarmerStorage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
class SmeltTransactionTest {
    @Test
    void pluginModeMovesStock() throws Exception {
        FakeStock stock = new FakeStock(8, 2, 10);

        SmeltTransaction.Result result = SmeltTransaction.complete(stock, ItemRule.RuleMode.PLUGIN,
                3, List.of(), command -> true);

        assertEquals(SmeltTransaction.Result.COMPLETED, result);
        assertEquals(5, stock.sourceAmount());
        assertEquals(5, stock.destinationAmount());
    }

    @Test
    void failedCommandDoesNotRefundAfterSideEffect() throws Exception {
        FakeStock stock = new FakeStock(8, 0, 0);

        SmeltTransaction.Result result = SmeltTransaction.complete(stock, ItemRule.RuleMode.COMMANDS,
                3, List.of("first", "second"), command -> command.equals("first"));

        assertEquals(SmeltTransaction.Result.COMMAND_FAILED, result);
        assertEquals(5, stock.sourceAmount(), "komut yan etkisi geri alınamadığı için stok iade edilmez");
    }

    @Test
    void insufficientStockDoesNotDispatch() throws Exception {
        FakeStock stock = new FakeStock(1, 0, 10);
        SmeltTransaction.Result result = SmeltTransaction.complete(stock, ItemRule.RuleMode.COMMANDS,
                2, List.of("give"), command -> { throw new AssertionError("dispatch edilmemeliydi"); });
        assertEquals(SmeltTransaction.Result.INSUFFICIENT_STOCK, result);
        assertEquals(1, stock.sourceAmount());
    }

    @Test
    void emptyCommandsDoNotConsume() throws Exception {
        FakeStock stock = new FakeStock(4, 0, 10);
        assertEquals(SmeltTransaction.Result.COMMAND_FAILED,
                SmeltTransaction.complete(stock, ItemRule.RuleMode.COMMANDS, 2, List.of(), command -> true));
        assertEquals(4, stock.sourceAmount());
    }

    @Test
    void fullDestinationDoesNotConsume() throws Exception {
        FakeStock stock = new FakeStock(4, 10, 10);
        assertEquals(SmeltTransaction.Result.DESTINATION_FULL,
                SmeltTransaction.complete(stock, ItemRule.RuleMode.PLUGIN, 2, List.of(), command -> true));
        assertEquals(4, stock.sourceAmount());
    }

    @Test
    void failedCreditRestoresSourceEvenIfDestinationRestoreFails() {
        FarmerStorage.StockSnapshot snapshot = new FarmerStorage.StockSnapshot(
                new FailingItem(3, false), 3, new FailingItem(0, true), 0, 10);

        assertThrows(ReflectiveOperationException.class, () ->
                SmeltTransaction.complete(snapshot, ItemRule.RuleMode.PLUGIN, 3, List.of(), command -> true));

        assertEquals(3, snapshot.sourceAmount(), "destination restore failure must not drop source refund");
    }

    private static final class FailingItem {
        private int amount;
        private final boolean fail;
        private FailingItem(int amount, boolean fail) { this.amount = amount; this.fail = fail; }
        public void setAmount(int value) {
            if (fail) throw new IllegalStateException("destination failure");
            this.amount = value;
        }
        public int getAmount() { return amount; }
    }

    private static final class FakeStock implements SmeltTransaction.Stock {
        private int source;
        private int destination;
        private final int capacity;

        private FakeStock(int source, int destination, int capacity) {
            this.source = source;
            this.destination = destination;
            this.capacity = capacity;
        }

        @Override public int sourceAmount() { return source; }
        @Override public int destinationSpace() { return capacity - destination; }
        @Override public void debit(int amount) { source -= amount; }
        @Override public void credit(int amount) { destination += amount; }
        @Override public void restore() { source = 8; destination = 2; }
        private int destinationAmount() { return destination; }
    }
}

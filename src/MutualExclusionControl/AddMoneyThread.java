package MutualExclusionControl;

public class AddMoneyThread implements Runnable {
    private MutualExclusionSemaphore mutualExclusionSemaphore;
    private Bank bank;
    private int addMoneyCount;
    public int id;

    public AddMoneyThread(Bank bank, int addMoneyCount, int id, MutualExclusionSemaphore mutualExclusionSemaphore) {
        this.bank = bank;
        this.addMoneyCount = addMoneyCount;
        this.id = id;
        this.mutualExclusionSemaphore = mutualExclusionSemaphore;
    }

    @Override
    public void run() {
        if (bank != null) {
            int index = 1;
            while (index <= this.addMoneyCount) {
                mutualExclusionSemaphore.P();
                bank.addMoney();
                index++;
                mutualExclusionSemaphore.V();
            }
        }
    }
}

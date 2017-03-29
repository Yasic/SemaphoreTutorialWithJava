package MutualExclusionControl;

public class MutualExclusion {

    public static void main(String[] args) {
        for (int i = 0; i < 5; i++) {
            Bank bank = new Bank(10000);
            MutualExclusionSemaphore mutualExclusionSemaphore = new MutualExclusionSemaphore(1);
            new Thread(new AddMoneyThread(bank, 10000, 1, mutualExclusionSemaphore)).start();
            new Thread(new AddMoneyThread(bank, 10000, 2, mutualExclusionSemaphore)).start();
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                System.out.println("第" + (i + 1) + "次" + bank.readMoney());
            }
        }
    }
}

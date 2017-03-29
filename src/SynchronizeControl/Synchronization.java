package SynchronizeControl;

import java.util.ArrayList;
import java.util.List;

public class Synchronization {
    public static void main(String[] args) {
        for (int i = 0; i < 5; i++){
            Repository repository = new Repository(10);
            SynchronizationSemaphore synchronizationSemaphore = new SynchronizationSemaphore(10, 0);
            List<Goods> goodsList = new ArrayList<>();
            Thread saveGood = new Thread(new SaveGoodThread(repository, 10000, synchronizationSemaphore));
            Thread takeGood = new Thread(new TakeGoodThread(repository, goodsList, synchronizationSemaphore));
            saveGood.start();
            takeGood.start();

            try {
                Thread.sleep(2000);
                saveGood.suspend();
                takeGood.suspend();
                System.out.println("第" + (i + 1) + "次" + goodsList.size());
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}

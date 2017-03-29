package SynchronizeControl;

import java.util.List;

public class TakeGoodThread implements Runnable {
    private int number = 0;
    private List<Goods> goodsList = null;
    private Repository repository;
    private SynchronizationSemaphore synchronizationSemaphore;

    public TakeGoodThread(Repository repository, List<Goods> goodsList, SynchronizationSemaphore synchronizationSemaphore) {
        this.repository = repository;
        this.synchronizationSemaphore = synchronizationSemaphore;
        this.goodsList = goodsList;
    }

    @Override
    public void run() {
        while (true) {
            synchronizationSemaphore.PFull();
            synchronizationSemaphore.PMutex();
            Goods temp = repository.takeGood();
            number++;
            if (temp != null){
                goodsList.add(temp);
                synchronizationSemaphore.VEmpty();
            }
            synchronizationSemaphore.VMutex();
        }
    }
}

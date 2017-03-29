package SynchronizeControl;

public class SaveGoodThread implements Runnable{
    private Repository repository;
    private int saveNumber = 0;
    protected SynchronizationSemaphore synchronizationSemaphore;

    public SaveGoodThread(Repository repository, int saveNumber, SynchronizationSemaphore synchronizationSemaphore){
        this.repository = repository;
        this.saveNumber = saveNumber;
        this.synchronizationSemaphore = synchronizationSemaphore;
    }

    @Override
    public void run(){
        if (repository != null){
            int i = 0;
            while (i < this.saveNumber){
                synchronizationSemaphore.PEmpty();
                synchronizationSemaphore.PMutex();
                repository.saveGood(i + 1);
                i++;
                synchronizationSemaphore.VMutex();
                synchronizationSemaphore.VFull();
            }
        }
    }
}

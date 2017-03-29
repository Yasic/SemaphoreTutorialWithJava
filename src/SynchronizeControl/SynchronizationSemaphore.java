package SynchronizeControl;

public class SynchronizationSemaphore {
    private static int idle = 0;
    private static int used = 0;
    private static int mutex = 1;

    public SynchronizationSemaphore(int idle, int used){
        this.idle = idle;
        this.used = used;
    }

    public synchronized void PEmpty(){
        idle--;
        if (idle < 0){
            try {
                this.wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public synchronized void VEmpty(){
        idle++;
        if (idle <= 0){
            notify();
        }
    }

    public synchronized void PFull(){
        used--;
        if (used < 0){
            try {
                this.wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public synchronized void VFull(){
        used++;
        if (used <= 0){
            notify();
        }
    }

    public synchronized void PMutex(){
        mutex--;
        if (mutex < 0){
            try {
                this.wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public synchronized void VMutex(){
        mutex++;
        if (mutex <= 0){
            notify();
        }
    }
}

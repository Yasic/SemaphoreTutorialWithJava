package MutualExclusionControl;

public class MutualExclusionSemaphore {
    private static int S = 1;

    public MutualExclusionSemaphore(int S) {
        this.S = S;
    }

    public synchronized void P(){
        S--;
        if (S < 0){
            try {
                this.wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public synchronized void V(){
        S++;
        if (S <= 0){
            notify();
        }
    }
}

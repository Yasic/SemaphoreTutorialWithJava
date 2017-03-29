package MutualExclusionControl;

public class Bank {
    private int money = 0;

    public Bank(int money){
        this.money = money;
    }

    public void addMoney(){
        this.money++;
    }

    public int readMoney(){
        return this.money;
    }
}

package SynchronizeControl;

public class Repository {
    private Goods[] goodsArray;
    private int usedSize = 0;

    public Repository(int goodSize){
        goodsArray = new Goods[goodSize];
    }

    public void saveGood(int goodId) {
        if (usedSize < goodsArray.length) {
            goodsArray[usedSize] = new Goods(goodId);
            usedSize++;
        }
    }

    public Goods takeGood(){
        if (usedSize == 0){
            return null;
        }
        return goodsArray[--usedSize];
    }
}

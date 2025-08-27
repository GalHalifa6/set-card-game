package bguspl.set.ex;

import java.util.Vector;

public class TokensVector implements Comparable<TokensVector>{
    private Vector<Integer> slots;
    private final int capacity;
    private volatile boolean mustRemove;
    private final boolean isInterrupting;
    private int[] validationArr;
    private final int pId;
    private volatile boolean terminate;
    public TokensVector(){
        isInterrupting = true;
        capacity=0;
        this.pId=-1;
        terminate = false;
    }
    public TokensVector(int cap,int pId){
        this.pId = pId;
        slots = new Vector<>();
        capacity=cap;
        mustRemove = false;
        isInterrupting=false;
        terminate=false;
    }
    public int getPlayerId(){
        return pId;
    }
    public synchronized int[] getArr(){
        int[] ret = new int[capacity];
        for (int i=0;i<slots.size();i++){
            ret[i]=slots.get(i);
        }
        return ret;
    }
    public synchronized boolean addOrRemoveToken(Integer slot){
        if (capacity==slots.size()||slots.contains(slot)){
            slots.remove(slot);
            mustRemove=false;
            return false;
        }
        else{
            slots.add(slot);
            return true;
        }
    }
    public synchronized boolean isFull(){
        return capacity==slots.size();
    }
    public synchronized void setMustRemove(boolean condition){
        mustRemove=condition;
    }
    public synchronized boolean isMustRemove(){
        return mustRemove;
    }
    public boolean isInterrupting(){
        return isInterrupting;
    }

    public void setValidation(int[] validate) {
        validationArr = validate;
    }
    public int[] getValidation(){
        return validationArr;
    }

    public void clear() {
        slots=new Vector<>();
    }

    public boolean contains(int slot) {
        return slots.contains(slot);
    }

    @Override
    public int compareTo(TokensVector other) {
        if(this.isInterrupting && !other.isInterrupting) return 1;
        else if ((!this.isInterrupting&&!other.isInterrupting)||(this.isInterrupting&&other.isInterrupting)) return 0;
        else return -1;
    }

    public synchronized boolean isNotValid() {
        return slots.isEmpty();
    }
    public void shutDown() {
        terminate = true;
    }
    public boolean terminate(){
        return terminate;
    }
   
}


package bguspl.set.ex;

import bguspl.set.Env;

import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * This class manages the players' threads and data
 *
 * @inv id >= 0
 * @inv score >= 0
 */
public class Player implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;

    /**
     * The id of the player (starting from 0).
     */
    public final int id;

    /**
     * The thread representing the current player.
     */
    private Thread playerThread;

    /**
     * The thread of the AI (computer) player (an additional thread used to generate key presses).
     */
    private Thread aiThread;

    /**
     * True iff the player is human (not a computer player).
     */
    private final boolean human;

    /**
     * True iff game should be terminated.
     */
    private volatile boolean terminate;

    /**
     * The current score of the player.
     */
    private int score;

    private BlockingQueue<Integer> keyPressedQueue;
    private TokensVector tokens;
    private BlockingQueue<Integer[]> checkPointPenaltyOrRemove;
    private volatile boolean is_waiting;
    private volatile boolean is_not_completed_set;
    private PlayerLocker playerLocker;
    private boolean is_sleeping;
    /**
     * The class constructor.
     *
     * @param env    - the environment object.
     * @param dealer - the dealer object.
     * @param table  - the table object.
     * @param id     - the id of the player.
     * @param human  - true iff the player is a human player (i.e. input is provided manually, via the keyboard).
     */
    public Player(Env env, Dealer dealer, Table table, int id, boolean human) {
        this.env = env;
        this.table = table;
        this.id = id;
        this.human = human;
        keyPressedQueue=new LinkedBlockingQueue<>(env.config.featureSize);
        checkPointPenaltyOrRemove = new LinkedBlockingQueue<>(1);
        tokens = new TokensVector(env.config.featureSize,id);
        is_waiting = false;
        is_not_completed_set = true;
    }

    /**
     * The main player thread of each player starts here (main loop for the player thread).
     */
    @Override
    public void run() {
        playerThread = Thread.currentThread();
        env.logger.info("Thread " + Thread.currentThread().getName() + " starting.");
        if (!human) createArtificialIntelligence();

        while (!terminate) {
            // TODO implement main player loop
            while((!tokens.isFull()||tokens.isMustRemove())&&!tokens.terminate()) {
                try {waitForKeyPressed();} catch (InterruptedException e) {throw new RuntimeException(e);}
            }
            if(!tokens.terminate()){
                is_not_completed_set=false;
                try {table.addSetForCheck(tokens);} catch (InterruptedException e) {throw new RuntimeException(e);}
                try {waitForDealerCheck();} catch (InterruptedException e) {throw new RuntimeException(e);}
                is_not_completed_set=true;
            }
            
        }
        if (!human) try { aiThread.join(); } catch (InterruptedException ignored) {}
        env.logger.info("Thread " + Thread.currentThread().getName() + " terminated.");
        table.shutMeDown(id);
    }

    private void waitForKeyPressed() throws InterruptedException {
       
        int slot=keyPressedQueue.take();
        if(slot!=-1){
       if(tokens.addOrRemoveToken(slot)){
           table.placeToken(id,slot);
     }
        else{
            table.removeToken(id,slot);
        }
    }
}

    
    
    
    
   
    /**
     * This method is called when a key is pressed.
     *
     * @param slot - the slot corresponding to the key pressed.
     */
    public void keyPressed(int slot) {
        // TODO implement
        if((!tokens.isFull()||(tokens.isMustRemove()&&tokens.contains(slot)))&&(!playerLocker.isSlotLocked(slot))&&(!playerLocker.isLocked(id))&&!is_sleeping){
            if(!tokens.terminate())
                try{keyPressedQueue.put(slot);} catch (InterruptedException e) {throw new RuntimeException(e);}
            //else
                //try {
                //    keyPressedQueue.take();
                //} catch (InterruptedException e) {
                    // TODO Auto-generated catch block
                 //   e.printStackTrace();
                //}
        }
    }
    private void waitForDealerCheck() throws InterruptedException {
        if(!terminate){
        Integer[] condition = checkPointPenaltyOrRemove.take();
        is_waiting=true;
        if (Arrays.stream(condition).allMatch((x)->x==-1)) penalty();
        else if(Arrays.stream(condition).allMatch((x)->x==-2)) point();
        else {
            tokens.setMustRemove(true);
            for (int slot:condition){
                if (slot!=-1){
                    tokens.addOrRemoveToken(slot);
                    table.removeToken(id,slot);
                }
            }
            is_waiting=false;
        }
    }
    }
    /**
     * Creates an additional thread for an AI (computer) player. The main loop of this thread repeatedly generates
     * key presses. If the queue of key presses is full, the thread waits until it is not full.
     */
    private void createArtificialIntelligence() {
        // note: this is a very, very smart AI (!)
        Random rnd = new Random();
        aiThread = new Thread(() -> {
            env.logger.info("Thread " + Thread.currentThread().getName() + " starting.");
            while (!terminate) {
                // TODO implement player key press simulator
                keyPressed(rnd.nextInt(env.config.tableSize));
            }
            env.logger.info("Thread " + Thread.currentThread().getName() + " terminated.");
        }, "computer-" + id);
        aiThread.start();
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        terminate = true;
        tokens.shutDown();
        consume();
        try {
            keyPressedQueue.put(-1);
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        Integer[] penalty = new Integer[env.config.featureSize];
                Arrays.fill(penalty, -1);
                try {
                    putCondition(penalty);
                } catch (InterruptedException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }        
        // TODO implement
    }


    /**
     * Award a point to a player and perform other related actions.
     *
     * @post - the player's score is increased by 1.
     * @post - the player's score is updated in the ui.
     */
    public void point(){
        // TODO implement

        int ignored = table.countCards(); // this part is just for demonstration in the unit tests
        env.ui.setScore(id, ++score);
        for (int slot:tokens.getArr()){
            table.removeToken(id,slot);
        }
        tokens.clear();
        if (env.config.pointFreezeMillis>0)
            sleep(env.config.pointFreezeMillis);
    }

    /**
     * Penalize a player and perform other related actions.
     */
    public void penalty() {
        // TODO implement
        tokens.setMustRemove(true);
        if (env.config.penaltyFreezeMillis>0)
            sleep(env.config.penaltyFreezeMillis);
    }
    private void sleep(long time_to_sleep){
        is_sleeping=true;
        while (time_to_sleep>0){
            env.ui.setFreeze(id,time_to_sleep);
            try{Thread.sleep(1000);} catch (InterruptedException e) {throw new RuntimeException(e);}
            time_to_sleep=time_to_sleep-1000;
        }
        env.ui.setFreeze(id,0);
        is_sleeping=false;
    }
    public int score() {
        return score;
    }

    public void removeSlot(int slot) {
        if (!tokens.contains(slot))
            tokens.addOrRemoveToken(slot);
        tokens.addOrRemoveToken(slot);
        table.removeToken(id,slot);
    }
    public void putCondition(Integer[] condition) throws InterruptedException {
        try{checkPointPenaltyOrRemove.put(condition);} catch (InterruptedException e) {throw new InterruptedException();}
    }

    public void reset() {
        tokens.clear();
    }

    public boolean isWaiting() {
        return is_waiting;
    }

    public boolean isNotCompleteSet() {
        return is_not_completed_set;
    }

    public void setLocker(PlayerLocker dealer) {
        playerLocker = dealer;
    }
    private void consume(){
        while (!keyPressedQueue.isEmpty()){
            try {
                keyPressedQueue.take();
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }
}

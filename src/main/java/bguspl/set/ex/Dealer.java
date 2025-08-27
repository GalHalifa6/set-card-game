package bguspl.set.ex;

import bguspl.set.Env;


import javax.swing.Timer;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * This class manages the dealer's threads and data
 */
public class Dealer implements Runnable,PlayerLocker {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;
    private final Player[] players;

    /**
     * The list of card ids that are left in the dealer's deck.
     */
    private final List<Integer> deck;

    /**
     * True iff game should be terminated.
     */
    private volatile boolean terminate;

    /**
     * The time when the dealer needs to reshuffle the deck due to turn timeout.
     */
    private long reshuffleTime = Long.MAX_VALUE;
    Thread dealerThread = Thread.currentThread();
    private boolean[] is_player_locked;
    private boolean[] is_slot_locked;
    boolean shouldReshuffle;
    Vector<Integer> shutDown;
    boolean is_busy;
    //gameMode
    private enum GameMode {Countdown,CountFromReshuffle,NoCount}
    GameMode gameMode;
    //time
    Timer timerSec;
    Timer timerMs;
    int curr_time_ms;
    boolean warn_time;

    Random rnd;

    Thread[] playerThreads;
    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
        is_slot_locked = new boolean[env.config.tableSize];
        Arrays.fill(is_slot_locked,true);
        is_player_locked = new boolean[env.config.players];
        Arrays.fill(is_player_locked,true);
        //timers missions init
        timerSec = new Timer(1000, e -> {
            curr_time_ms +=1000;
            try {updateTimerDisplay();} catch (InterruptedException ex) {throw new RuntimeException(ex);}
            if (reshuffleTime-curr_time_ms <= env.config.turnTimeoutWarningMillis) {
                warn_time = true;
                timerSec.stop();
                timerMs.start();
            }
        });
        timerMs = new Timer(10, e -> {
            curr_time_ms +=10;
            try {updateTimerDisplay();} catch (InterruptedException ex) {throw new RuntimeException(ex);}
        });
        //gamemode
        switch ((int) env.config.turnTimeoutMillis) {
            case (0): {
                gameMode = GameMode.CountFromReshuffle;
                reshuffleTime = Long.MAX_VALUE;
                break;
            }
            case (-1000): {
                gameMode = GameMode.NoCount;
                reshuffleTime = Long.MAX_VALUE;
                break;
            }
            default: {
                gameMode = GameMode.Countdown;
                reshuffleTime = env.config.turnTimeoutMillis;
            }
        }
        shouldReshuffle=false;
        rnd = new Random();
        playerThreads = new Thread[env.config.players];
        is_busy=true;
    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        env.logger.info("Thread " + Thread.currentThread().getName() + " starting.");
        for(int i=0; i<env.config.players;i++){
            players[i].setLocker(this);
            playerThreads[i] = new Thread(players[i],"player"+players[i].id+" thread");
            playerThreads[i].start();
        }
        while (!shouldFinish()) {
            placeCardsOnTable();
            shouldReshuffle=false;
            for (Player player : players){
                player.reset();
            }
            curr_time_ms=0;
            warn_time=false;
            timerSec.start();
            try {updateTimerDisplay();} catch (InterruptedException ex) {throw new RuntimeException(ex);}
            unlockPlayers();
            try {timerLoop();} catch (InterruptedException e) {throw new RuntimeException(e);}
            removeAllCardsFromTable();
        }
        if(!terminate||gameMode!=GameMode.Countdown){
            announceWinners();
            is_busy=false;
            for (int i = env.config.players-1 ; i>=0;i--){
                players[i].terminate();
            }
        }
        shutDown = new Vector<>();
        while(!players_dead()){
            try {
                shutDown.add(table.shutDown.take());
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }       
    }
        env.logger.info("Thread " + Thread.currentThread().getName() + " terminated.");
    }

    private boolean players_dead() {
        return shutDown.size()==env.config.players;
    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did not time out.
     */
    private void timerLoop() throws InterruptedException {
        while ((!terminate && curr_time_ms < reshuffleTime) && !shouldReshuffle) {
            sleepUntilWokenOrTimeout();
            placeCardsOnTable();
            unlockPlayers();
        }
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        while (is_busy())
            try {
                Thread.sleep(50);
            } catch (InterruptedException e1) {
                // TODO Auto-generated catch block
                e1.printStackTrace();
            }
        System.out.println("here2");
        // TODO implement
        for (int i = env.config.players-1 ; i>=0;i--){
            players[i].terminate();
        }
        terminate = true;
        try {
            table.setsOfThree.put(new TokensVector());
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        System.out.println("here4");    
        }


    private boolean is_busy() {
        return is_busy;
    }

    /**
     * Check if the game should be terminated or the game end conditions are met.
     *
     * @return true iff the game should be finished.
     */
    private boolean shouldFinish() {
        return terminate || env.util.findSets(deck, 1).size() == 0;
    }

    /**
     * Checks cards should be removed from the table and removes them.
     */
    private void removeCardsFromTable(int[] slots) {
        // TODO implement
        is_busy=true;
        for(int slot:slots){
            lockSlot(slot);
            table.removeCard(slot);
            for (Player player:players){
                if(player.isNotCompleteSet())
                    player.removeSlot(slot);
            }
        }
    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {
        // TODO implement
        int[] empty_slots = new int[env.config.tableSize-table.countCards()];
        int j=0;
        for (int i=0;i<env.config.tableSize;i++){
            if (table.slotToCard[i]==null){
                empty_slots[j] = i;
                j++;
            }
        }
        for (int slot:shuffleTableSlots(empty_slots)){
            if (deck.size()>0)
                {table.placeCard(pickAcard(),slot);
                unlockSlot(slot);}
        }
            if (gameMode!=GameMode.Countdown){
                if (!shouldFinish()) {
                    List<Integer> testTable = new ArrayList<>(Arrays.asList(table.slotToCard));
                    List<int[]> sets = env.util.findSets(testTable, 1);
                    if (sets.isEmpty()) {
                        shouldReshuffle = true;
                    }
                }
                else {
                    terminate=true;
                    timerSec.stop();
                }
                
        }
        is_busy=false;
    }

    private void unlockSlot(int slot) {
        is_slot_locked[slot]=false;
    }

    private int[] shuffleTableSlots(int[] empty_slots) {
        int rnd_int;
        for (int i=0;i<empty_slots.length;i++){
            rnd_int=rnd.nextInt(empty_slots.length);
            int temp=empty_slots[i];
            empty_slots[i]=empty_slots[rnd_int];
            empty_slots[rnd_int]=temp;
        }
        return empty_slots;
    }
    private int pickAcard() {
        return deck.remove(rnd.nextInt(deck.size()));
    }


    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
     */
    private void sleepUntilWokenOrTimeout() throws InterruptedException {
        // TODO implement
        TokensVector set = table.setsOfThree.take();
        if(!set.isInterrupting()) {
            lockPlayer(set.getPlayerId());
            checkPlayerSet(set);
        }
    }

    private void checkPlayerSet(TokensVector set) throws InterruptedException {
        if (set.isNotValid()) players[set.getPlayerId()].putCondition(new Integer[]{});
        else{
        Integer[] validation = checkValidation(set);
        if (Arrays.stream(validation).allMatch((x)->x==-1)){
            //validation succeeded
            int[] slots = set.getArr();
            if(env.util.testSet(slotsToCards(slots))){
                //point
                Integer[] point = new Integer[env.config.featureSize];
                Arrays.fill(point, -2);
                players[set.getPlayerId()].putCondition(point);
                removeCardsFromTable(slots);
                curr_time_ms=0;
                timerMs.stop();
                timerSec.start();
            }
            else{
                //penalty
                Integer[] penalty = new Integer[env.config.featureSize];
                Arrays.fill(penalty, -1);
                players[set.getPlayerId()].putCondition(penalty);
            }
        }
        else {
            //check validation fails and should return the player which token to remove
            players[set.getPlayerId()].putCondition(validation);
        }
    }
    }
    private Integer[] checkValidation(TokensVector tokensVector){
        int[] validation = tokensVector.getValidation();
        int[] slots = tokensVector.getArr();
        Integer[] ret = {-1,-1,-1};
        for(int i=0;i<validation.length;i++){
            if (table.slotToCard[slots[i]]==null||table.slotToCard[slots[i]]!=validation[i])
                    ret[i]=slots[i];
        }
        return ret;
    }
    private int[] slotsToCards(int[] queue) {
        int[] cards = new int[queue.length];
        for(int i=0;i<queue.length;i++){
            cards[i] = table.slotToCard[queue[i]];
        }
        return cards;
    }

    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private synchronized void updateTimerDisplay() throws InterruptedException {
        // TODO implement
        if (gameMode == GameMode.Countdown) {
            long countdown = reshuffleTime - curr_time_ms;
            env.ui.setCountdown(countdown, warn_time);
            if (countdown == 0) {
                timerMs.stop();
                table.setsOfThree.put(new TokensVector());
            }
        } else if (gameMode == GameMode.CountFromReshuffle) {
            env.ui.setElapsed(curr_time_ms);
        }
    }


    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {
        // TODO implement
        //lockAllSlots();
        is_busy=true;
        int[] occupied_slots = new int[table.countCards()];
        int j=0;
        for (int i=0;i<env.config.tableSize;i++){
            if (table.slotToCard[i]!=null){
                occupied_slots[j] = i;
                j++;
            }
        }
        for (int slot: shuffleTableSlots(occupied_slots)){
            lockSlot(slot);
            deck.add(table.removeCard(slot));
            for(int i=0;i<env.config.players;i++){
                players[i].removeSlot(slot);
            }
        }
    }

    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {
        // TODO implement
        ArrayList<Integer> winners = new ArrayList<>();
        int max_score=0;
        int player_id=-1;
        for (Player player:players){
            if (player.score()>=max_score){
                max_score=player.score();
                player_id=player.id;
            }
        }
        winners.add(player_id);
        for(Player player:players){
            if(player.score()==max_score &&winners.get(0)!=player_id){
                winners.add(player_id);
            }
        }
        int[] winner = new int[winners.size()];
        for (int i=0;i<winner.length;i++){
            winner[i]=winners.get(i);
        }
        env.ui.announceWinner(winner);
    }

    @Override
    public boolean isLocked(int pId) {
        return is_player_locked[pId];
    }

    @Override
    public boolean isSlotLocked(int slot) {
        return is_slot_locked[slot];
    }
    private void lockPlayer(int pId){
        is_player_locked[pId]=true;
    }
    private void lockSlot(int slot){
        is_slot_locked[slot]=true;
    }
    private void unlockPlayers(){
        Arrays.fill(is_player_locked,false);
    }
    
    
}

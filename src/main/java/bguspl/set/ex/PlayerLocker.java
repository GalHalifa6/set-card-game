package bguspl.set.ex;

public interface PlayerLocker {
    boolean isLocked(int pId);
    boolean isSlotLocked(int slot);
}


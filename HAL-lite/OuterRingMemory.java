public class OuterRingMemory {
  public Direction dir;
  public boolean isFighting;
  public Direction secondDir;

  public OuterRingMemory(Direction dir_, boolean isFighting_) {
    dir = dir_;
    isFighting = isFighting_;
    secondDir = Direction.STILL;
  }
  public OuterRingMemory(Direction dir_, boolean isFighting_, Direction secondDir_) {
    dir = dir_;
    isFighting = isFighting_;
    secondDir = secondDir_;
  }
}

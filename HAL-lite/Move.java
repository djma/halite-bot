public class Move {
  public Location loc;
  public Direction dir;

  public Move(Location loc_, Direction dir_) {
    loc = loc_;
    dir = dir_;
  }

  public Move(Move move_) {
    loc = move_.loc;
    dir = move_.dir;
  }
}

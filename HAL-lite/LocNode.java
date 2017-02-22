import java.util.Set;
import java.util.HashSet;

@SuppressWarnings("unchecked")
public class LocNode {
  public Location loc;
  public Set<Location> visited;
  public int targetStrg;
  public int pathProductionSum;
  public int depth;
  public Direction lastDirection;
  public int prodLoss;

  public LocNode(Location loc_, Set<Location> visited_, int targetStrg_, int pathProductionSum_, int depth_, Direction lastDirection_) {
    loc = new Location(loc_);
    visited = new HashSet(visited_);
    targetStrg = targetStrg_;
    pathProductionSum = pathProductionSum_;
    depth = depth_;
    lastDirection = lastDirection_;
    prodLoss = Integer.MAX_VALUE;
  }

  public LocNode(Location loc_, Set<Location> visited_, int targetStrg_, int pathProductionSum_, int depth_, Direction lastDirection_, int prodLoss_) {
    loc = new Location(loc_);
    visited = new HashSet(visited_);
    targetStrg = targetStrg_;
    pathProductionSum = pathProductionSum_;
    depth = depth_;
    lastDirection = lastDirection_;
    prodLoss = prodLoss_;
  }
}

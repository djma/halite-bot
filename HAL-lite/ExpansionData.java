import java.util.Set;
import java.util.HashSet;

@SuppressWarnings("unchecked")
public class ExpansionData {
  public Set<Location> visited;
  public int twoStepStrength;
  public int twoStepProd;
  public int prodLoss;
  public Move move;

  public ExpansionData(Set<Location> visited_, int twoStepStrength_, int twoStepProd_, int prodLoss_, Move move_) {
    visited = new HashSet(visited_);
    twoStepStrength = twoStepStrength_;
    twoStepProd = twoStepProd_;
    prodLoss = prodLoss_;
    move = new Move(move_);
  }

  public String toString() {
    return "Setsize : " + visited.size() + ", twoStepStrength : " + twoStepStrength + ", twoStepProd : " + twoStepProd + ", prodLoss : " + prodLoss + ", move: " + move.loc + move.dir;
  }
}

public class InnerAreaMemory {
  public Direction dir;
  public int distToOuterRing;
  public int productionLossToOuterRing;

  public InnerAreaMemory(Direction dir_, int distToOuterRing_) {
    dir = dir_;
    distToOuterRing = distToOuterRing_;
    productionLossToOuterRing = Integer.MAX_VALUE;
  }

  public InnerAreaMemory(Direction dir_, int distToOuterRing_, int productionLossToOuterRing_) {
    dir = dir_;
    distToOuterRing = distToOuterRing_;
    productionLossToOuterRing = productionLossToOuterRing_;
  }
}

public class TwoStepProdStr {
  public int production;
  public int strength;

  public TwoStepProdStr(int production_, int strength_) {
    production = production_;
    strength = strength_;
  }

  public double prodOverStr() {
    return production / (strength > 0 ? strength : 1);
  }
}

public class MaxGenerationsExceededException extends Exception {

  public MaxGenerationsExceededException(int maxGenerations) {
    super("Max generations of " + maxGenerations + " exceeded");
  }
}

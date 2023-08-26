import lombok.Data;

@Data
public class Config {

  private String dataFile;
  private int mutationProbability;
  private int crossoverProbability; // Not used
  private int populationSize;
  private int selectionSize;  // Not used
  private String selectionType;
  private String mutationType;
  private int maxGenerations;
  private String resultsFilename;

}

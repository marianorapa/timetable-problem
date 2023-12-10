import com.google.gson.Gson;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;

public class CliRunner {

  public static void main(String[] args) throws IOException {
    String configFile = args[0];
    int iterations = Integer.parseInt(args[1]);
    System.out.printf("Config file %s and iterations %s%n", configFile, iterations);

    Config config = readConfigFile(configFile);
    System.out.println("Config: " + config);

    GA ga = new GA();
    setupGA(ga, config);

    BufferedWriter writer = new BufferedWriter(new FileWriter("output/" + config.getResultsFilename()));

    List<Long> elapsedTimes = new ArrayList<>();

    for (int i = 0; i < iterations; i++) {
      try {
        long startTime = System.nanoTime();
        TimeTable timeTable = ga.generateTimeTable();
        long endTime = System.nanoTime();
        long elapsedTime = endTime - startTime;
        long elapsedTimeMillis = elapsedTime / 1_000_000;
        elapsedTimes.add(elapsedTimeMillis);
        System.out.println("Timetable from iteration " + i);
        ga.printTimeTable(timeTable);
        int finalGenerations = ga.getFinalGenerations();
        System.out.println("Generations: " + finalGenerations);
        System.out.println("Elapsed time: " + elapsedTimeMillis + " milliseconds");
        System.out.println("====================================================");
        writer.append(String.valueOf(i)).append(",").append(String.valueOf(finalGenerations)).append("\n");
        BufferedWriter generationsProgress = new BufferedWriter(new FileWriter("output/progress-" + i + "_" + config.getResultsFilename()));
        timeTable.getAncestorsFitness().forEach((generation, fitness) -> {
          try {
            generationsProgress.append(generation.toString()).append(',').append(fitness.toString()).append('\n');
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        });
        generationsProgress.close();
      }
      catch (MaxGenerationsExceededException e) {
        System.err.println("Max generations exceeded in iteration " + i);
        i = i - 1;
      }

    }
    writer.close();
    OptionalDouble average = elapsedTimes.stream().mapToDouble(Long::doubleValue).average();
    if (average.isPresent()) {
      System.out.printf("Average generations execution time: %s millis", average.getAsDouble());
    }
  }

  private static void setupGA(GA ga, Config config) {
    ga.loadData(config.getDataFile());
    ga.setMutationProbability(config.getMutationProbability());
    ga.setCrossoverProbability(config.getCrossoverProbability());
    ga.setPopulationSize(config.getPopulationSize());
    ga.setSelectionSize(config.getSelectionSize());
    ga.setMaxGenerations(config.getMaxGenerations());
    // Only one type for now
    ga.setSelectionType(config.getSelectionType());
    ga.setMutationType(config.getMutationType());
  }

  private static Config readConfigFile(String configFile) {
    try {
      String content = new String(Files.readAllBytes(Paths.get(configFile)));
      return new Gson().fromJson(content, Config.class);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }


}

import com.google.gson.Gson;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class CliRunner {

  public static void main(String[] args) throws IOException {
    String configFile = args[0];
    int iterations = Integer.parseInt(args[1]);
    System.out.printf("Config file %s and iterations %s%n", configFile, iterations);

    Config config = readConfigFile(configFile);
    System.out.println("Config: " + config);

    GA ga = new GA();
    List<Integer> ldbs = List.of(2, 6, 12);
    List<Integer> emls = List.of(4, 8, 10);
    List<Integer> mtscs = List.of(1, 5, 12);
    for (Integer ldb : ldbs) {
      for (Integer eml : emls) {
        for (Integer mtsc : mtscs) {
          setupGA(ga, config, ldb, eml, mtsc);
          String dir = "output/" + LocalDateTime.now() + "/" + ldb + "-" + eml + "-" + mtsc;
          if (new File(dir).mkdirs()) {
            System.out.println("Created dirs");
          };
          BufferedWriter writer = new BufferedWriter(new FileWriter(dir + "/" + config.getResultsFilename()));

          for (int i = 0; i < iterations; i++) {
            try {
              TimeTable timeTable = ga.generateTimeTable();
              System.out.println("Timetable from iteration " + i);
              ga.printTimeTable(timeTable);
              int finalGenerations = ga.getFinalGenerations();
              System.out.println("Generations: " + finalGenerations);
              System.out.println("====================================================");
              writer.append(String.valueOf(i)).append(",").append(String.valueOf(finalGenerations)).append("\n");
              BufferedWriter generationsProgress = new BufferedWriter(new FileWriter(dir + "/progress-" + i + "_" + config.getResultsFilename()));
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
              //i = i - 1; // Commented so each iteration counts even if exceeded
              writer.append(String.valueOf(i)).append(",").append(String.valueOf(-1)).append("\n");
            }

          }
          writer.close();
        }

      }
    }


  }

  private static void setupGA(GA ga, Config config, int ldb, int eml, int mtsc) {
    ga.loadData(config.getDataFile());
    ga.setMutationProbability(config.getMutationProbability());
    ga.setCrossoverProbability(config.getCrossoverProbability());
    ga.setPopulationSize(config.getPopulationSize());
    ga.setSelectionSize(config.getSelectionSize());
    ga.setMaxGenerations(config.getMaxGenerations());
    // Only one type for now
    ga.setSelectionType(config.getSelectionType());
    ga.setMutationType(config.getMutationType());

    ga.setLDB(ldb);
    ga.setEML(eml);
    ga.setMTSC(mtsc);
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
